/*
 * Copyright 2026 Austin Lehman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aussom.DefaultLoggingImpl;
import com.aussom.DefaultSecurityManagerImpl;
import com.aussom.Engine;
import com.aussom.stdlib.Lang;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomList;
import com.aussom.types.AussomNull;
import com.aussom.types.AussomObject;
import com.aussom.types.Mock;
import com.aussom.types.MockFunctionSpyRecord;

/**
 * Regression stress tests for the engine's eval-time thread-safety
 * fixes. Each test recreates a race that shipped at some point:
 * many threads hitting a lazy-initialization or shared-collection
 * path at the same instant, synchronized with a CyclicBarrier so
 * the collision window is as tight as possible.
 *
 * Covered fixes (see design/aussom-concurrency.md "What the engine
 * already guarantees"):
 *  - astClass.init()/initSync()/mergeInherited(): concurrent first
 *    instantiation of a class no longer sees half-initialized
 *    extern info or corrupts the inherited dispatch tables
 *    (formerly NPE "(null)", ConcurrentModificationException, or
 *    ArrayIndexOutOfBoundsException).
 *  - AussomObject.getMembers()/getMock(): concurrent first write on
 *    a shared object no longer loses members to a double-allocated
 *    Members instance.
 *  - Mock/MockFunction: spy records survive concurrent appends.
 *  - Lang.get(): one instance under concurrent first call.
 *
 * Also here: the cross-thread exactness stress tests for the
 * concurrent stdlib module (include concurrent;). The module's API
 * coverage lives with the rest of the interpreter tests in
 * tests/interpreter.aus; only the multi-thread tests are JUnit,
 * because aussom-base has no script-level threads to drive them
 * with.
 *
 * The round counts are sized to catch regressions reliably (the
 * pre-fix engine failed roughly 1 in 25 rounds at 8 threads) while
 * keeping the suite fast.
 */
@DisplayName("Eval-time concurrency regressions")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConcurrencyRegressionTest {

	private static final int THREADS = 8;
	private ExecutorService pool;

	@BeforeAll
	void startPool() {
		com.aussom.stdlib.console.get().register(new DefaultLoggingImpl());
		pool = Executors.newFixedThreadPool(THREADS);
	}

	@AfterAll
	void stopPool() {
		pool.shutdownNow();
	}

	@BeforeEach
	void setUp() {
		com.aussom.stdlib.console.get().register(new DefaultLoggingImpl());
	}

	/**
	 * Runs task on THREADS threads released together by a barrier
	 * and waits for all of them. Returns the number of tasks that
	 * threw.
	 */
	private int raceThreads(Runnable task) throws Exception {
		CyclicBarrier gate = new CyclicBarrier(THREADS);
		CountDownLatch done = new CountDownLatch(THREADS);
		AtomicInteger failures = new AtomicInteger();
		AtomicReference<Throwable> first = new AtomicReference<>();
		for (int t = 0; t < THREADS; t++) {
			pool.submit(() -> {
				try {
					gate.await();
					task.run();
				} catch (Throwable e) {
					failures.incrementAndGet();
					first.compareAndSet(null, e);
				} finally {
					done.countDown();
				}
			});
		}
		done.await();
		if (first.get() != null) {
			System.out.println("first failure: " + first.get());
		}
		return failures.get();
	}

	@Test
	@DisplayName("1. Concurrent first instantiation of an inheritance chain is clean")
	void classFirstInstantiationRace() throws Exception {
		final String src =
			"class base {\n" +
			"    public baseVal = 1;\n" +
			"    public bfunc() { return 10; }\n" +
			"    public shared() { return \"base\"; }\n" +
			"}\n" +
			"class mid : base {\n" +
			"    public midVal = 2;\n" +
			"    public mfunc() { return 20; }\n" +
			"    public shared() { return \"mid\"; }\n" +
			"}\n" +
			"class leaf : mid {\n" +
			"    public leafVal = 3;\n" +
			"    public lfunc() { return 30; }\n" +
			"}\n";

		int rounds = 200;
		int failures = 0;
		for (int r = 0; r < rounds; r++) {
			// Fresh engine each round so every round is a true
			// first use of the class chain.
			Engine eng = new Engine(new DefaultSecurityManagerImpl());
			eng.parseString("stress.aus", src);
			failures += raceThreads(() -> {
				try {
					eng.instantiateObject("leaf");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		}
		assertEquals(0, failures,
			"concurrent first instantiation failed; astClass init/merge race is back");
	}

	@Test
	@DisplayName("2. Concurrent first member writes on a shared object all land")
	void membersLazyInitRace() throws Exception {
		int rounds = 500;
		int lost = 0;
		for (int r = 0; r < rounds; r++) {
			AussomObject obj = new AussomObject(false); // members starts null
			AtomicInteger id = new AtomicInteger();
			raceThreads(() -> {
				int i = id.getAndIncrement();
				obj.addMember("m" + i, new AussomInt(i));
			});
			for (int t = 0; t < THREADS; t++) {
				if (!obj.containsMember("m" + t)) lost++;
			}
		}
		assertEquals(0, lost,
			"member writes vanished; AussomObject.getMembers() lazy-init race is back");
	}

	@Test
	@DisplayName("3. Concurrent first mock setup on a shared object all lands")
	void mockLazyInitRace() throws Exception {
		int rounds = 500;
		int lost = 0;
		for (int r = 0; r < rounds; r++) {
			AussomObject obj = new AussomObject(false); // mock starts null
			AtomicInteger id = new AtomicInteger();
			raceThreads(() -> {
				int i = id.getAndIncrement();
				obj.getMock().setSpy("f" + i);
			});
			for (int t = 0; t < THREADS; t++) {
				if (!obj.isSpySet("f" + t)) lost++;
			}
		}
		assertEquals(0, lost,
			"mock setups vanished; AussomObject.getMock() lazy-init race is back");
	}

	@Test
	@DisplayName("4. Concurrent spy recording keeps every record")
	void spyRecordRace() throws Exception {
		int rounds = 200;
		int perThread = 25;
		for (int r = 0; r < rounds; r++) {
			Mock mock = new Mock();
			mock.setSpy("target");
			int failures = raceThreads(() -> {
				for (int i = 0; i < perThread; i++) {
					mock.addSpyRecord("target",
						new MockFunctionSpyRecord(new AussomList(), new AussomNull()));
				}
			});
			assertEquals(0, failures, "spy recording threw under concurrency");
			assertEquals(THREADS * perThread,
				mock.getSpyResults("target").size(),
				"spy records lost; Mock list race is back");
		}
	}

	@Test
	@DisplayName("5. Lang.get() returns one instance under concurrent calls")
	void langSingletonRace() throws Exception {
		// Lang.instance is likely set by earlier engine use in this
		// JVM; this still verifies every concurrent caller observes
		// the same instance through the synchronized getter.
		AtomicReference<Lang> seen = new AtomicReference<>();
		int failures = raceThreads(() -> {
			Lang l = Lang.get();
			Lang prev = seen.getAndSet(l);
			if (prev != null && prev != l) {
				throw new IllegalStateException("two Lang instances observed");
			}
		});
		assertEquals(0, failures, "Lang.get() returned different instances");
		assertSame(seen.get(), Lang.get());
	}

	/* ============================================================ */
	/*  concurrent stdlib module: cross-thread exactness            */
	/* ============================================================ */

	/**
	 * Invokes method on obj from THREADS threads, iterations times
	 * each, all released together. Returns the number of invocations
	 * that threw.
	 */
	private int hammer(ScriptEngine engine, Object obj, String method, int iterations)
			throws Exception {
		Invocable inv = (Invocable) engine;
		CyclicBarrier gate = new CyclicBarrier(THREADS);
		CountDownLatch done = new CountDownLatch(THREADS);
		AtomicInteger failures = new AtomicInteger();
		for (int t = 0; t < THREADS; t++) {
			pool.submit(() -> {
				try {
					gate.await();
					for (int i = 0; i < iterations; i++) {
						inv.invokeMethod(obj, method);
					}
				} catch (Throwable e) {
					failures.incrementAndGet();
					System.out.println("hammer failure: " + e);
				} finally {
					done.countDown();
				}
			});
		}
		done.await();
		return failures.get();
	}

	private ScriptEngine newScriptEngine() {
		return new ScriptEngineManager().getEngineByName("aussom");
	}

	@Test
	@DisplayName("6. AtomicLong holds an exact count under 8-thread contention")
	void atomicLongStress() throws Exception {
		ScriptEngine engine = newScriptEngine();
		engine.eval(
			"include concurrent;\n" +
			"class S {\n" +
			"  private n = null;\n" +
			"  public S() { this.n = new AtomicLong(0); }\n" +
			"  public inc() { return this.n.incrementAndGet(); }\n" +
			"  public total() { return this.n.get(); }\n" +
			"}");
		Object s = engine.eval("return new S();");
		int iterations = 500;
		assertEquals(0, hammer(engine, s, "inc", iterations), "invocations threw");
		assertEquals((long) THREADS * iterations,
			((Invocable) engine).invokeMethod(s, "total"),
			"increments lost; AtomicLong is not holding an exact count");
	}

	@Test
	@DisplayName("7. Counter holds an exact count under 8-thread contention")
	void counterStress() throws Exception {
		ScriptEngine engine = newScriptEngine();
		engine.eval(
			"include concurrent;\n" +
			"class S {\n" +
			"  private n = null;\n" +
			"  public S() { this.n = new Counter(); }\n" +
			"  public inc() { this.n.increment(); return true; }\n" +
			"  public total() { return this.n.sum(); }\n" +
			"}");
		Object s = engine.eval("return new S();");
		int iterations = 500;
		assertEquals(0, hammer(engine, s, "inc", iterations), "invocations threw");
		assertEquals((long) THREADS * iterations,
			((Invocable) engine).invokeMethod(s, "total"),
			"increments lost; Counter is not holding an exact count");
	}

	@Test
	@DisplayName("8. Lock.withLock makes a plain += member hold an exact count")
	void lockStress() throws Exception {
		// Without the lock this is the canonical lost-update race;
		// withLock must serialize the read-compute-write.
		ScriptEngine engine = newScriptEngine();
		engine.eval(
			"include concurrent;\n" +
			"class S {\n" +
			"  private lk = null;\n" +
			"  public n = 0;\n" +
			"  public S() { this.lk = new Lock(); }\n" +
			"  public inc() { return this.lk.withLock(::incBody); }\n" +
			"  public incBody() { this.n += 1; return this.n; }\n" +
			"  public total() { return this.n; }\n" +
			"}");
		Object s = engine.eval("return new S();");
		int iterations = 250;
		assertEquals(0, hammer(engine, s, "inc", iterations), "invocations threw");
		assertEquals((long) THREADS * iterations,
			((Invocable) engine).invokeMethod(s, "total"),
			"increments lost; Lock.withLock is not serializing the critical section");
	}

	@Test
	@DisplayName("9. BlockingQueue hands every value across threads exactly once")
	void queueStress() throws Exception {
		ScriptEngine engine = newScriptEngine();
		engine.eval(
			"include concurrent;\n" +
			"class S {\n" +
			"  private q = null;\n" +
			"  private got = null;\n" +
			"  public S() { this.q = new BlockingQueue(); this.got = new Counter(); }\n" +
			"  public produce() { this.q.offer(1); return true; }\n" +
			"  public drain() {\n" +
			"    while (true) {\n" +
			"      v = this.q.poll();\n" +
			"      if (v == null) { break; }\n" +
			"      this.got.increment();\n" +
			"    }\n" +
			"    return this.got.sum();\n" +
			"  }\n" +
			"}");
		Object s = engine.eval("return new S();");
		int iterations = 500;
		assertEquals(0, hammer(engine, s, "produce", iterations), "invocations threw");
		assertEquals((long) THREADS * iterations,
			((Invocable) engine).invokeMethod(s, "drain"),
			"values lost in the queue handoff");
	}
}

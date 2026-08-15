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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aussom.DefaultLoggingImpl;
import com.aussom.Engine;
import com.aussom.TestSecurityManagerImpl;
import com.aussom.types.AussomException;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomType;

/**
 * JUnit 5 coverage for interpreter cancellation. See the
 * "Cancellation" section of com.aussom.Engine.
 *
 * Most cases pre-set the cancellation flag and then run a loop, which
 * makes the assertion deterministic: the check sits at the top of the
 * loop body, so the very first iteration stops. The concurrent cases
 * in "cross-thread" cover the part that pre-setting cannot, namely
 * that a flag raised by another thread reaches a loop already running.
 */
@DisplayName("Interpreter cancellation")
public class CancellationTest {

	@BeforeEach
	void setUp() {
		// Quiet the engine's [trc] chatter during tests.
	}

	/**
	 * Builds an engine in script mode. Script mode is the test seam
	 * here because evalLine hands runtime errors back as
	 * AussomException values instead of printing them, so the
	 * cancellation exception can be inspected directly.
	 */
	private static Engine scriptEngine() throws Exception {
		Engine eng = new Engine(new TestSecurityManagerImpl());
		eng.setScriptMode(true);
		return eng;
	}

	/**
	 * Asserts the value is the interpreter's cancellation exception.
	 */
	private static AussomException assertCancelled(AussomType val) {
		assertNotNull(val, "Expected a value back from the loop.");
		assertTrue(val.isEx(), "Expected an exception, got: " + val.getType().name());
		AussomException ex = (AussomException) val;
		assertEquals(Engine.CANCELLED_EXCEPTION_ID, ex.getId());
		assertTrue(ex.isCancellation(), "Expected the cancellation flag to be set.");
		return ex;
	}

	/* ============================================================ */
	/*  Loop back edges                                             */
	/* ============================================================ */

	@Nested
	@DisplayName("stops at every loop back edge")
	class LoopSites {

		@Test
		@DisplayName("while loop")
		void whileLoop() throws Exception {
			Engine eng = scriptEngine();
			eng.cancel();
			assertCancelled(eng.evalLine("while (true) { }"));
		}

		@Test
		@DisplayName("classic for loop")
		void classicFor() throws Exception {
			Engine eng = scriptEngine();
			eng.cancel();
			assertCancelled(eng.evalLine("for (i = 0; true; i++) { }"));
		}

		@Test
		@DisplayName("foreach over a list")
		void foreachList() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("hits = 0;");
			eng.cancel();
			assertCancelled(eng.evalLine("for (item : [1, 2, 3]) { hits = hits + 1; }"));

			// The body never ran, so the loop stopped on the back edge
			// rather than after finishing the list.
			eng.clearCancel();
			AussomType hits = eng.evalLine("hits;");
			assertEquals(0, ((AussomInt) hits).getValue());
		}

		@Test
		@DisplayName("foreach over a map")
		void foreachMap() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("hits = 0;");
			eng.cancel();
			assertCancelled(eng.evalLine("for (key : { 'a': 1, 'b': 2 }) { hits = hits + 1; }"));

			eng.clearCancel();
			AussomType hits = eng.evalLine("hits;");
			assertEquals(0, ((AussomInt) hits).getValue());
		}
	}

	/* ============================================================ */
	/*  Shape of the reported exception                             */
	/* ============================================================ */

	@Nested
	@DisplayName("reports a distinct, non-catchable exception")
	class ExceptionShape {

		@Test
		@DisplayName("carries the cancelled id, not a generic runtime id")
		void distinctId() throws Exception {
			Engine eng = scriptEngine();
			eng.cancel();
			AussomException ex = assertCancelled(eng.evalLine("while (true) { }"));
			assertEquals("EXECUTION_CANCELLED", ex.getId());
			assertEquals(AussomException.exType.exRuntime, ex.getEt());
			assertTrue(ex.getDetails().contains("Engine.cancel()"),
				"Details should name the cancellation source, got: " + ex.getDetails());
		}

		@Test
		@DisplayName("reports the line number of the cancelled loop")
		void reportsLineNumber() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("a = 1;", 1);
			eng.cancel();
			AussomException ex = assertCancelled(eng.evalLine("while (true) { }", 7));
			assertEquals(7, ex.getLineNumber());
		}

		@Test
		@DisplayName("a catch block cannot swallow it")
		void notCatchable() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("caught = false;");
			eng.cancel();

			AussomType res = eng.evalLine(
				"try { while (true) { } } catch (e) { caught = true; }");
			assertCancelled(res);

			eng.clearCancel();
			AussomType caught = eng.evalLine("caught;");
			assertFalse(caught.getValueString().equals("true"),
				"The catch block should not have run.");
		}

		@Test
		@DisplayName("an ordinary runtime error is still catchable")
		void ordinaryErrorsUnaffected() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("caught = false;");

			AussomType res = eng.evalLine(
				"try { throw \"boom\"; } catch (e) { caught = true; }");
			assertFalse(res.isEx(), "An ordinary throw should be caught, not propagated.");

			AussomType caught = eng.evalLine("caught;");
			assertTrue(caught.getValueString().equals("true"),
				"The catch block should have run.");
		}
	}

	/* ============================================================ */
	/*  Flag lifecycle                                              */
	/* ============================================================ */

	@Nested
	@DisplayName("cancellation flag lifecycle")
	class FlagLifecycle {

		@Test
		@DisplayName("defaults to not cancelled and leaves loops alone")
		void notCancelledByDefault() throws Exception {
			Engine eng = scriptEngine();
			assertFalse(eng.isCancelled());

			eng.evalLine("total = 0;");
			eng.evalLine("for (i = 0; i < 100; i++) { total = total + 1; }");
			AussomType total = eng.evalLine("total;");
			assertEquals(100, ((AussomInt) total).getValue());
			assertFalse(eng.isCancelled());
		}

		@Test
		@DisplayName("is sticky until cleared, then the engine runs again")
		void stickyThenCleared() throws Exception {
			Engine eng = scriptEngine();

			eng.cancel();
			assertTrue(eng.isCancelled());
			assertCancelled(eng.evalLine("while (true) { }"));

			// Still set, so a second loop stops too.
			assertTrue(eng.isCancelled());
			assertCancelled(eng.evalLine("while (true) { }"));

			assertTrue(eng.clearCancel(), "clearCancel should report the prior value.");
			assertFalse(eng.isCancelled());
			assertFalse(eng.clearCancel(), "Clearing an already-clear flag reports false.");

			eng.evalLine("total = 0;");
			eng.evalLine("for (i = 0; i < 10; i++) { total = total + 1; }");
			assertEquals(10, ((AussomInt) eng.evalLine("total;")).getValue());
		}
	}

	/* ============================================================ */
	/*  Cross-thread                                                */
	/* ============================================================ */

	@Nested
	@DisplayName("reaches past loops")
	class BeyondLoops {

		@Test
		@DisplayName("1. A program with no loop in it is still cancellable, "
			+ "because every call is a checkpoint")
		void programWithNoLoopStops() throws Exception {
			// Cancellation used to be checked only at loop back edges, so a
			// program built out of calls and recursion could not be stopped
			// at all: it ran until the Java stack gave out. The flag is
			// pre-set here for the same reason the loop cases do it, namely
			// that it makes the assertion deterministic rather than a race
			// against how fast recursion eats a stack.
			Engine eng = scriptEngine();
			eng.evalLine("class rec { public rec() { } "
				+ "public go(int n) { if (n <= 0) { return 0; } return this.go(n - 1); } }");
			eng.cancel();
			assertCancelled(eng.evalLine("x = new rec().go(50);"));
		}

		@Test
		@DisplayName("1b. Paired: the same program runs normally when not cancelled")
		void sameProgramRunsWhenNotCancelled() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("class rec { public rec() { } "
				+ "public go(int n) { if (n <= 0) { return 0; } return this.go(n - 1); } }");
			AussomType val = eng.evalLine("x = new rec().go(50);");
			assertFalse(val.isEx(), "The recursion should run when nothing cancelled it.");
		}

		@Test
		@DisplayName("2. A sleeping program stops on cancel, within a slice")
		void sleepingProgramStops() throws Exception {
			final Engine eng = scriptEngine();
			eng.evalLine("include sys;");

			final AtomicReference<AussomType> result = new AtomicReference<AussomType>();
			Thread worker = new Thread(() -> {
				try {
					result.set(eng.evalLine("sys.sleep(60000);"));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}, "sleeper");
			worker.start();

			Thread.sleep(100);
			long t0 = System.currentTimeMillis();
			eng.cancel();
			worker.join(10000);
			long ms = System.currentTimeMillis() - t0;
			assertFalse(worker.isAlive(), "cancel() did not reclaim a sleeping program.");
			assertTrue(ms < 5000L, "Should have stopped within a slice, took " + ms + " ms.");
			assertCancelled(result.get());
		}
	}

	@Nested
	@DisplayName("cross-thread")
	class CrossThread {

		/**
		 * Runs source on a worker thread and hands the caller a way to
		 * wait for the loop to actually be spinning before it acts.
		 */
		private final AtomicReference<AussomType> result = new AtomicReference<AussomType>();
		private final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

		private Thread startLoop(Engine eng, String source, CountDownLatch started) {
			Thread t = new Thread(new Runnable() {
				public void run() {
					try {
						started.countDown();
						result.set(eng.evalLine(source));
					} catch (Throwable th) {
						failure.set(th);
					}
				}
			}, "aussom-cancel-test");
			t.setDaemon(true);
			t.start();
			return t;
		}

		@Test
		@DisplayName("cancel() stops a loop that is already running")
		void cancelWhileRunning() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("spins = 0;");

			CountDownLatch started = new CountDownLatch(1);
			Thread worker = startLoop(eng, "while (true) { spins = spins + 1; }", started);

			assertTrue(started.await(5, TimeUnit.SECONDS), "Worker never started.");
			// Give the loop a moment to get going so this exercises the
			// cross-thread visibility of the flag, not the pre-set case.
			Thread.sleep(100);
			eng.cancel();

			worker.join(10000);
			assertFalse(worker.isAlive(), "The loop did not stop after cancel().");
			assertNull(failure.get());
			AussomException ex = assertCancelled(result.get());
			assertTrue(ex.getDetails().contains("Engine.cancel()"));

			// The loop really did run before it was stopped.
			eng.clearCancel();
			assertTrue(((AussomInt) eng.evalLine("spins;")).getValue() > 0,
				"Expected the loop body to have run before cancellation.");
		}

		@Test
		@DisplayName("interrupting the interpreter thread does NOT stop a loop")
		void interruptIsIgnored() throws Exception {
			// cancel() is the single mechanism. Interrupt status is
			// per-thread state that any code on the stack can set, and
			// one engine may be shared by many threads, so an interrupt
			// is not a reason to stop the whole program. This test pins
			// that decision down. See the "Cancellation" section of
			// Engine.
			Engine eng = scriptEngine();

			CountDownLatch started = new CountDownLatch(1);
			Thread worker = startLoop(eng, "while (true) { }", started);

			assertTrue(started.await(5, TimeUnit.SECONDS), "Worker never started.");
			Thread.sleep(100);
			worker.interrupt();
			Thread.sleep(200);

			assertTrue(worker.isAlive(), "Interrupt should not have stopped the loop.");
			assertFalse(eng.isCancelled(), "Interrupt should not set the engine flag.");

			// cancel() still stops it, interrupted or not.
			eng.cancel();
			worker.join(10000);
			assertFalse(worker.isAlive(), "The loop did not stop after cancel().");
			assertNull(failure.get());
			assertCancelled(result.get());
		}

		private void assertNull(Throwable t) {
			if (t != null) {
				throw new AssertionError("Worker thread failed.", t);
			}
		}
	}
}

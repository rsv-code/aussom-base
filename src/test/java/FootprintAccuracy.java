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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.aussom.Engine;
import com.aussom.TestSecurityManagerImpl;

/**
 * JUnit 5 coverage for what a footprint measurement counts and how
 * closely it tracks the JVM.
 *
 * Three things are being pinned here. That a measurement is only
 * available when the engine is not running, since a walk of a moving
 * graph is a number nobody can use. That the walk reaches what a running
 * program actually holds, which is the locals of the frames its threads
 * are parked in, not just statics and members. And that the estimate
 * stays in the same neighbourhood as the JVM's own heap accounting,
 * because an estimate whose relationship to reality has quietly changed
 * is worse than no estimate.
 *
 * The accuracy tests assert a loose band rather than the exact ratios
 * measured when they were written. The point is to catch a change that
 * breaks the relationship, not to freeze numbers that depend on the JVM,
 * its collector and its pointer width.
 *
 * See design/security-evaluation-g1-g3.md.
 */
@DisplayName("Footprint accuracy")
public class FootprintAccuracy {

	/** A program that holds its data on the main class instance. */
	private static final String HOLDER =
		"class app {\n"
		+ "    public held = %s;\n"
		+ "    public app() { }\n"
		+ "    public main(args) {\n"
		+ "        i = 0;\n"
		+ "        while (i < %d) { %s i = i + 1; }\n"
		+ "        return 0;\n"
		+ "    }\n"
		+ "}\n";

	/**
	 * Used heap after repeated collection. Not exact, which is why every
	 * assertion built on it is a band rather than a value.
	 */
	private static long usedHeap() throws Exception {
		for (int i = 0; i < 4; i++) {
			System.gc();
			Thread.sleep(50);
		}
		Runtime rt = Runtime.getRuntime();
		return rt.totalMemory() - rt.freeMemory();
	}

	private static Engine engine() throws Exception {
		return new Engine(new TestSecurityManagerImpl());
	}

	/**
	 * Runs a holder program and reports the estimate against what the JVM
	 * held for it.
	 * @return A double with estimate divided by JVM delta.
	 */
	private static double ratioFor(String init, int count, String body) throws Exception {
		String src = String.format(HOLDER, init, count, body);
		long before = usedHeap();
		Engine eng = engine();
		eng.parseString("accuracy.aus", src);
		eng.run();
		long after = usedHeap();

		long jvm = after - before;
		long est = eng.measureRetainedFootprint();
		assertTrue(jvm > 0L, "The workload should have grown the heap; JVM delta was " + jvm);
		return (double) est / (double) jvm;
	}

	private static void assertBand(double ratio, String what) {
		assertTrue(ratio > 0.4 && ratio < 1.8,
			what + ": estimate/JVM was " + String.format("%.2f", ratio)
				+ ", outside the 0.4 to 1.8 band. The model and the JVM have "
				+ "drifted apart; see design/security-evaluation-g1-g3.md.");
	}

	/**
	 * Starts a program on its own thread. The program builds a large
	 * value in a local inside a method, then sleeps in a loop so a test
	 * can pause it while that local is live.
	 */
	private static Thread runAsync(Engine eng, AtomicReference<Throwable> failure) {
		Thread t = new Thread(() -> {
			try {
				eng.run();
			} catch (Throwable thrown) {
				failure.set(thrown);
			}
		}, "footprint-worker");
		t.start();
		return t;
	}

	private static final String LOCAL_HOLDER =
		"include sys;\n"
		+ "class app {\n"
		+ "    public app() { }\n"
		+ "    public work() {\n"
		+ "        data = [];\n"
		+ "        i = 0;\n"
		+ "        while (i < 100000) { data.add(\"value number \" + i); i = i + 1; }\n"
		+ "        n = 0;\n"
		+ "        while (n < 200) { sys.sleep(50); n = n + 1; }\n"
		+ "        return 0;\n"
		+ "    }\n"
		+ "    public main(args) { this.work(); return 0; }\n"
		+ "}\n";

	/* ============================================================ */

	@Nested
	@DisplayName("availability")
	class Availability {

		@Test
		@DisplayName("1. An engine that has finished a program can be measured")
		void notRunningIsMeasurable() throws Exception {
			Engine eng = engine();
			eng.parseString("done.aus", String.format(HOLDER, "[]", 1000, "this.held.add(i);"));
			eng.run();
			assertTrue(eng.measureRetainedFootprint() > 0L,
				"An engine with no threads running is measurable.");
		}

		@Test
		@DisplayName("2. Paired: a running engine refuses to be measured, "
			+ "and the same engine paused does not")
		void runningRefusesPausedAllows() throws Exception {
			Engine eng = engine();
			eng.parseString("spin.aus", LOCAL_HOLDER);
			AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
			Thread worker = runAsync(eng, failure);
			try {
				Thread.sleep(400);
				assertEquals(-1L, eng.measureRetainedFootprint(),
					"A running engine has no coherent graph to walk and must refuse.");

				eng.pause();
				assertTrue(eng.awaitPaused(10, TimeUnit.SECONDS),
					"The program should reach a checkpoint and park.");
				assertTrue(eng.measureRetainedFootprint() > 0L,
					"A fully paused engine is measurable.");
			} finally {
				eng.cancel();
				eng.resume();
				worker.join(10000);
			}
		}
	}

	@Nested
	@DisplayName("roots")
	class Roots {

		@Test
		@DisplayName("3. A value held only in a method's local is counted while "
			+ "the program is paused")
		void frameLocalsAreCounted() throws Exception {
			Engine eng = engine();
			eng.parseString("locals.aus", LOCAL_HOLDER);
			AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
			Thread worker = runAsync(eng, failure);
			long paused = 0L;
			try {
				// Long enough to be in the sleep loop with the list built,
				// and harmless if it is still building: the local exists
				// either way.
				Thread.sleep(3000);
				eng.pause();
				assertTrue(eng.awaitPaused(10, TimeUnit.SECONDS), "Should park.");
				paused = eng.measureRetainedFootprint();
			} finally {
				eng.cancel();
				eng.resume();
				worker.join(10000);
			}

			// The list is a local of work(), reachable from no static and
			// no member. Before the frame roots existed this measured as
			// near nothing.
			assertTrue(paused > 1000000L,
				"A paused program holding a large list in a local should measure it; got "
					+ paused + " bytes. The parked frame roots are not reaching it.");
		}

		@Test
		@DisplayName("4. Once the frame has gone, its locals are not counted")
		void framesAreNotCountedAfterTheyReturn() throws Exception {
			Engine eng = engine();
			// Builds the list in a method local, then returns without
			// keeping it.
			eng.parseString("gone.aus",
				"class app {\n"
				+ "    public app() { }\n"
				+ "    public work() {\n"
				+ "        data = [];\n"
				+ "        i = 0;\n"
				+ "        while (i < 100000) { data.add(\"value number \" + i); i = i + 1; }\n"
				+ "        return 0;\n"
				+ "    }\n"
				+ "    public main(args) { this.work(); return 0; }\n"
				+ "}\n");
			eng.run();
			long after = eng.measureRetainedFootprint();
			assertTrue(after < 1000000L,
				"A returned frame's locals must not be charged; got " + after + " bytes.");
		}
	}

	@Nested
	@DisplayName("class definitions")
	class ClassDefinitions {

		@Test
		@DisplayName("5. Parsing definitions charges for them, and the footprint "
			+ "includes the charge")
		void definitionsAreCharged() throws Exception {
			Engine eng = engine();
			long before = eng.getClassDefinitionBytes();

			StringBuilder src = new StringBuilder();
			for (int c = 0; c < 100; c++) {
				src.append("class gen").append(c).append(" {\n");
				src.append("    public m = 0;\n");
				src.append("    public gen").append(c).append("() { }\n");
				for (int m = 0; m < 10; m++) {
					src.append("    public meth").append(m)
						.append("(a, b) { x = a + b; y = x - a; return y + this.m; }\n");
				}
				src.append("}\n");
			}
			src.append("class app { public app() { } public main(args) { return 0; } }\n");
			eng.parseString("defs.aus", src.toString());

			long after = eng.getClassDefinitionBytes();
			assertTrue(after > before,
				"Parsing 100 classes should charge for their definitions; "
					+ before + " -> " + after);
			eng.run();
			assertTrue(eng.measureRetainedFootprint() >= after,
				"A footprint should include the class definition charge.");
		}
	}

	@Nested
	@DisplayName("extern objects")
	class Externs {

		@Test
		@DisplayName("6. A buffer's bytes are counted")
		void bufferIsCounted() throws Exception {
			Engine eng = engine();
			eng.parseString("buf.aus",
				"class app {\n"
				+ "    public held = [];\n"
				+ "    public app() { }\n"
				+ "    public main(args) {\n"
				+ "        i = 0;\n"
				+ "        while (i < 8) { this.held.add(new Buffer(1048576)); i = i + 1; }\n"
				+ "        return 0;\n"
				+ "    }\n"
				+ "}\n");
			eng.run();
			assertTrue(eng.measureRetainedFootprint() >= 8L * 1048576L,
				"Eight one megabyte buffers should be counted through AussomFootprintInt.");
		}

		@Test
		@DisplayName("7. One buffer referenced twice is counted once")
		void sharedBufferCountedOnce() throws Exception {
			Engine eng = engine();
			eng.parseString("shared.aus",
				"class app {\n"
				+ "    public one = null;\n"
				+ "    public two = null;\n"
				+ "    public app() { }\n"
				+ "    public main(args) {\n"
				+ "        this.one = new Buffer(4194304);\n"
				+ "        this.two = this.one;\n"
				+ "        return 0;\n"
				+ "    }\n"
				+ "}\n");
			eng.run();
			long est = eng.measureRetainedFootprint();
			assertTrue(est < 2L * 4194304L,
				"A buffer held twice must be charged once; got " + est + " bytes for a 4 MB buffer.");
		}
	}

	@Nested
	@DisplayName("how closely it tracks the JVM")
	class Accuracy {

		@Test
		@DisplayName("8. Many strings")
		void strings() throws Exception {
			assertBand(ratioFor("[]", 200000, "this.held.add(\"value number \" + i);"), "strings");
		}

		@Test
		@DisplayName("9. Many ints")
		void ints() throws Exception {
			assertBand(ratioFor("[]", 200000, "this.held.add(i);"), "ints");
		}

		@Test
		@DisplayName("10. A large map")
		void maps() throws Exception {
			assertBand(ratioFor("{}", 100000,
				"this.held[\"key\" + i] = \"value number \" + i;"), "map");
		}

		@Test
		@DisplayName("11. Buffers, where the model is exact and the JVM is the "
			+ "one doing the rounding")
		void buffers() throws Exception {
			// 512 KB rather than 1 MB deliberately. G1's default region at
			// a small heap is 1 or 2 MB, and an array over half a region
			// is humongous and takes a whole one, which makes the JVM hold
			// twice what was asked for. Measured: with 1 MB buffers the
			// ratio is 0.50, and with 512 KB buffers or an 8 MB region it
			// is 1.00. The model is right in every one of those cases.
			double ratio = ratioFor("[]", 40, "this.held.add(new Buffer(524288));");
			assertTrue(ratio > 0.8 && ratio < 1.2,
				"Buffer bytes are known exactly, so the estimate should be close to the "
					+ "JVM delta; ratio was " + String.format("%.2f", ratio));
		}
	}
}

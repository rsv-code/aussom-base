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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aussom.ControlState;
import com.aussom.Engine;
import com.aussom.Limits;
import com.aussom.TestSecurityManagerImpl;
import com.aussom.ThreadMeter;
import com.aussom.types.AussomException;
import com.aussom.types.AussomType;
import com.aussom.types.AussomTypeInt;

/**
 * JUnit 5 coverage for the controls and readings a host needs in order to
 * govern a tenant: pause, resume, cancel, CPU and allocation accounting,
 * and the retained-footprint measurement.
 *
 * The engine supplies mechanism only. Nothing here samples anything on a
 * timer or decides to stop a program: every decision in these tests is
 * made by the test, standing in for the host.
 *
 * See design/security-evaluation-f4-f5.md section 5.
 */
@DisplayName("Engine control and accounting")
public class EngineControl {

	private static final long BOUND_MS = 15000L;

	private static Engine scriptEngine() throws Exception {
		Engine eng = new Engine(new TestSecurityManagerImpl());
		eng.setScriptMode(true);
		return eng;
	}

	/**
	 * Renders a value for a failure message, short enough to be readable.
	 * Values here can be hundred-thousand element lists, and a full
	 * rendering of one costs more than the test it is describing.
	 */
	private static String str(AussomType val) {
		if (val == null) return "null";
		String s = ((AussomTypeInt) val).str();
		if (s.length() > 200) return s.substring(0, 200) + "... (" + s.length() + " chars)";
		return s;
	}

	/** Type name only, for messages about values that may be huge. */
	private static String kind(Object o) {
		if (o == null) return "null";
		return o.getClass().getSimpleName();
	}

	/**
	 * Starts a program on its own thread and hands back the reference the
	 * result will land in.
	 */
	private static Thread runAsync(Engine eng, String source, AtomicReference<Object> out,
			String name) {
		Thread t = new Thread(() -> {
			try {
				out.set(eng.evalLine(source));
			} catch (Throwable thrown) {
				out.set(thrown);
			}
		}, name);
		t.start();
		return t;
	}

	/* ============================================================ */

	@Nested
	@DisplayName("pause and resume")
	class PauseResume {

		@Test
		@DisplayName("1. A running loop stops advancing on pause and finishes after resume")
		void pauseStopsProgressResumeContinues() throws Exception {
			Engine eng = scriptEngine();
			// A loop long enough to still be running when we pause it,
			// writing its progress where the test can read it.
			eng.evalLine("total = 0;");
			AtomicReference<Object> out = new AtomicReference<Object>();
			Thread worker = runAsync(eng,
				"for (i = 0; i < 4000000; i++) { total = total + 1; }", out, "loop");

			eng.pause();
			assertTrue(eng.awaitPaused(BOUND_MS, TimeUnit.MILLISECONDS),
				"The engine never reported itself fully paused.");
			assertEquals(ControlState.PAUSED, eng.getControlState());
			assertTrue(eng.isFullyPaused(), "isFullyPaused should agree with awaitPaused.");

			// Nothing should move while it is paused.
			long first = eng.getCpuNanos();
			Thread.sleep(120);
			assertTrue(worker.isAlive(), "The program should still be mid-run.");
			if (ThreadMeter.isCpuAvailable()) {
				long second = eng.getCpuNanos();
				assertTrue(second - first < 50000000L,
					"A paused program should burn no CPU; used " + (second - first) + " ns.");
			}

			eng.resume();
			worker.join(BOUND_MS);
			assertFalse(worker.isAlive(), "The program did not finish after resume().");
			Object result = out.get();
			assertFalse(result instanceof Throwable, "Unexpected throwable: " + kind(result));
			assertEquals("4000000", str(eng.evalLine("total;")),
				"Pausing must not change the result.");
		}

		@Test
		@DisplayName("2. Paired: the same program with no pause produces the same answer")
		void unpausedRunMatches() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("total = 0;");
			eng.evalLine("for (i = 0; i < 4000000; i++) { total = total + 1; }");
			assertEquals("4000000", str(eng.evalLine("total;")));
		}

		@Test
		@DisplayName("3. cancel() ends a paused program without a resume first")
		void cancelOutranksPause() throws Exception {
			Engine eng = scriptEngine();
			AtomicReference<Object> out = new AtomicReference<Object>();
			Thread worker = runAsync(eng,
				"n = 0; while (true) { n = n + 1; }", out, "spin");

			eng.pause();
			assertTrue(eng.awaitPaused(BOUND_MS, TimeUnit.MILLISECONDS),
				"The engine never reported itself fully paused.");

			eng.cancel();
			worker.join(BOUND_MS);
			assertFalse(worker.isAlive(), "cancel() should end a paused program.");
			assertEquals(ControlState.CANCELLED, eng.getControlState());

			Object result = out.get();
			assertFalse(result instanceof Throwable, "Unexpected throwable: " + kind(result));
			AussomType val = (AussomType) result;
			assertTrue(val.isEx(), "Expected the cancellation exception, got: " + str(val));
			AussomException ex = (AussomException) val;
			assertEquals(Engine.CANCELLED_EXCEPTION_ID, ex.getId());
			assertTrue(ex.isCancellation());
		}

		@Test
		@DisplayName("4. sys.sleep is pausable and cancellable")
		void sleepIsControllable() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("include sys;");
			AtomicReference<Object> out = new AtomicReference<Object>();
			Thread worker = runAsync(eng, "sys.sleep(60000);", out, "sleeper");

			Thread.sleep(120);
			eng.cancel();
			worker.join(BOUND_MS);
			assertFalse(worker.isAlive(), "cancel() should reclaim a sleeping program.");

			Object result = out.get();
			assertFalse(result instanceof Throwable, "Unexpected throwable: " + kind(result));
			AussomType val = (AussomType) result;
			assertTrue(val.isEx(), "Expected the cancellation exception, got: " + str(val));
			assertEquals(Engine.CANCELLED_EXCEPTION_ID, ((AussomException) val).getId());
		}

		@Test
		@DisplayName("4b. The sleep slice comes from policy, and a smaller slice "
			+ "reclaims a sleeping program sooner")
		void sleepSliceFromPolicy() throws Exception {
			assertEquals(Limits.DEFAULT_SLEEP_SLICE_MS,
				scriptEngine().getLimits().getSleepSliceMs(),
				"The slice should come from the security manager by default.");

			// A host sets the slice in policy, before the engine is built.
			Engine eng = new Engine(new LimitSecMan().with(Limits.SLEEP_SLICE_PROP, 5L));
			eng.setScriptMode(true);
			assertEquals(5L, eng.getLimits().getSleepSliceMs());
			eng.evalLine("include sys;");

			AtomicReference<Object> out = new AtomicReference<Object>();
			Thread worker = runAsync(eng, "sys.sleep(60000);", out, "sleeper");
			Thread.sleep(60);
			long t0 = System.currentTimeMillis();
			eng.cancel();
			worker.join(BOUND_MS);
			long ms = System.currentTimeMillis() - t0;
			assertFalse(worker.isAlive(), "cancel() should reclaim a sleeping program.");
			assertTrue(ms < 1000L, "A 5 ms slice should stop it quickly, took " + ms + " ms.");
			assertEquals(Engine.CANCELLED_EXCEPTION_ID,
				((AussomException) out.get()).getId());
		}

		@Test
		@DisplayName("4c. Paired: a slice of 0 turns slicing off, so a sleeping "
			+ "program runs to the end of its sleep")
		void sleepSliceZeroDisablesControl() throws Exception {
			Engine eng = new Engine(new LimitSecMan().with(Limits.SLEEP_SLICE_PROP, 0L));
			eng.setScriptMode(true);
			eng.evalLine("include sys;");

			AtomicReference<Object> out = new AtomicReference<Object>();
			Thread worker = runAsync(eng, "sys.sleep(400);", out, "sleeper");
			Thread.sleep(60);
			eng.cancel();
			worker.join(BOUND_MS);
			assertFalse(worker.isAlive(), "The sleep should still finish on its own.");

			Object result = out.get();
			assertFalse(result instanceof Throwable, "Unexpected throwable: " + kind(result));
			assertFalse(((AussomType) result).isEx(),
				"With slicing off the cancel cannot reach the sleep, so it completes: "
					+ str((AussomType) result));
		}

		@Test
		@DisplayName("5. A negative sleep reports its own error")
		void negativeSleepRefused() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("include sys;");
			AussomType val = eng.evalLine("sys.sleep(-1);");
			assertTrue(val.isEx(), "A negative sleep should be an error, got: " + str(val));
			assertTrue(((AussomException) val).getText().contains("negative"),
				"Message should say what was wrong, was: " + ((AussomException) val).getText());
		}

		@Test
		@DisplayName("6. Two engines are independent: pausing one leaves the other running")
		void enginesAreIndependent() throws Exception {
			Engine a = scriptEngine();
			Engine b = scriptEngine();
			a.evalLine("total = 0;");
			b.evalLine("total = 0;");

			AtomicReference<Object> outA = new AtomicReference<Object>();
			AtomicReference<Object> outB = new AtomicReference<Object>();
			Thread wa = runAsync(a, "for (i = 0; i < 4000000; i++) { total = total + 1; }",
				outA, "tenant-a");
			Thread wb = runAsync(b, "for (i = 0; i < 4000000; i++) { total = total + 1; }",
				outB, "tenant-b");

			a.pause();
			assertTrue(a.awaitPaused(BOUND_MS, TimeUnit.MILLISECONDS),
				"Engine a never reported itself fully paused.");

			// b must be free to finish while a is held.
			wb.join(BOUND_MS);
			assertFalse(wb.isAlive(), "Pausing engine a stalled engine b.");
			assertEquals(ControlState.RUNNING, b.getControlState());
			assertEquals("4000000", str(b.evalLine("total;")));

			a.resume();
			wa.join(BOUND_MS);
			assertFalse(wa.isAlive(), "Engine a did not finish after resume().");
		}

		@Test
		@DisplayName("7. An engine nobody touches never pauses itself")
		void noSelfPause() throws Exception {
			Engine eng = scriptEngine();
			assertEquals(ControlState.RUNNING, eng.getControlState());
			eng.evalLine("x = 0; for (i = 0; i < 1000; i++) { x = x + i; }");
			assertEquals(ControlState.RUNNING, eng.getControlState());
			assertEquals(0, eng.getInterpreterThreadIds().length,
				"No interpreter threads should remain registered after a program.");
		}

		@Test
		@DisplayName("8. Registration is balanced after a cancellation too")
		void registrationBalancedAfterCancel() throws Exception {
			Engine eng = scriptEngine();
			AtomicReference<Object> out = new AtomicReference<Object>();
			Thread worker = runAsync(eng, "while (true) { x = 1; }", out, "spin");
			Thread.sleep(100);
			eng.cancel();
			worker.join(BOUND_MS);
			assertEquals(0, eng.getInterpreterThreadIds().length,
				"No interpreter threads should remain registered after a cancellation.");
		}
	}

	@Nested
	@DisplayName("accounting")
	class Accounting {

		@Test
		@DisplayName("1. CPU and allocation grow with work")
		void countersGrow() throws Exception {
			Engine eng = scriptEngine();
			eng.resetAccounting();
			eng.evalLine("s = 0; l = []; for (i = 0; i < 200000; i++) { "
				+ "s = s + i; l.add(i); }");

			if (ThreadMeter.isCpuAvailable()) {
				assertTrue(eng.getCpuNanos() > 0L, "CPU time should have been recorded.");
			} else {
				assertEquals(-1L, eng.getCpuNanos(), "Unavailable must read as -1.");
			}
			if (ThreadMeter.isAllocAvailable()) {
				assertTrue(eng.getAllocatedBytes() > 100000L,
					"Allocation should have been recorded, was: " + eng.getAllocatedBytes());
			} else {
				assertEquals(-1L, eng.getAllocatedBytes(), "Unavailable must read as -1.");
			}
		}

		@Test
		@DisplayName("2. Totals accumulate across programs and across threads")
		void totalsSpanThreadsAndRuns() throws Exception {
			if (!ThreadMeter.isAllocAvailable()) return;

			Engine eng = scriptEngine();
			eng.resetAccounting();

			// Two programs on two different threads, so the banking of a
			// finished thread's usage is what makes the total right. Both
			// counters read -1 once a thread is gone, so this is the case
			// that would silently lose work if the banking were missing.
			for (int i = 0; i < 2; i++) {
				AtomicReference<Object> out = new AtomicReference<Object>();
				Thread t = runAsync(eng, "l" + i + " = []; for (j = 0; j < 100000; j++) { l"
					+ i + ".add(j); }", out, "worker-" + i);
				t.join(BOUND_MS);
				assertFalse(out.get() instanceof Throwable, "Unexpected throwable: " + kind(out.get()));
			}

			long total = eng.getAllocatedBytes();
			assertTrue(total > 100000L, "Both runs should be counted, was: " + total);
			assertEquals(0, eng.getInterpreterThreadIds().length);
		}

		@Test
		@DisplayName("3. resetAccounting zeroes the totals")
		void resetZeroes() throws Exception {
			if (!ThreadMeter.isAllocAvailable()) return;

			Engine eng = scriptEngine();
			eng.evalLine("l = []; for (i = 0; i < 100000; i++) { l.add(i); }");
			assertTrue(eng.getAllocatedBytes() > 0L);
			eng.resetAccounting();
			assertEquals(0L, eng.getAllocatedBytes(),
				"After a reset the total should start again from zero.");
		}
	}

	@Nested
	@DisplayName("retained footprint")
	class Footprint {

		@Test
		@DisplayName("1. Holding data raises the measurement, dropping it lowers it")
		void measurementTracksData() throws Exception {
			Engine eng = scriptEngine();
			long empty = eng.measureRetainedFootprint();

			eng.evalLine("big = []; for (i = 0; i < 20000; i++) { big.add(\"a string value\"); }");
			long held = eng.measureRetainedFootprint();
			assertTrue(held > empty + 100000L,
				"A 20,000 element list should show up, empty=" + empty + " held=" + held);

			// Reading twice must give the same number on an idle engine.
			assertEquals(held, eng.measureRetainedFootprint(),
				"The measurement should be stable on an engine that is not running.");

			eng.evalLine("big = null;");
			long dropped = eng.measureRetainedFootprint();
			assertTrue(dropped < held,
				"Dropping the list should lower the measurement, held=" + held
					+ " dropped=" + dropped);
		}

		@Test
		@DisplayName("2. A structure that contains itself is measured, not walked forever")
		void selfReferenceTerminates() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("l = []; l.add(1); l.add(l);");
			long bytes = eng.measureRetainedFootprint();
			assertTrue(bytes > 0L, "Should have measured something.");
		}

		@Test
		@DisplayName("3. Measuring works while the engine is paused mid-run")
		void measureWhilePaused() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("l = [];");
			AtomicReference<Object> out = new AtomicReference<Object>();
			Thread worker = runAsync(eng,
				"for (i = 0; i < 200000; i++) { l.add(\"value\"); }", out, "builder");

			eng.pause();
			assertTrue(eng.awaitPaused(BOUND_MS, TimeUnit.MILLISECONDS),
				"The engine never reported itself fully paused.");
			long bytes = eng.measureRetainedFootprint();
			assertTrue(bytes > 0L, "Should have measured the data built so far.");

			eng.resume();
			worker.join(BOUND_MS);
			assertNotNull(out.get());
		}
	}
}

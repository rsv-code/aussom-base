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

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aussom.Engine;
import com.aussom.Limits;
import com.aussom.TestSecurityManagerImpl;
import com.aussom.stdlib.RegexSubject;
import com.aussom.types.AussomException;
import com.aussom.types.AussomType;
import com.aussom.types.AussomTypeInt;

/**
 * JUnit 5 coverage for the regular expression step budget, and for pause
 * and cancel reaching inside a running match.
 *
 * java.util.regex offers no timeout and does not check thread interrupt
 * status, so a pattern that backtracks exponentially cannot be
 * interrupted by any ordinary means. The subject the matcher reads is a
 * CharSequence, though, so the engine hands it a RegexSubject that
 * counts every character read: that count meters the work, and the same
 * hook is where a pause or a cancel gets in.
 *
 * The pattern used here, (a+)+\1b, backtracks through a backreference,
 * which current JVMs do not optimize away. Measured before this work: 26
 * characters took 2.25 seconds and 30 took 59 seconds. Every test that
 * runs it asserts a wall-clock bound, so a regression that removes the
 * budget fails the suite instead of hanging it.
 *
 * See design/security-evaluation-f4-f5.md section 4.2.
 */
@DisplayName("Regex step budget and control")
public class RegexBudget {

	/** The exponential case: a pattern and a subject, both short. */
	private static final String BAD_PATTERN = "(a+)+\\\\1b";

	/** How long any single exponential case may take before the test fails. */
	private static final long BOUND_MS = 15000L;

	private static Engine scriptEngine() throws Exception {
		Engine eng = new Engine(new TestSecurityManagerImpl());
		eng.setScriptMode(true);
		eng.evalLine("include util;");
		return eng;
	}

	/**
	 * An engine with a regex step budget, set the way a host sets it: in
	 * the security manager, before the engine is built.
	 */
	private static Engine scriptEngine(long budget) throws Exception {
		Engine eng = new Engine(new LimitSecMan().with(Limits.REGEX_STEPS_PROP, budget));
		eng.setScriptMode(true);
		eng.evalLine("include util;");
		return eng;
	}

	private static String repeat(String unit, int times) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < times; i++) sb.append(unit);
		return sb.toString();
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

	private static AussomException asEx(AussomType val) {
		assertNotNull(val, "Expected a value back.");
		assertTrue(val.isEx(), "Expected an exception, got: " + val.getType().name());
		return (AussomException) val;
	}

	/* ============================================================ */

	@Nested
	@DisplayName("budget")
	class Budget {

		@Test
		@DisplayName("1. With a budget set, ordinary matching still returns the right answer")
		void ordinaryMatchingUnaffected() throws Exception {
			Engine eng = scriptEngine(5000000L);
			AussomType val = eng.evalLine("m = regex.match(\"wor(ld)\", \""
				+ repeat("hello world ", 500) + "\"); n = m.size();");
			assertFalse(val.isEx(), "Ordinary matching should be unaffected, got: " + str(val));
			assertEquals("500", str(eng.evalLine("n;")));
		}

		@Test
		@DisplayName("2. The exponential case is refused with REGEX_BUDGET_EXCEEDED, quickly")
		void exponentialCaseRefused() throws Exception {
			Engine eng = scriptEngine(1000000L);
			long t0 = System.currentTimeMillis();
			AussomException ex = asEx(eng.evalLine("m = regex.match(\"" + BAD_PATTERN
				+ "\", \"" + repeat("a", 30) + "\");"));
			long ms = System.currentTimeMillis() - t0;
			assertEquals(RegexSubject.REGEX_BUDGET_EXCEEDED_ID, ex.getId());
			assertTrue(ms < BOUND_MS, "Should have been stopped promptly, took " + ms + " ms.");
			assertTrue(ex.getDetails().contains(Limits.REGEX_STEPS_PROP),
				"Details should name the property to raise, was: " + ex.getDetails());
		}

		@Test
		@DisplayName("3. Paired: with no budget the same ordinary matching works, "
			+ "so the default path is untouched")
		void noBudgetLeavesBehaviorAlone() throws Exception {
			Engine eng = scriptEngine();
			assertEquals(0L, eng.getLimits().getRegexSteps(), "Budget should default to off.");
			AussomType val = eng.evalLine("m = regex.match(\"wor(ld)\", \""
				+ repeat("hello world ", 500) + "\"); n = m.size();");
			assertFalse(val.isEx(), "Unbudgeted matching should work, got: " + str(val));
			assertEquals("500", str(eng.evalLine("n;")));
			// The exponential case is deliberately not run with the budget
			// off. It takes about a minute.
		}

		@Test
		@DisplayName("4. string.matches honors the budget")
		void stringMatchesHonorsBudget() throws Exception {
			Engine eng = scriptEngine(1000000L);
			long t0 = System.currentTimeMillis();
			AussomException ex = asEx(eng.evalLine("s = \"" + repeat("a", 30)
				+ "\"; b = s.matches(\"" + BAD_PATTERN + "\");"));
			long ms = System.currentTimeMillis() - t0;
			assertEquals(RegexSubject.REGEX_BUDGET_EXCEEDED_ID, ex.getId());
			assertTrue(ms < BOUND_MS, "Should have been stopped promptly, took " + ms + " ms.");
		}

		@Test
		@DisplayName("5. string.replaceRegex honors the budget")
		void stringReplaceHonorsBudget() throws Exception {
			Engine eng = scriptEngine(1000000L);
			long t0 = System.currentTimeMillis();
			AussomException ex = asEx(eng.evalLine("s = \"" + repeat("a", 30)
				+ "\"; r = s.replaceRegex(\"" + BAD_PATTERN + "\", \"z\");"));
			long ms = System.currentTimeMillis() - t0;
			assertEquals(RegexSubject.REGEX_BUDGET_EXCEEDED_ID, ex.getId());
			assertTrue(ms < BOUND_MS, "Should have been stopped promptly, took " + ms + " ms.");
		}

		@Test
		@DisplayName("6. string.split honors the budget")
		void stringSplitHonorsBudget() throws Exception {
			Engine eng = scriptEngine(1000000L);
			long t0 = System.currentTimeMillis();
			AussomException ex = asEx(eng.evalLine("s = \"" + repeat("a", 30)
				+ "\"; parts = s.split(\"" + BAD_PATTERN + "\", true);"));
			long ms = System.currentTimeMillis() - t0;
			assertEquals(RegexSubject.REGEX_BUDGET_EXCEEDED_ID, ex.getId());
			assertTrue(ms < BOUND_MS, "Should have been stopped promptly, took " + ms + " ms.");
		}

		@Test
		@DisplayName("7. split, matches and replace still behave exactly as before")
		void ordinarySemanticsPreserved() throws Exception {
			Engine eng = scriptEngine();
			AussomType val = eng.evalLine(
				"parts = \"a,b,,c\".split(\",\", true); "
				+ "n = parts.size(); "
				+ "m1 = \"hello\".matches(\"h.*o\"); "
				+ "m2 = \"hello\".matches(\"z.*\"); "
				+ "r = \"a1b2\".replaceRegex(\"[0-9]\", \"#\");");
			assertFalse(val.isEx(), "Ordinary string regex work broke: " + str(val));
			assertEquals("4", str(eng.evalLine("n;")));
			assertEquals("true", str(eng.evalLine("m1;")));
			assertEquals("false", str(eng.evalLine("m2;")));
			assertEquals("a#b#", str(eng.evalLine("r;")));
		}
	}

	@Nested
	@DisplayName("control reaches inside a match")
	class Control {

		@Test
		@DisplayName("1. cancel() from another thread stops a running match")
		void cancelStopsAMatch() throws Exception {
			final Engine eng = scriptEngine();
			final AtomicReference<Object> result = new AtomicReference<Object>();

			Thread worker = new Thread(() -> {
				try {
					result.set(eng.evalLine("m = regex.match(\"" + BAD_PATTERN
						+ "\", \"" + repeat("a", 30) + "\");"));
				} catch (Throwable t) {
					result.set(t);
				}
			}, "regex-worker");
			worker.start();

			// Give the match time to get going, then stop it.
			Thread.sleep(250);
			eng.cancel();
			worker.join(BOUND_MS);
			assertFalse(worker.isAlive(), "cancel() did not reach the running match.");

			Object out = result.get();
			assertFalse(out instanceof Throwable, "Unexpected throwable: " + kind(out));
			AussomException ex = asEx((AussomType) out);
			assertEquals(Engine.CANCELLED_EXCEPTION_ID, ex.getId());
			assertTrue(ex.isCancellation(), "Should carry the cancellation flag.");
		}

		@Test
		@DisplayName("2. pause() holds a running match, and resume() lets it finish "
			+ "with the same answer as an un-paused run")
		void pauseHoldsAMatch() throws Exception {
			// A subject big enough that the match takes a while but still
			// finishes: 20 characters is about 0.1 seconds of work.
			final String subject = repeat("a", 20);

			// Un-paused reference run.
			Engine ref = scriptEngine();
			ref.evalLine("m = regex.match(\"" + BAD_PATTERN + "\", \"" + subject
				+ "\"); n = m.size();");
			String expected = str(ref.evalLine("n;"));

			final Engine eng = scriptEngine();
			final AtomicReference<Object> result = new AtomicReference<Object>();
			Thread worker = new Thread(() -> {
				try {
					result.set(eng.evalLine("m = regex.match(\"" + BAD_PATTERN + "\", \""
						+ subject + "\"); n = m.size();"));
				} catch (Throwable t) {
					result.set(t);
				}
			}, "regex-worker");
			worker.start();

			eng.pause();
			// The match reads its subject constantly, so it reaches a
			// checkpoint quickly. If it had already finished, this simply
			// reports no running threads, which is also fully paused.
			assertTrue(eng.awaitPaused(BOUND_MS, java.util.concurrent.TimeUnit.MILLISECONDS),
				"The engine never reported itself fully paused.");

			eng.resume();
			worker.join(BOUND_MS);
			assertFalse(worker.isAlive(), "The match did not finish after resume().");

			Object out = result.get();
			assertFalse(out instanceof Throwable, "Unexpected throwable: " + kind(out));
			assertFalse(((AussomType) out).isEx(),
				"A paused and resumed match should succeed, got: " + str((AussomType) out));
			assertEquals(expected, str(eng.evalLine("n;")),
				"Pausing must not change the answer.");
		}
	}
}

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aussom.Engine;
import com.aussom.Limits;
import com.aussom.TestSecurityManagerImpl;
import com.aussom.types.AussomException;
import com.aussom.types.AussomType;
import com.aussom.types.AussomTypeInt;

/**
 * JUnit 5 coverage for how the engine reads its numeric runtime settings
 * from policy, and for the argument validation on Buffer sizes.
 *
 * There are deliberately no per-value size caps to test. Earlier drafts
 * capped string length, list and map element counts, and buffer bytes;
 * all four were removed because a cap on one value bounds neither memory
 * nor anything else a host can reason about. Element count is not memory,
 * one value's cap says nothing about many values, and a single allocation
 * larger than the heap is refused cleanly by the JVM while sustained
 * retention is what actually breaks other tenants. Retention is covered
 * by Engine.getAllocatedBytes, Engine.measureRetainedFootprint and a host
 * deadline instead.
 *
 * The three settings that remain each bound something the JVM does not:
 * call depth (tested in CallDepth), regex steps (tested in RegexBudget)
 * and the sleep slice (tested in EngineControl). What is tested here is
 * that all three are read from the security manager correctly, including
 * when a value is missing or unusable.
 *
 * See design/security-evaluation-f4-f5.md section 5.
 */
@DisplayName("Resource limits")
public class ResourceLimits {

	private static Engine scriptEngine() throws Exception {
		Engine eng = new Engine(new TestSecurityManagerImpl());
		eng.setScriptMode(true);
		return eng;
	}

	private static String str(AussomType val) {
		if (val == null) return "null";
		String s = ((AussomTypeInt) val).str();
		if (s.length() > 200) return s.substring(0, 200) + "... (" + s.length() + " chars)";
		return s;
	}

	private static AussomException asEx(AussomType val) {
		assertNotNull(val, "Expected a value back.");
		assertTrue(val.isEx(), "Expected an exception, got: " + val.getType().name());
		return (AussomException) val;
	}

	/* ============================================================ */

	@Nested
	@DisplayName("buffer argument validation")
	class BufferValidation {

		// These are argument checks, not policy. There is no configurable
		// ceiling on buffer size: a cap on one value bounds neither memory
		// nor anything a host can reason about, and the JVM refuses a
		// single allocation larger than the heap cleanly on its own. What
		// is worth refusing is a size that would quietly allocate
		// something other than what the script asked for.

		@Test
		@DisplayName("1. Paired: an ordinary size works, a size too large to "
			+ "address is refused instead of silently wrapping")
		void sizeNoLongerWraps() throws Exception {
			Engine eng = scriptEngine();

			AussomType ok = eng.evalLine("b = new Buffer(512); n = b.size();");
			assertFalse(ok.isEx(), "512 bytes should allocate, got: " + str(ok));
			assertEquals("512", str(eng.evalLine("n;")));

			// 4294967296 narrowed to an int is 0, which used to allocate an
			// empty buffer and report nothing at all.
			AussomException ex = asEx(eng.evalLine("big = new Buffer(4294967296);"));
			assertTrue(ex.getText().contains("larger than a Java array"),
				"Should say why it was refused, was: " + ex.getText());
		}

		@Test
		@DisplayName("2. A negative size is refused")
		void negativeSizeRefused() throws Exception {
			Engine eng = scriptEngine();
			AussomException ex = asEx(eng.evalLine("b = new Buffer(-1);"));
			assertTrue(ex.getText().contains("negative"),
				"Should say why it was refused, was: " + ex.getText());
		}

		@Test
		@DisplayName("3. Reading a string past the end of the buffer is refused "
			+ "before anything is allocated")
		void readPastEndRefused() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("b = new Buffer(16);");
			AussomException ex = asEx(eng.evalLine("s = b.getStringAt(64, 0);"));
			assertTrue(ex.getText().contains("past the end"),
				"Should say why it was refused, was: " + ex.getText());
		}

		@Test
		@DisplayName("4. An index that does not fit in an int is refused, not narrowed")
		void oversizedIndexRefused() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("b = new Buffer(16); b.setByte(0, 65);");

			// 2^32 casts to 0, so this used to read index 0 and answer 65.
			AussomException ex = asEx(eng.evalLine("v = b.getByte(4294967296);"));
			assertTrue(ex.getText().contains("out of range"),
				"Should say why it was refused, was: " + ex.getText());

			// 2^32-1 casts to -1, which is the "read at the cursor"
			// convention, so the argument used to change meaning and the
			// cursor moved with it.
			eng.evalLine("b.readSeek(5);");
			asEx(eng.evalLine("v = b.getByte(4294967295);"));
			assertEquals("5", str(eng.evalLine("b.getReadCursor();")),
				"A refused read must not move the cursor.");
		}

		@Test
		@DisplayName("5. Paired: a negative index still reads at the cursor and advances it")
		void negativeIndexStillMeansCursor() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("b = new Buffer(16); b.setByte(5, 90); b.readSeek(5);");
			assertEquals("90", str(eng.evalLine("b.getByte(-1);")),
				"The documented cursor convention must survive the fix.");
			assertEquals("6", str(eng.evalLine("b.getReadCursor();")),
				"And it still advances the cursor.");
		}

		@Test
		@DisplayName("6. Paired: multi-byte reads at the tail report cleanly, and the "
			+ "last valid index still works")
		void widthGuardsReportCleanly() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("b = new Buffer(16);");

			// Valid boundary reads: 2 bytes at 14, 4 at 12, 8 at 8.
			assertFalse(eng.evalLine("s = b.getShort(14);").isEx(), "getShort(14) is valid.");
			assertFalse(eng.evalLine("i = b.getInt(12);").isEx(), "getInt(12) is valid.");
			assertFalse(eng.evalLine("l = b.getLong(8);").isEx(), "getLong(8) is valid.");

			// One past each: the guard must report it rather than letting
			// the array access fail with a Java error.
			for (String call : new String[] { "b.getShort(15)", "b.getInt(13)", "b.getLong(9)" }) {
				AussomException ex = asEx(eng.evalLine("x = " + call + ";"));
				assertTrue(ex.getText().contains("Index out of bounds"),
					call + " should report a range error, was: " + ex.getText());
				assertFalse(ex.getText().contains("ArrayIndexOutOfBounds"),
					call + " should not surface a Java error, was: " + ex.getText());
			}
		}

		@Test
		@DisplayName("7. A refusal is catchable and the engine keeps working")
		void refusalIsCatchableAndRecoverable() throws Exception {
			Engine eng = scriptEngine();
			eng.evalLine("class t { public t() { } "
				+ "public go() { try { b = new Buffer(-1); return \"no error\"; } "
				+ "catch (e) { return \"caught\"; } } }");
			AussomType val = eng.evalLine("r = new t().go();");
			assertFalse(val.isEx(), "The catch block should have handled it, got: " + str(val));
			assertEquals("caught", str(eng.evalLine("r;")));

			AussomType after = eng.evalLine("x = 2 + 2;");
			assertFalse(after.isEx(), "Engine should still work, got: " + str(after));
			assertEquals("4", str(eng.evalLine("x;")));
		}
	}

	@Nested
	@DisplayName("policy")
	class Policy {

		@Test
		@DisplayName("1. Limits come from the security manager")
		void limitsReadFromPolicy() throws Exception {
			LimitSecMan sm = new LimitSecMan();
			sm.with(Limits.CALL_DEPTH_PROP, Long.valueOf(60L));
			sm.with(Limits.REGEX_STEPS_PROP, Long.valueOf(1000000L));
			sm.with(Limits.SLEEP_SLICE_PROP, Long.valueOf(7L));
			Engine eng = new Engine(sm);
			eng.setScriptMode(true);

			assertEquals(60L, eng.getLimits().getCallDepth());
			assertEquals(1000000L, eng.getLimits().getRegexSteps());
			assertEquals(7L, eng.getLimits().getSleepSliceMs());
			assertEquals(60, eng.getMaxCallDepth(),
				"The cached depth used on the call path must agree with policy.");
		}

		@Test
		@DisplayName("2. A missing or unusable value denies nothing and throws nothing")
		void unusableValuesFallBack() throws Exception {
			LimitSecMan sm = new LimitSecMan();
			sm.without(Limits.REGEX_STEPS_PROP);
			sm.with(Limits.CALL_DEPTH_PROP, "not a number");
			sm.with(Limits.SLEEP_SLICE_PROP, Long.valueOf(-5L));

			Engine eng = new Engine(sm);
			eng.setScriptMode(true);
			assertEquals(0L, eng.getLimits().getRegexSteps(),
				"Missing key should mean no budget.");
			assertEquals(Limits.DEFAULT_CALL_DEPTH, eng.getLimits().getCallDepth(),
				"Bad type should fall back to the default rather than throw.");
			assertEquals(Limits.DEFAULT_SLEEP_SLICE_MS, eng.getLimits().getSleepSliceMs(),
				"A negative value has no meaning; should fall back to the default.");

			AussomType ok = eng.evalLine("l = []; for (i = 0; i < 50; i++) { l.add(i); }");
			assertFalse(ok.isEx(), "Nothing should be refused, got: " + str(ok));
		}

		@Test
		@DisplayName("3. A limit stored as something other than an integer is not "
			+ "converted; the default stands")
		void wrongTypeIsNotConverted() throws Exception {
			// The security manager's typed reads never convert a value. The
			// string "12" is not an integer, so the limit is not set to 12,
			// it is left at its default. See SecurityManagerInt.
			LimitSecMan sm = new LimitSecMan();
			sm.with(Limits.REGEX_STEPS_PROP, "12");
			sm.with(Limits.CALL_DEPTH_PROP, Double.valueOf(50.0));
			Engine eng = new Engine(sm);

			assertEquals(0L, eng.getLimits().getRegexSteps(),
				"A string is not an integer, so no budget is set.");
			assertEquals(Limits.DEFAULT_CALL_DEPTH, eng.getLimits().getCallDepth(),
				"A double is not an integer either.");

			// An Integer and a Long both read, since both are integers.
			LimitSecMan ok = new LimitSecMan();
			ok.with(Limits.REGEX_STEPS_PROP, Integer.valueOf(12));
			assertEquals(12L, new Engine(ok).getLimits().getRegexSteps());
		}

		@Test
		@DisplayName("4. Every limit key is visible to a script through secman")
		void keysAreVisible() throws Exception {
			Engine eng = scriptEngine();
			// Asked one key at a time rather than by joining the whole key
			// set, which is long enough that a truncated rendering would
			// make this test pass or fail for the wrong reason.
			assertEquals("true", str(eng.evalLine(
				"secman.keySet().contains(\"" + Limits.CALL_DEPTH_PROP + "\");")),
				"call depth key missing");
			assertEquals("true", str(eng.evalLine(
				"secman.keySet().contains(\"" + Limits.REGEX_STEPS_PROP + "\");")),
				"regex steps key missing");
			assertEquals("1000", str(eng.evalLine(
				"secman.getProp(\"" + Limits.CALL_DEPTH_PROP + "\");")),
				"call depth should read back as the default");
			assertEquals("true", str(eng.evalLine(
				"secman.keySet().contains(\"" + Limits.SLEEP_SLICE_PROP + "\");")),
				"sleep slice key missing");
			assertEquals("50", str(eng.evalLine(
				"secman.getProp(\"" + Limits.SLEEP_SLICE_PROP + "\");")),
				"sleep slice should read back as the default");
		}
	}
}

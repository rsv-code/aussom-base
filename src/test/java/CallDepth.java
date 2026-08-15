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
import com.aussom.types.AussomException;
import com.aussom.types.AussomType;
import com.aussom.types.AussomTypeInt;

/**
 * JUnit 5 coverage for the call depth limit and for the conversion of a
 * Java stack overflow into an Aussom exception.
 *
 * Before this work, runaway recursion in an Aussom program threw a
 * StackOverflowError that killed the interpreter thread: Aussom
 * try/catch never saw it, and neither did a host catching Exception.
 * Two independent changes fixed that, and both are tested here. The
 * depth limit turns recursion into a normal catchable exception at the
 * call that went too deep, and the boundary conversion catches what the
 * depth counter cannot see, such as a single expression with thousands
 * of terms and no function call in it.
 *
 * Script mode is the seam for most cases because evalLine hands runtime
 * errors back as AussomException values rather than printing them.
 *
 * See design/security-evaluation-f4-f5.md sections 3.1 and 3.2.
 */
@DisplayName("Call depth and stack overflow")
public class CallDepth {

	private static Engine scriptEngine() throws Exception {
		Engine eng = new Engine(new TestSecurityManagerImpl());
		eng.setScriptMode(true);
		return eng;
	}

	/**
	 * An engine whose call depth limit is set the way a host sets it: in
	 * the security manager, before the engine is built. There is no
	 * setter on Engine for this, deliberately.
	 */
	private static Engine scriptEngine(long callDepth) throws Exception {
		Engine eng = new Engine(new LimitSecMan().with(Limits.CALL_DEPTH_PROP, callDepth));
		eng.setScriptMode(true);
		return eng;
	}

	/**
	 * Declares a class with a recursive method that counts down, and one
	 * that never terminates.
	 */
	private static void declareRec(Engine eng) throws Exception {
		eng.evalLine("class rec { "
			+ "public rec() { } "
			+ "public down(int n) { if (n <= 0) { return 0; } return this.down(n - 1); } "
			+ "public forever(int n) { return this.forever(n + 1); } "
			+ "public guarded() { try { return this.forever(0); } "
			+ "catch (e) { return e.getId(); } } "
			+ "}");
	}

	/** Renders a value for a failure message. */
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
	 * Builds a single expression with many terms. There is no function
	 * call anywhere in it, so the depth counter cannot see it; only the
	 * boundary conversion can.
	 */
	private static String longExpression(int terms) {
		StringBuilder sb = new StringBuilder("x = 1");
		for (int i = 0; i < terms; i++) {
			sb.append("+1");
		}
		sb.append(";");
		return sb.toString();
	}

	private static AussomException asEx(AussomType val) {
		assertNotNull(val, "Expected a value back.");
		assertTrue(val.isEx(), "Expected an exception, got: " + val.getType().name());
		return (AussomException) val;
	}

	/* ============================================================ */

	@Nested
	@DisplayName("depth limit")
	class DepthLimit {

		@Test
		@DisplayName("1. Recursion well inside the limit still works")
		void shallowRecursionWorks() throws Exception {
			Engine eng = scriptEngine();
			declareRec(eng);
			AussomType val = eng.evalLine("r = new rec(); x = r.down(100);");
			assertFalse(val.isEx(), "100-deep recursion should be fine, got: " + str(val));
		}

		@Test
		@DisplayName("2. Unbounded recursion reports CALL_DEPTH_EXCEEDED, not an Error")
		void unboundedRecursionRefused() throws Exception {
			// A limit the JUnit thread's stack can actually reach. The
			// default of 1000 sits above where a 1 MB stack gives out at
			// roughly 550 Aussom frames, which is deliberate (it cannot
			// refuse a program that ran before) and is what the next case
			// covers.
			Engine eng = scriptEngine(200);
			declareRec(eng);
			AussomException ex = asEx(eng.evalLine("r = new rec(); x = r.forever(0);"));
			assertEquals(Engine.CALL_DEPTH_EXCEEDED_ID, ex.getId());
			assertFalse(ex.isCancellation(),
				"A depth breach is a program fault, not a host cancellation.");
		}

		@Test
		@DisplayName("2b. On a small stack the default limit does not fire, and the "
			+ "boundary conversion catches it instead")
		void defaultLimitAboveASmallStack() throws Exception {
			Engine eng = scriptEngine();
			assertEquals((int) Limits.DEFAULT_CALL_DEPTH, eng.getMaxCallDepth(),
				"Default call depth should come from policy.");
			declareRec(eng);
			// This documents the honest division of labour: on a default
			// thread stack the Java stack runs out first, and the program
			// still comes back as a value rather than killing the thread.
			AussomException ex = asEx(eng.evalLine("r = new rec(); x = r.forever(0);"));
			assertEquals(Engine.STACK_OVERFLOW_ID, ex.getId());
		}

		@Test
		@DisplayName("3. Paired: with the limit at 50, 40 deep passes and 60 deep is refused")
		void limitIsTheThingThatFires() throws Exception {
			Engine eng = scriptEngine(50);
			declareRec(eng);

			AussomType ok = eng.evalLine("r = new rec(); a = r.down(40);");
			assertFalse(ok.isEx(), "40 deep should pass under a limit of 50, got: " + str(ok));

			AussomException ex = asEx(eng.evalLine("b = r.down(60);"));
			assertEquals(Engine.CALL_DEPTH_EXCEEDED_ID, ex.getId());
		}

		@Test
		@DisplayName("4. The message names the limit and the function")
		void messageIsUseful() throws Exception {
			Engine eng = scriptEngine(20);
			declareRec(eng);
			AussomException ex = asEx(eng.evalLine("r = new rec(); x = r.down(100);"));
			assertTrue(ex.getText().contains("20"),
				"Message should name the limit, was: " + ex.getText());
			assertTrue(ex.getText().contains("down"),
				"Message should name the function, was: " + ex.getText());
			assertTrue(ex.getDetails().contains(Limits.CALL_DEPTH_PROP),
				"Details should name the property to raise, was: " + ex.getDetails());
		}

		@Test
		@DisplayName("5. A script can catch it")
		void catchableInAussom() throws Exception {
			Engine eng = scriptEngine(40);
			declareRec(eng);
			AussomType val = eng.evalLine("r = new rec(); id = r.guarded();");
			assertFalse(val.isEx(), "The catch block should have handled it, got: " + str(val));
			AussomType id = eng.evalLine("id;");
			assertEquals(Engine.CALL_DEPTH_EXCEEDED_ID, str(id).replace("\"", ""),
				"The catch block should have seen the depth exception.");
		}

		@Test
		@DisplayName("6. The engine still works after a refusal")
		void engineReusableAfterRefusal() throws Exception {
			Engine eng = scriptEngine(30);
			declareRec(eng);
			asEx(eng.evalLine("r = new rec(); x = r.forever(0);"));

			AussomType val = eng.evalLine("y = r.down(10);");
			assertFalse(val.isEx(), "Engine should still run after a depth refusal, got: " + str(val));
		}

		@Test
		@DisplayName("6b. A policy change between submissions takes effect on the next one")
		void policyIsRereadPerProgram() throws Exception {
			LimitSecMan sm = new LimitSecMan().with(Limits.CALL_DEPTH_PROP, 500L);
			Engine eng = new Engine(sm);
			eng.setScriptMode(true);
			declareRec(eng);

			AussomType ok = eng.evalLine("r = new rec(); a = r.down(60);");
			assertFalse(ok.isEx(), "60 deep should pass under a limit of 500, got: " + str(ok));

			// The host tightens its own policy; the next program sees it.
			sm.with(Limits.CALL_DEPTH_PROP, 50L);
			asEx(eng.evalLine("b = r.down(60);"));
			assertEquals(50, eng.getMaxCallDepth(),
				"The cached depth should have been refreshed from policy.");
		}

		@Test
		@DisplayName("7. Zero means no limit")
		void zeroDisablesTheLimit() throws Exception {
			Engine eng = scriptEngine(0);
			assertEquals(0, eng.getMaxCallDepth());
			declareRec(eng);
			// 200 deep is fine on any stack; the point is that the limit
			// is not consulted, which the next test covers for the case
			// where the stack itself runs out.
			AussomType val = eng.evalLine("r = new rec(); x = r.down(200);");
			assertFalse(val.isEx(), "With no limit, 200 deep should run, got: " + str(val));
		}
	}

	@Nested
	@DisplayName("stack overflow conversion")
	class OverflowConversion {

		@Test
		@DisplayName("1. A huge expression comes back as STACK_OVERFLOW, not an Error")
		void overflowBecomesAValue() throws Exception {
			// Run on a thread with a small stack so the case is
			// deterministic rather than dependent on the JVM default.
			final AtomicReference<Object> result = new AtomicReference<Object>();
			Runnable body = () -> {
				try {
					Engine eng = scriptEngine(0);
					result.set(eng.evalLine(longExpression(20000)));
				} catch (Throwable t) {
					result.set(t);
				}
			};
			Thread t = new Thread(null, body, "small-stack", 256L * 1024L);
			t.start();
			t.join(60000);

			Object out = result.get();
			assertFalse(out instanceof Throwable,
				"Nothing should escape as a Throwable, got: " + out);
			AussomException ex = asEx((AussomType) out);
			assertEquals(Engine.STACK_OVERFLOW_ID, ex.getId());
		}

		@Test
		@DisplayName("2. Paired: a modest expression on the same small stack still evaluates")
		void modestExpressionStillWorks() throws Exception {
			final AtomicReference<Object> result = new AtomicReference<Object>();
			Runnable body = () -> {
				try {
					Engine eng = scriptEngine();
					result.set(eng.evalLine(longExpression(50)));
				} catch (Throwable t) {
					result.set(t);
				}
			};
			Thread t = new Thread(null, body, "small-stack", 256L * 1024L);
			t.start();
			t.join(60000);

			Object out = result.get();
			assertFalse(out instanceof Throwable, "Unexpected throwable: " + kind(out));
			AussomType val = (AussomType) out;
			assertFalse(val.isEx(), "51 terms should evaluate, got: " + str(val));
			assertEquals("51", str(val));
		}

		@Test
		@DisplayName("3. The engine still works after an overflow")
		void engineReusableAfterOverflow() throws Exception {
			final AtomicReference<Object> result = new AtomicReference<Object>();
			Runnable body = () -> {
				try {
					Engine eng = scriptEngine(0);
					eng.evalLine(longExpression(20000));
					// Same engine, ordinary work.
					result.set(eng.evalLine("y = 2 + 2;"));
				} catch (Throwable t) {
					result.set(t);
				}
			};
			Thread t = new Thread(null, body, "small-stack", 256L * 1024L);
			t.start();
			t.join(60000);

			Object out = result.get();
			assertFalse(out instanceof Throwable, "Unexpected throwable: " + kind(out));
			AussomType val = (AussomType) out;
			assertFalse(val.isEx(), "Engine should still work, got: " + str(val));
			assertEquals("4", str(val));
		}
	}
}

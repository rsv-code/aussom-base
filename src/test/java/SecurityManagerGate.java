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
import com.aussom.types.AussomException;
import com.aussom.types.AussomType;
import com.aussom.types.AussomTypeInt;

/**
 * JUnit 5 coverage for the securitymanager.instantiate gate.
 *
 * The gate used to be in ASecurityManager's Java constructor, reading
 * this.getProperty("securitymanager.instantiate"). It could never refuse:
 * "this" is the object being built, and SecurityManagerImpl's constructor
 * had just populated its property map with instantiate = true, so the
 * running engine's policy was never consulted. A constructor cannot do
 * better, because it has no Environment and therefore no way to reach the
 * engine.
 *
 * The check now lives in ASecurityManager.newSecurityManager(), which the
 * Aussom constructor in lang.aus calls, so it reads the policy of the
 * engine that is actually running.
 *
 * See F6 in design/security-evaluation-f6-f10.md.
 */
@DisplayName("Security manager instantiate gate")
public class SecurityManagerGate {

	private static final String INSTANTIATE = "securitymanager.instantiate";

	private static Engine scriptEngine(boolean allowInstantiate) throws Exception {
		LimitSecMan sm = new LimitSecMan().with(INSTANTIATE, Boolean.valueOf(allowInstantiate));
		Engine eng = new Engine(sm);
		eng.setScriptMode(true);
		return eng;
	}

	private static String str(AussomType val) {
		if (val == null) return "null";
		String s = ((AussomTypeInt) val).str();
		if (s.length() > 300) return s.substring(0, 300) + "...";
		return s;
	}

	private static AussomException asEx(AussomType val) {
		assertNotNull(val, "Expected a value back.");
		assertTrue(val.isEx(), "Expected an exception, got: " + val.getType().name());
		return (AussomException) val;
	}

	/* ============================================================ */

	@Nested
	@DisplayName("the gate")
	class Gate {

		@Test
		@DisplayName("1. Policy denies instantiation: new SecurityManager() is refused")
		void deniedWhenPolicySaysNo() throws Exception {
			Engine eng = scriptEngine(false);
			AussomException ex = asEx(eng.evalLine("sm = new SecurityManager();"));
			assertTrue(ex.getText().contains(INSTANTIATE),
				"The message should name the property, was: " + ex.getText());
		}

		@Test
		@DisplayName("2. Paired: policy allows it, so the object is built and is writable")
		void allowedWhenPolicySaysYes() throws Exception {
			Engine eng = scriptEngine(true);
			AussomType val = eng.evalLine("sm = new SecurityManager();");
			assertFalse(val.isEx(), "Should have constructed, got: " + str(val));
			assertEquals("true", str(eng.evalLine("sm instanceof 'SecurityManager';")));

			// The new object is a policy value the script owns, so it may
			// read and write it. That is what newSecurityManager() opens up
			// once the gate passes.
			AussomType wrote = eng.evalLine("sm.setProp('a.test.prop', true); "
				+ "v = sm.getProp('a.test.prop');");
			assertFalse(wrote.isEx(), "Should be writable, got: " + str(wrote));
			assertEquals("true", str(eng.evalLine("v;")));
		}

		@Test
		@DisplayName("3. The refusal is catchable, and the engine keeps working")
		void refusalIsCatchableAndRecoverable() throws Exception {
			Engine eng = scriptEngine(false);
			eng.evalLine("class t { public t() { } "
				+ "public go() { try { s = new SecurityManager(); return \"built\"; } "
				+ "catch (e) { return \"caught\"; } } }");
			AussomType val = eng.evalLine("r = new t().go();");
			assertFalse(val.isEx(), "The catch block should have handled it, got: " + str(val));
			assertEquals("caught", str(eng.evalLine("r;")));

			AussomType after = eng.evalLine("x = 2 + 2;");
			assertFalse(after.isEx(), "Engine should still work, got: " + str(after));
			assertEquals("4", str(eng.evalLine("x;")));
		}

		@Test
		@DisplayName("4. Denying instantiation does not stop an engine from being built, "
			+ "and leaves the static secman class working")
		void enginesStillBuildAndSecmanStillWorks() throws Exception {
			// Two things could go wrong with a gate placed carelessly.
			// Resolving an extern class at parse time must not consult it,
			// or lang.aus itself would fail to load. And secman is a
			// different class (ASecMan) that only forwards to the engine's
			// own manager, so it must be unaffected.
			Engine eng = scriptEngine(false);
			assertFalse(eng.hasParseErrors(), "lang.aus should have loaded cleanly.");

			AussomType val = eng.evalLine("p = secman.getProp('" + INSTANTIATE + "');");
			assertFalse(val.isEx(), "secman should still read policy, got: " + str(val));
			assertEquals("false", str(eng.evalLine("p;")));
		}

		@Test
		@DisplayName("5. The engine's own policy is what decides, not the new object's copy")
		void enginePolicyDecides() throws Exception {
			// The regression this whole finding is about. An object built
			// under a permissive engine carries instantiate = true in its
			// own property map, and that value must have no bearing on
			// whether a different engine allows instantiation.
			Engine permissive = scriptEngine(true);
			AussomType built = permissive.evalLine("sm = new SecurityManager(); "
				+ "own = sm.getProp('" + INSTANTIATE + "');");
			assertFalse(built.isEx(), "Should have constructed, got: " + str(built));
			assertEquals("true", str(permissive.evalLine("own;")),
				"The object carries the property as true, which is the trap.");

			Engine denied = scriptEngine(false);
			asEx(denied.evalLine("sm = new SecurityManager();"));
		}
	}

}

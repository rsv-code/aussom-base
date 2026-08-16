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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aussom.Engine;
import com.aussom.SecurityManagerImpl;
import com.aussom.types.AussomException;
import com.aussom.types.AussomType;
import com.aussom.types.AussomTypeInt;

/**
 * JUnit 5 coverage for the securitymanager.instantiate gate (F6) and for
 * the typed policy reads that every gate now uses (F7).
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
 * See F6 and F7 in design/security-evaluation-f6-f9.md.
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

	/**
	 * A manager that holds no properties at all. Its getProperty always
	 * answers null, which is what an unpopulated policy looks like.
	 */
	private static class EmptySecMan extends SecurityManagerImpl {
		EmptySecMan() {
			this.props.clear();
		}
	}

	/* ============================================================ */

	@Nested
	@DisplayName("typed policy reads")
	class TypedReads {

		private SecurityManagerImpl loaded() {
			return new LimitSecMan()
				.with("t.bool.true", Boolean.TRUE)
				.with("t.bool.false", Boolean.FALSE)
				.with("t.bool.string", "true")
				.with("t.int.long", Long.valueOf(7L))
				.with("t.int.integer", Integer.valueOf(9))
				.with("t.int.string", "11")
				.with("t.int.bad", "not a number")
				.with("t.double", Double.valueOf(2.5))
				.with("t.string", "hello")
				.with("t.number.as.string", Long.valueOf(42L))
				.with("t.list", java.util.Arrays.asList("a", "b"))
				.with("t.map", java.util.Collections.singletonMap("k", "v"));
		}

		@Test
		@DisplayName("1. Booleans: a real value wins, and no match takes the default")
		void booleans() {
			SecurityManagerImpl sm = loaded();
			assertTrue(sm.getPropertyBoolean("t.bool.true", false));
			assertFalse(sm.getPropertyBoolean("t.bool.false", true),
				"A real value wins over the default.");

			// Every gate passes false, so an unanswered question denies.
			assertFalse(sm.getPropertyBoolean("t.missing", false), "Absent must deny.");
			assertFalse(sm.getPropertyBoolean("t.bool.string", false),
				"The string \"true\" must not grant a permission.");
			assertFalse(sm.getPropertyBoolean("t.int.long", false),
				"A number must not grant either.");

			// And a caller that wants the other direction says so.
			assertTrue(sm.getPropertyBoolean("t.missing", true));
			assertTrue(sm.getPropertyBoolean("t.bool.string", true),
				"A wrong-typed value is no match, so the default stands.");
		}

		@Test
		@DisplayName("2. Numbers: stored as the right kind of number, or the default. "
			+ "Nothing is parsed or widened")
		void numbers() {
			SecurityManagerImpl sm = loaded();
			assertEquals(7L, sm.getPropertyInt("t.int.long", 5));
			assertEquals(9L, sm.getPropertyInt("t.int.integer", 5),
				"An Integer reads as a long.");
			assertEquals(5L, sm.getPropertyInt("t.missing", 5));

			// A value of the wrong type is no match. It is not converted.
			assertEquals(5L, sm.getPropertyInt("t.int.string", 5),
				"The string \"11\" is not an integer.");
			assertEquals(5L, sm.getPropertyInt("t.int.bad", 5), "Neither is a non-numeric string.");
			assertEquals(5L, sm.getPropertyInt("t.double", 5), "A Double is not an integer.");
			assertEquals(5L, sm.getPropertyInt("t.bool.true", 5), "Nor is a boolean.");

			assertEquals(2.5, sm.getPropertyDouble("t.double", 1.5), 0.0001);
			assertEquals(1.5, sm.getPropertyDouble("t.missing", 1.5), 0.0001);
			assertEquals(1.5, sm.getPropertyDouble("t.int.long", 1.5), 0.0001,
				"An integer is not a double.");
			assertEquals(1.5, sm.getPropertyDouble("t.int.string", 1.5), 0.0001,
				"A string is not a double.");
		}

		@Test
		@DisplayName("3. Strings: the default on no match, and a value of another type "
			+ "is not rendered")
		void strings() {
			SecurityManagerImpl sm = loaded();
			assertEquals("hello", sm.getPropertyString("t.string", "fallback"));
			assertEquals("fallback", sm.getPropertyString("t.missing", "fallback"));
			assertNull(sm.getPropertyString("t.missing", null),
				"A null default is a legitimate way to ask for absence.");
			assertEquals("fallback", sm.getPropertyString("t.number.as.string", "fallback"),
				"A number is not a string, and is not rendered as one.");
			assertEquals("fallback", sm.getPropertyString("t.bool.true", "fallback"),
				"Nor is a boolean.");
		}

		@Test
		@DisplayName("4. Lists and maps: null on no match, and a copy when present")
		void listsAndMaps() {
			SecurityManagerImpl sm = loaded();
			assertEquals(2, sm.getPropertyList("t.list").size());
			assertEquals("a", sm.getPropertyList("t.list").get(0));
			assertNull(sm.getPropertyList("t.missing"));
			assertNull(sm.getPropertyList("t.string"), "A non-collection is no match.");

			assertEquals("v", sm.getPropertyMap("t.map").get("k"));
			assertNull(sm.getPropertyMap("t.missing"));
			assertNull(sm.getPropertyMap("t.list"), "A list is not a map.");

			// A non-String key is dropped rather than renamed.
			SecurityManagerImpl odd = new LimitSecMan().with("t.oddkeys",
				java.util.Collections.singletonMap(Integer.valueOf(1), "v"));
			assertTrue(odd.getPropertyMap("t.oddkeys").isEmpty(),
				"An entry keyed by a number is not converted to a string key.");

			// The returned collections are copies, so a caller cannot edit
			// policy through a value it was handed.
			sm.getPropertyList("t.list").add("c");
			assertEquals(2, sm.getPropertyList("t.list").size(), "Policy must be unchanged.");
		}

		@Test
		@DisplayName("5. An empty policy answers every read without throwing")
		void emptyPolicyAnswers() {
			SecurityManagerImpl sm = new EmptySecMan();
			assertFalse(sm.getPropertyBoolean("anything", false));
			assertEquals(7L, sm.getPropertyInt("anything", 7));
			assertEquals(1.5, sm.getPropertyDouble("anything", 1.5), 0.0001);
			assertEquals("d", sm.getPropertyString("anything", "d"));
			assertNull(sm.getPropertyList("anything"));
			assertNull(sm.getPropertyMap("anything"));
		}
	}

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
		@DisplayName("6. A script cannot turn a gate into an internal error by deleting "
			+ "the property")
		void deletedPropertyStillDenies() throws Exception {
			// SecurityManagerImpl.putOrRemove drops a key when the value is
			// null, and setProp is script-facing, so a host that allows
			// policy writes lets a tenant delete a permission. Every gate
			// used to unbox that null and throw. This is not an escalation:
			// a script that can write policy can already disarm it. What it
			// must not do is turn a permission check into an opaque NPE.
			// os.info.view is false in TestSecurityManagerImpl, so grant it
			// here: the point of the test is what happens when a granted
			// property is deleted.
			LimitSecMan sm = new LimitSecMan()
				.with("securitymanager.property.set", Boolean.TRUE)
				.with("os.info.view", Boolean.TRUE);
			Engine eng = new Engine(sm);
			eng.setScriptMode(true);
			eng.evalLine("include sys;");

			// TestSecurityManagerImpl allows this view, so the read works.
			AussomType before = eng.evalLine("n = sys.getOsName();");
			assertFalse(before.isEx(), "Should read the OS name to start, got: " + str(before));
			assertFalse(before.isNull(), "Should have returned a name, got: " + str(before));

			eng.evalLine("secman.setProp('os.info.view', null);");
			assertNull(eng.getSecurityManager().getProperty("os.info.view"),
				"The script should have removed the property.");

			// ASys denies a view by returning null rather than raising, so
			// the deleted property must now read exactly like a false one.
			// Before the sweep this line raised
			// EXTERN_INVOCATION_TARGET_EXCEPTION wrapping a
			// NullPointerException.
			AussomType after = eng.evalLine("n = sys.getOsName();");
			assertFalse(after.isEx(),
				"A missing property must deny, not throw, got: " + str(after));
			assertTrue(after.isNull(), "Denied reads answer null, got: " + str(after));

			// And an explicitly false property behaves identically, which is
			// the point: absent and false are the same answer.
			eng.evalLine("secman.setProp('os.info.view', false);");
			AussomType explicit = eng.evalLine("n = sys.getOsName();");
			assertTrue(explicit.isNull(), "Explicit false should match, got: " + str(explicit));
		}

		@Test
		@DisplayName("7. An engine builds and denies cleanly when policy holds nothing")
		void emptyPolicyDeniesCleanly() throws Exception {
			// The construction failure F7 records: the first property read
			// happens while parsing lang.aus, so an unpopulated policy used
			// to take the constructor down with an NPE.
			Engine eng = new Engine(new EmptySecMan());
			assertFalse(eng.hasParseErrors(), "lang.aus should still load.");

			eng.parseString("t.aus", "include sys;\nclass Main { public Main() {} "
				+ "public main() { try { c.log(sys.getOsName()); } "
				+ "catch (e) { c.log(\"denied: \" + e.getText()); } return 0; } }");
			assertEquals(0, eng.run(), "The program should run and handle the denial.");
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

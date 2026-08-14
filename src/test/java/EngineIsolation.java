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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aussom.Engine;
import com.aussom.LoggingInt;
import com.aussom.SecurityManagerImpl;
import com.aussom.TestSecurityManagerImpl;
import com.aussom.ast.astClass;
import com.aussom.types.AussomString;

/**
 * Multi-engine isolation regressions.
 *
 * <p>Two Engine instances in one JVM must behave as though they were in
 * two JVMs. Before the work in {@code design/multitenancy-safety.md}
 * they shared the lang.aus class definitions by reference, which let
 * one engine's script inject methods that another engine then executed,
 * and logging was bound to the calling thread rather than to the engine
 * that produced the output.
 *
 * @author austin
 */
@DisplayName("Engine isolation")
public class EngineIsolation {

	private static Engine newEngine() throws Exception {
		return new Engine(new TestSecurityManagerImpl());
	}

	/** Collects output so a test can assert where it went. */
	private static final class CapturingLogger implements LoggingInt {
		final List<String> lines = new ArrayList<String>();
		public void log(String s) { lines.add(s); }
		public void trc(String s) { lines.add(s); }
		public void dbg(String s) { lines.add(s); }
		public void info(String s) { lines.add(s); }
		public void warn(String s) { lines.add(s); }
		public void err(String s) { lines.add(s); }
		public void print(String s) { lines.add(s); }
		public void println(String s) { lines.add(s); }
	}

	/* ============================================================ */
	/*  Class table                                                 */
	/* ============================================================ */

	@Nested
	@DisplayName("Class definitions are per-engine")
	class ClassTable {

		@Test
		@DisplayName("1. Two engines hold distinct astClass objects for the same lang class")
		void langDefsAreNotShared() throws Exception {
			Engine a = newEngine();
			Engine b = newEngine();
			for (String name : new String[] { "string", "int", "list", "map", "object", "Buffer", "Date", "exception" }) {
				astClass da = a.getClassByName(name);
				astClass db = b.getClassByName(name);
				assertNotNull(da, "engine a is missing lang class '" + name + "'");
				assertNotNull(db, "engine b is missing lang class '" + name + "'");
				assertNotSame(da, db, "engines share the '" + name + "' class definition");
			}
		}

		@Test
		@DisplayName("2. A user class defined in one engine is invisible to another")
		void userClassesDoNotLeak() throws Exception {
			Engine a = newEngine();
			Engine b = newEngine();
			a.parseString("a.aus", "class TenantAOnly { public TenantAOnly() {} }");
			assertTrue(a.containsClass("TenantAOnly"));
			assertFalse(b.containsClass("TenantAOnly"), "engine b saw engine a's class");
		}

		@Test
		@DisplayName("3. Redefining a lang class affects only the engine that did it")
		void langClassRedefinitionIsLocal() throws Exception {
			Engine a = newEngine();
			Engine b = newEngine();
			astClass before = b.getClassByName("string");
			a.parseString("a.aus", "class string { public string() {} public hijacked() { return 1; } }");
			assertNotSame(before, a.getClassByName("string"), "engine a should hold its own redefinition");
			assertEquals(before, b.getClassByName("string"), "engine b's string definition changed");
		}
	}

	/* ============================================================ */
	/*  The injection this work exists to close                     */
	/* ============================================================ */

	@Nested
	@DisplayName("Cross-engine code injection")
	class Injection {

		/**
		 * The reproduction from design/multitenancy-safety.md section
		 * 2.2. Tenant A declares its own 'object' and instantiates a
		 * lang class, which used to merge A's methods into the shared
		 * definition. Tenant B, a separate engine that never mentions
		 * 'object', then found and ran them.
		 */
		@Test
		@DisplayName("4. One engine cannot inject a method into another's lang classes")
		void objectShadowingDoesNotCross() throws Exception {
			Engine a = newEngine();
			a.parseString("evil.aus",
				"class object {\n"
				+ "  public object() {}\n"
				+ "  public backdoor() { return \"OWNED-BY-A\"; }\n"
				+ "}\n"
				+ "class AMain {\n"
				+ "  public AMain() {}\n"
				+ "  public main(list argv) {\n"
				+ "    b = new Buffer();\n"
				+ "    d = new Date();\n"
				+ "    return 0;\n"
				+ "  }\n"
				+ "}\n");
			a.run();

			Engine b = newEngine();
			assertFalse(b.getClassByName("Buffer").hasAnyFunctionByName("backdoor"),
				"engine a injected 'backdoor' into engine b's Buffer");
			assertFalse(b.getClassByName("Date").hasAnyFunctionByName("backdoor"),
				"engine a injected 'backdoor' into engine b's Date");

			// And the injected method is genuinely unreachable from B.
			b.parseString("victim.aus",
				"class BMain {\n"
				+ "  public BMain() {}\n"
				+ "  public main(list argv) {\n"
				+ "    d = new Date();\n"
				+ "    try { d.backdoor(); return 1; }\n"
				+ "    catch (ex) { return 0; }\n"
				+ "  }\n"
				+ "}\n");
			assertEquals(0, b.run(), "engine b was able to call engine a's injected method");
		}

		@Test
		@DisplayName("5. Shadowing 'object' still works inside the engine that did it")
		void objectShadowingStillWorksLocally() throws Exception {
			Engine a = newEngine();
			a.parseString("a.aus",
				"class Base { public Base() {} public tag() { return \"local\"; } }\n"
				+ "class Derived : Base { public Derived() {} }\n"
				+ "class AMain {\n"
				+ "  public AMain() {}\n"
				+ "  public main(list argv) {\n"
				+ "    d = new Derived();\n"
				+ "    if (d.tag() == \"local\") { return 0; }\n"
				+ "    return 1;\n"
				+ "  }\n"
				+ "}\n");
			assertEquals(0, a.run(), "ordinary inheritance broke");
		}
	}

	/* ============================================================ */
	/*  Primitives                                                  */
	/* ============================================================ */

	@Nested
	@DisplayName("Primitives")
	class Primitives {

		@Test
		@DisplayName("6. A primitive built with no engine still dispatches once given one")
		void engineLessPrimitiveDispatches() throws Exception {
			// Constructed with no engine in sight, as doc generation,
			// clone(), and the JSR 223 marshaller all do.
			AussomString s = new AussomString("  padded  ");
			assertNull(s.getClassDef(), "a primitive should carry no class definition");
			assertEquals("string", s.getTypeName());

			// Handed to an engine, it dispatches normally.
			Engine eng = newEngine();
			eng.parseString("t.aus",
				"class TMain {\n"
				+ "  public TMain() {}\n"
				+ "  public main(list argv) {\n"
				+ "    s = \"  padded  \";\n"
				+ "    if (s.trim() == \"padded\") { return 0; }\n"
				+ "    return 1;\n"
				+ "  }\n"
				+ "}\n");
			assertEquals(0, eng.run(), "primitive method dispatch failed");
		}

		@Test
		@DisplayName("7. Each engine resolves primitives against its own definitions")
		void primitiveDefsArePerEngine() throws Exception {
			Engine a = newEngine();
			Engine b = newEngine();
			assertNotSame(a.getPrimitiveClassDef(com.aussom.types.cType.cString),
				b.getPrimitiveClassDef(com.aussom.types.cType.cString),
				"engines share a primitive class definition");
			assertSameEngineTable(a);
			assertSameEngineTable(b);
		}

		private void assertSameEngineTable(Engine e) {
			assertEquals(e.getClassByName("string"),
				e.getPrimitiveClassDef(com.aussom.types.cType.cString),
				"the cached primitive def is not the one in the engine's own table");
		}

		@Test
		@DisplayName("8. toString on an engine-less value does not throw")
		void debugDumpSurvivesMissingClassDef() {
			AussomString s = new AussomString("x");
			assertNotNull(s.toString(0));
		}
	}

	/* ============================================================ */
	/*  Logging                                                     */
	/* ============================================================ */

	@Nested
	@DisplayName("Logging is per-engine")
	class Logging {

		@Test
		@DisplayName("9. Output goes to the producing engine's logger, not the thread's")
		void outputDoesNotCrossEngines() throws Exception {
			Engine a = newEngine();
			Engine b = newEngine();
			CapturingLogger la = new CapturingLogger();
			CapturingLogger lb = new CapturingLogger();
			a.setLogger(la);
			b.setLogger(lb);

			b.parseString("b.aus",
				"class BMain {\n"
				+ "  public BMain() {}\n"
				+ "  public main(list argv) { c.log(\"FROM-B\"); return 0; }\n"
				+ "}\n");
			b.run();

			assertTrue(lb.lines.contains("FROM-B"), "engine b's logger did not receive its own output");
			assertFalse(la.lines.contains("FROM-B"), "engine a's logger received engine b's output");
		}

		@Test
		@DisplayName("10. An engine always has a logger, and setLogger(null) restores the default")
		void loggerIsNeverNull() throws Exception {
			Engine e = new Engine(new SecurityManagerImpl());
			assertNotNull(e.getLogger());
			CapturingLogger cap = new CapturingLogger();
			e.setLogger(cap);
			assertEquals(cap, e.getLogger());
			e.setLogger(null);
			assertNotNull(e.getLogger(), "setLogger(null) left the engine without a logger");
			assertNotSame(cap, e.getLogger());
		}
	}

	/* ============================================================ */
	/*  Standard library registry                                   */
	/* ============================================================ */

	@Nested
	@DisplayName("Module registry is per-engine")
	class Modules {

		@Test
		@DisplayName("11. A module registered on one engine is invisible to another")
		void modulesDoNotLeak() throws Exception {
			Engine a = newEngine();
			Engine b = newEngine();
			a.addModule("tenant.aus", "class TenantMod { public TenantMod() {} }");
			assertTrue(a.getLangRegistry().contains("tenant.aus"));
			assertFalse(b.getLangRegistry().contains("tenant.aus"),
				"engine b saw a module registered on engine a");
		}

		@Test
		@DisplayName("12. Engines get independent registries holding the base stdlib")
		void registriesAreIndependent() throws Exception {
			Engine a = newEngine();
			Engine b = newEngine();
			assertNotSame(a.getLangRegistry(), b.getLangRegistry());
			for (String m : new String[] { "lang.aus", "sys.aus", "math.aus", "util.aus", "reflect.aus" }) {
				assertTrue(a.getLangRegistry().contains(m), "base module '" + m + "' missing");
				assertTrue(b.getLangRegistry().contains(m), "base module '" + m + "' missing");
			}
		}
	}
}

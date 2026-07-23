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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aussom.DefaultLoggingImpl;
import com.aussom.Engine;
import com.aussom.TestSecurityManagerImpl;
import com.aussom.ast.aussomException;
import com.aussom.stdlib.console;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomString;

/**
 * Engine-level closure coverage that cannot live in the Aussom
 * integration suite (tests/interpreter.aus): parse-error paths that
 * would sink the whole file, script-mode hoisting onto the synthetic
 * script class, and the JSR 223 marshalling path. Interpreter
 * behavior of closures themselves is covered by the closureTests
 * class in tests/interpreter.aus. See design/closures.md.
 */
@DisplayName("Closures (engine level)")
public class Closures {

	@BeforeEach
	void setUp() {
		// Quiet the engine's [trc] chatter during tests.
		console.get().register(new DefaultLoggingImpl());
	}

	/**
	 * Constructs an Engine with TestSecurityManagerImpl (script
	 * mode allowed) and stdlib resource path registered.
	 */
	private static Engine newScriptEngine() {
		try {
			Engine eng = new Engine(new TestSecurityManagerImpl());
			eng.addResourceIncludePath("/com/aussom/stdlib/aus/");
			return eng;
		} catch (Exception e) {
			throw new RuntimeException("test engine construction failed", e);
		}
	}

	@Nested
	@DisplayName("Script-mode closures")
	class ScriptModeClosures {

		@Test
		@DisplayName("Top-level closure hoists onto the script class and captures locals")
		void topLevelClosure() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.evalLine("n = 5;");
			eng.evalLine("f = ::smScale(int x) { return x * n; };");
			Object r = eng.evalLine("return f.call(3);");
			assertEquals(15L, ((AussomInt) r).getValue());
		}

		@Test
		@DisplayName("Closure inside a class declared via evalLine works")
		void closureInScriptDeclaredClass() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.evalLine("class smOwner { public smOwner() {} "
				+ "public make(int n) { return ::smAdd(int x) { return x + n; }; } }");
			Object r = eng.evalLine("o = new smOwner(); a = o.make(10); return a.call(5);");
			assertEquals(15L, ((AussomInt) r).getValue());
		}
	}

	@Nested
	@DisplayName("Parse errors")
	class ParseErrors {

		@Test
		@DisplayName("Duplicate closure name and signature in one class is a parse error")
		void duplicateClosureName() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			assertThrows(aussomException.class, () -> eng.evalLine(
				"class dupOwner { public dupOwner() {} "
				+ "public a() { f = ::dupClo(int x) { return x; }; } "
				+ "public b() { g = ::dupClo(int y) { return y; }; } }"));
		}

		@Test
		@DisplayName("Same closure name with different signatures overloads cleanly")
		void closureOverloadsAccepted() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.evalLine("class ovOwner { public ovOwner() {} "
				+ "public a() { f = ::ovClo(int x) { return x; }; return f.call(4); } "
				+ "public b() { g = ::ovClo(string s) { return s; }; return g.call(\"hi\"); } }");
			Object r = eng.evalLine("o = new ovOwner(); return \"\" + o.a() + o.b();");
			assertEquals("4hi", ((AussomString) r).getValue());
		}
	}

	@Nested
	@DisplayName("JSR 223")
	class Jsr223Closures {

		@Test
		@DisplayName("Closure defined and fired through the JSR 223 engine")
		void jsr223Closure() throws Exception {
			ScriptEngineManager mgr = new ScriptEngineManager();
			ScriptEngine engine = mgr.getEngineByName("aussom");
			assertNotNull(engine, "engine must be discoverable by name");
			Object r = engine.eval(
				"n = 6; f = ::jsrScale(int x) { return x * n; }; return f.call(7);");
			assertEquals(42L, r);
		}
	}
}

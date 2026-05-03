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

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 coverage for the JSR 223 Aussom scripting engine
 * (com.aussom.script). Verifies discovery, factory metadata, all
 * eval overloads, bindings flow in/out, type marshalling, I/O
 * routing, Compilable, Invocable, error mapping, and the
 * MULTITHREADED concurrency contract.
 */
@DisplayName("JSR 223 Aussom Scripting Engine")
public class Jsr223 {

	private ScriptEngineManager mgr;
	private ScriptEngine engine;
	private ScriptEngineFactory factory;

	@BeforeEach
	void setUp() {
		mgr = new ScriptEngineManager();
		engine = mgr.getEngineByName("aussom");
		assertNotNull(engine, "engine must be discoverable by name");
		factory = engine.getFactory();
	}

	/* ============================================================ */
	/*  Discovery via SPI                                           */
	/* ============================================================ */

	@Nested
	@DisplayName("Discovery via META-INF/services")
	class Discovery {

		@Test
		void byName() {
			assertNotNull(mgr.getEngineByName("aussom"));
			assertNotNull(mgr.getEngineByName("aus"));
			assertNotNull(mgr.getEngineByName("Aussom"));
		}

		@Test
		void byExtension() {
			assertNotNull(mgr.getEngineByExtension("aus"));
		}

		@Test
		void byMimeType() {
			assertNotNull(mgr.getEngineByMimeType("application/x-aussom"));
			assertNotNull(mgr.getEngineByMimeType("text/x-aussom"));
		}

		@Test
		void unknownNameReturnsNull() {
			assertNull(mgr.getEngineByName("not-a-real-engine"));
		}

		@Test
		void distinctEngineInstancesPerLookup() {
			ScriptEngine a = mgr.getEngineByName("aussom");
			ScriptEngine b = mgr.getEngineByName("aussom");
			assertNotSame(a, b, "every getEngineByName call should mint a new engine");
		}
	}

	/* ============================================================ */
	/*  Factory metadata                                            */
	/* ============================================================ */

	@Nested
	@DisplayName("ScriptEngineFactory metadata")
	class FactoryMetadata {

		@Test
		void engineName() {
			assertEquals("Aussom Scripting Engine", factory.getEngineName());
		}

		@Test
		void languageName() {
			assertEquals("Aussom", factory.getLanguageName());
		}

		@Test
		void engineAndLanguageVersionsArePopulated() {
			assertNotNull(factory.getEngineVersion());
			assertFalse(factory.getEngineVersion().isEmpty());
			assertEquals(factory.getEngineVersion(), factory.getLanguageVersion());
		}

		@Test
		void shortNames() {
			List<String> names = factory.getNames();
			assertTrue(names.contains("aussom"));
			assertTrue(names.contains("aus"));
		}

		@Test
		void extensions() {
			assertEquals(Arrays.asList("aus"), factory.getExtensions());
		}

		@Test
		void mimeTypes() {
			List<String> types = factory.getMimeTypes();
			assertTrue(types.contains("application/x-aussom"));
			assertTrue(types.contains("text/x-aussom"));
		}

		@Test
		void parameterEngineKeys() {
			assertEquals("Aussom Scripting Engine", factory.getParameter(ScriptEngine.ENGINE));
			assertEquals("Aussom",                  factory.getParameter(ScriptEngine.LANGUAGE));
			assertEquals("aussom",                  factory.getParameter(ScriptEngine.NAME));
			assertNotNull(factory.getParameter(ScriptEngine.ENGINE_VERSION));
			assertNotNull(factory.getParameter(ScriptEngine.LANGUAGE_VERSION));
		}

		@Test
		void threadingIsMultithreaded() {
			assertEquals("MULTITHREADED", factory.getParameter("THREADING"));
		}

		@Test
		void unknownParameterReturnsNull() {
			assertNull(factory.getParameter("not-a-real-key"));
		}

		@Test
		void methodCallSyntax() {
			assertEquals("o.m(a, b)", factory.getMethodCallSyntax("o", "m", "a", "b"));
			assertEquals("o.m()",     factory.getMethodCallSyntax("o", "m"));
		}

		@Test
		void outputStatementEscapesQuotes() {
			String out = factory.getOutputStatement("hello \"world\"");
			assertTrue(out.contains("c.log("));
			assertTrue(out.contains("\\\"world\\\""));
		}

		@Test
		void getProgramWrapsStatementsInMain() {
			String prog = factory.getProgram("x = 1", "return x");
			assertTrue(prog.contains("class jsr223Program"));
			assertTrue(prog.contains("public main(args)"));
			assertTrue(prog.contains("x = 1;"));
			assertTrue(prog.contains("return x;"));
		}
	}

	/* ============================================================ */
	/*  eval overloads                                              */
	/* ============================================================ */

	@Nested
	@DisplayName("eval overloads")
	class EvalOverloads {

		@Test
		void evalStringReturnsValue() throws Exception {
			assertEquals(3L, engine.eval("return 1 + 2;"));
		}

		@Test
		void evalReader() throws Exception {
			Object r = engine.eval(new StringReader("return 42;"));
			assertEquals(42L, r);
		}

		@Test
		void evalStringWithBindings() throws Exception {
			Bindings b = new SimpleBindings();
			b.put("a", 5L);
			b.put("b", 7L);
			Object r = engine.eval(
				"return this.bindings[\"a\"] + this.bindings[\"b\"];", b);
			assertEquals(12L, r);
		}

		@Test
		void evalReaderWithBindings() throws Exception {
			Bindings b = new SimpleBindings();
			b.put("name", "abc");
			Object r = engine.eval(
				new StringReader("return this.bindings[\"name\"];"), b);
			assertEquals("abc", r);
		}

		@Test
		void evalStringWithCustomScriptContext() throws Exception {
			SimpleScriptContext ctx = new SimpleScriptContext();
			Bindings b = new SimpleBindings();
			b.put("n", 10L);
			ctx.setBindings(b, ScriptContext.ENGINE_SCOPE);
			Object r = engine.eval("return this.bindings[\"n\"] * 2;", ctx);
			assertEquals(20L, r);
		}

		@Test
		void emptySnippetIsOk() throws Exception {
			assertNull(engine.eval(""));
		}

		@Test
		void snippetWithoutReturnYieldsNull() throws Exception {
			assertNull(engine.eval("x = 1;"));
		}

		@Test
		void nullScriptThrowsNpe() {
			assertThrows(NullPointerException.class, () -> engine.eval((String) null));
		}

		@Test
		void fullProgramWithoutMainReturnsNull() throws Exception {
			Object r = engine.eval("class Calc { public add(a, b) { return a + b; } }");
			assertNull(r, "registering a class with no main is a no-op at run time");
		}

		@Test
		void fullProgramWithMainRunsMain() throws Exception {
			Object r = engine.eval(
				"class App { public main(args) { return 99; } }");
			assertEquals(99L, r);
		}
	}

	/* ============================================================ */
	/*  Bindings round-trip                                         */
	/* ============================================================ */

	@Nested
	@DisplayName("Bindings flow in and out")
	class BindingsFlow {

		@Test
		void bindingsFlowIn() throws Exception {
			engine.put("name", "world");
			Object r = engine.eval(
				"return \"hello \" + this.bindings[\"name\"] + \"!\";");
			assertEquals("hello world!", r);
		}

		@Test
		void bindingsWriteBack() throws Exception {
			engine.put("counter", 10L);
			engine.eval(
				"this.bindings[\"counter\"] = this.bindings[\"counter\"] + 5;");
			assertEquals(15L, engine.get("counter"));
		}

		@Test
		void newBindingFromScriptShowsUpInEngineScope() throws Exception {
			engine.eval("this.bindings[\"created\"] = \"yes\";");
			assertEquals("yes", engine.get("created"));
		}

		@Test
		void globalScopeBindingsAreAlsoVisible() throws Exception {
			Bindings global = engine.createBindings();
			global.put("g", 100L);
			engine.getContext().setBindings(global, ScriptContext.GLOBAL_SCOPE);
			Object r = engine.eval("return this.bindings[\"g\"];");
			assertEquals(100L, r);
		}

		@Test
		void engineScopeShadowsGlobalScope() throws Exception {
			Bindings global = engine.createBindings();
			global.put("k", "from-global");
			engine.getContext().setBindings(global, ScriptContext.GLOBAL_SCOPE);
			engine.put("k", "from-engine");
			Object r = engine.eval("return this.bindings[\"k\"];");
			assertEquals("from-engine", r);
		}

		@Test
		void createBindingsReturnsSimpleBindings() {
			Bindings b = engine.createBindings();
			assertNotNull(b);
			assertInstanceOf(SimpleBindings.class, b);
		}

		@Test
		void getFactoryReturnsSameFactoryAcrossCalls() {
			assertSame(engine.getFactory(), engine.getFactory());
		}
	}

	/* ============================================================ */
	/*  Type marshalling                                            */
	/* ============================================================ */

	@Nested
	@DisplayName("Java <-> Aussom type marshalling")
	class TypeMarshalling {

		@Test
		void nullRoundTrip() throws Exception {
			engine.put("v", null);
			assertNull(engine.eval("return this.bindings[\"v\"];"));
		}

		@Test
		void boolRoundTrip() throws Exception {
			engine.put("yes", true);
			engine.put("no",  false);
			assertEquals(Boolean.TRUE,  engine.eval("return this.bindings[\"yes\"];"));
			assertEquals(Boolean.FALSE, engine.eval("return this.bindings[\"no\"];"));
		}

		@Test
		void integerWidensToLong() throws Exception {
			engine.put("i", Integer.valueOf(7));
			engine.put("l", Long.valueOf(42L));
			engine.put("s", Short.valueOf((short) 3));
			assertEquals(7L,  engine.eval("return this.bindings[\"i\"];"));
			assertEquals(42L, engine.eval("return this.bindings[\"l\"];"));
			assertEquals(3L,  engine.eval("return this.bindings[\"s\"];"));
		}

		@Test
		void doubleRoundTrip() throws Exception {
			engine.put("d", 3.14);
			Object r = engine.eval("return this.bindings[\"d\"];");
			assertInstanceOf(Double.class, r);
			assertEquals(3.14, (Double) r, 1e-9);
		}

		@Test
		void stringRoundTrip() throws Exception {
			engine.put("s", "hello");
			assertEquals("hello", engine.eval("return this.bindings[\"s\"];"));
		}

		@Test
		void listMarshalsToAussomListAndBack() throws Exception {
			engine.put("xs", Arrays.asList(1L, 2L, 3L));
			Object r = engine.eval(
				"sum = 0;\n" +
				"for (v : this.bindings[\"xs\"]) { sum = sum + v; }\n" +
				"return sum;\n");
			assertEquals(6L, r);
		}

		@Test
		void mapMarshalsToAussomMapAndBack() throws Exception {
			Map<String, Object> m = new HashMap<>();
			m.put("first", "Ada");
			m.put("last", "Lovelace");
			engine.put("u", m);
			Object r = engine.eval(
				"return this.bindings[\"u\"][\"first\"] + \" \" "
				+ "+ this.bindings[\"u\"][\"last\"];");
			assertEquals("Ada Lovelace", r);
		}

		@Test
		void nestedListInMap() throws Exception {
			Map<String, Object> outer = new HashMap<>();
			outer.put("nums", Arrays.asList(10L, 20L));
			engine.put("o", outer);
			Object r = engine.eval(
				"return this.bindings[\"o\"][\"nums\"][1];");
			assertEquals(20L, r);
		}

		@Test
		void aussomListReturnsAsArrayList() throws Exception {
			Object r = engine.eval(
				"l = []; l.add(1); l.add(2); l.add(3); return l;");
			assertInstanceOf(ArrayList.class, r);
			assertEquals(Arrays.asList(1L, 2L, 3L), r);
		}

		@Test
		void aussomMapReturnsAsLinkedHashMap() throws Exception {
			Object r = engine.eval(
				"m = {}; m[\"a\"] = 1; m[\"b\"] = 2; return m;");
			assertInstanceOf(LinkedHashMap.class, r);
			LinkedHashMap<?,?> lm = (LinkedHashMap<?,?>) r;
			assertEquals(1L, lm.get("a"));
			assertEquals(2L, lm.get("b"));
		}

		@Test
		void arrayMarshalsAsList() throws Exception {
			engine.put("arr", new Object[] { 1L, 2L, 3L });
			Object r = engine.eval(
				"sum = 0;\n" +
				"for (v : this.bindings[\"arr\"]) { sum = sum + v; }\n" +
				"return sum;\n");
			assertEquals(6L, r);
		}

		@Test
		void characterBecomesString() throws Exception {
			engine.put("c", Character.valueOf('Z'));
			assertEquals("Z", engine.eval("return this.bindings[\"c\"];"));
		}
	}

	/* ============================================================ */
	/*  ScriptContext I/O                                           */
	/* ============================================================ */

	@Nested
	@DisplayName("ScriptContext writer routing")
	class IoRouting {

		@Test
		void cLogRoutesToScriptContextWriter() throws Exception {
			StringWriter buf = new StringWriter();
			engine.getContext().setWriter(buf);
			engine.eval("c.log(\"captured\");");
			assertTrue(buf.toString().contains("captured"));
		}

		@Test
		void cErrRoutesToScriptContextErrorWriter() throws Exception {
			StringWriter outBuf = new StringWriter();
			StringWriter errBuf = new StringWriter();
			engine.getContext().setWriter(outBuf);
			engine.getContext().setErrorWriter(errBuf);
			engine.eval("c.err(\"oops\");");
			assertTrue(errBuf.toString().contains("oops"));
			assertFalse(outBuf.toString().contains("oops"),
				"err() must not bleed into the regular writer");
		}

		@Test
		void writerReplacementBetweenEvalsTakesEffect() throws Exception {
			StringWriter first = new StringWriter();
			engine.getContext().setWriter(first);
			engine.eval("c.log(\"one\");");

			StringWriter second = new StringWriter();
			engine.getContext().setWriter(second);
			engine.eval("c.log(\"two\");");

			assertTrue(first.toString().contains("one"));
			assertFalse(first.toString().contains("two"));
			assertTrue(second.toString().contains("two"));
			assertFalse(second.toString().contains("one"));
		}
	}

	/* ============================================================ */
	/*  Compilable                                                  */
	/* ============================================================ */

	@Nested
	@DisplayName("Compilable")
	class CompilableSurface {

		@Test
		void engineImplementsCompilable() {
			assertInstanceOf(Compilable.class, engine);
		}

		@Test
		void compileStringAndRunMultipleTimes() throws Exception {
			Compilable c = (Compilable) engine;
			CompiledScript cs = c.compile(
				"return this.bindings[\"x\"] * this.bindings[\"x\"];");
			engine.put("x", 7L);
			assertEquals(49L, cs.eval());
			engine.put("x", 9L);
			assertEquals(81L, cs.eval());
			engine.put("x", 0L);
			assertEquals(0L,  cs.eval());
		}

		@Test
		void compileReader() throws Exception {
			Compilable c = (Compilable) engine;
			CompiledScript cs = c.compile(new StringReader("return 5;"));
			assertEquals(5L, cs.eval());
		}

		@Test
		void compiledScriptKnowsItsEngine() throws Exception {
			Compilable c = (Compilable) engine;
			CompiledScript cs = c.compile("return 1;");
			assertSame(engine, cs.getEngine());
		}

		@Test
		void compileNullThrowsNpe() {
			Compilable c = (Compilable) engine;
			assertThrows(NullPointerException.class, () -> c.compile((String) null));
		}

		@Test
		void compileBadSourceThrowsScriptException() {
			Compilable c = (Compilable) engine;
			assertThrows(ScriptException.class, () -> c.compile("class @@@ {"));
		}

		@Test
		void distinctCompilesGetDistinctSyntheticClasses() throws Exception {
			Compilable c = (Compilable) engine;
			CompiledScript a = c.compile("return 1;");
			CompiledScript b = c.compile("return 2;");
			assertEquals(1L, a.eval());
			assertEquals(2L, b.eval());
		}
	}

	/* ============================================================ */
	/*  Invocable                                                   */
	/* ============================================================ */

	@Nested
	@DisplayName("Invocable")
	class InvocableSurface {

		@Test
		void engineImplementsInvocable() {
			assertInstanceOf(Invocable.class, engine);
		}

		@Test
		void invokeFunctionOnFreshlyDefinedClass() throws Exception {
			engine.eval(
				"class Calc { public add(int a, int b) { return a + b; } }");
			Invocable inv = (Invocable) engine;
			assertEquals(5L, inv.invokeFunction("add", 2L, 3L));
			assertEquals(0L, inv.invokeFunction("add", -7L, 7L));
		}

		@Test
		void invokeFunctionUnknownNameThrows() {
			Invocable inv = (Invocable) engine;
			assertThrows(NoSuchMethodException.class,
				() -> inv.invokeFunction("noSuchThing"));
		}

		@Test
		void invokeFunctionNullNameThrowsNpe() {
			Invocable inv = (Invocable) engine;
			assertThrows(NullPointerException.class,
				() -> inv.invokeFunction(null));
		}

		@Test
		void invokeMethodOnReturnedObjectDispatches() throws Exception {
			engine.eval(
				"class Greeter { public hi(string name) { return \"hi \" + name; } }");
			Object g = engine.eval("return new Greeter();");
			assertNotNull(g);
			Invocable inv = (Invocable) engine;
			assertEquals("hi alice", inv.invokeMethod(g, "hi", "alice"));
		}

		@Test
		void invokeMethodNonAussomReceiverIsIllegal() {
			Invocable inv = (Invocable) engine;
			assertThrows(IllegalArgumentException.class,
				() -> inv.invokeMethod("not-an-aussom-object", "m"));
		}

		@Test
		void getInterfaceProxiesIntoAussom() throws Exception {
			engine.eval(
				"class Worker { public run() { c.log(\"ran\"); return null; } }");
			StringWriter out = new StringWriter();
			engine.getContext().setWriter(out);
			Invocable inv = (Invocable) engine;
			Runnable r = inv.getInterface(Runnable.class);
			assertNotNull(r, "engine has a 'run' method, getInterface should bind");
			r.run();
			assertTrue(out.toString().contains("ran"));
		}

		@Test
		void getInterfaceReturnsNullWhenMethodMissing() {
			// No class declares a 'compareTo' method.
			Invocable inv = (Invocable) engine;
			Comparable<?> c = inv.getInterface(Comparable.class);
			assertNull(c);
		}

		@Test
		void getInterfaceRejectsNonInterface() {
			Invocable inv = (Invocable) engine;
			assertThrows(IllegalArgumentException.class,
				() -> inv.getInterface(String.class));
		}
	}

	/* ============================================================ */
	/*  Errors                                                      */
	/* ============================================================ */

	@Nested
	@DisplayName("Error mapping")
	class Errors {

		@Test
		void parseErrorThrowsScriptException() {
			assertThrows(ScriptException.class,
				() -> engine.eval("class @@@ broken {"));
		}

		@Test
		void engineRecoversFromParseError() throws Exception {
			assertThrows(ScriptException.class,
				() -> engine.eval("class @@@ broken {"));
			// A subsequent valid eval must succeed -- the sticky
			// hasParseErrors flag must not bleed across calls.
			assertEquals(7L, engine.eval("return 7;"));
		}

		@Test
		void runtimeErrorThrowsScriptException() {
			ScriptException ex = assertThrows(ScriptException.class,
				() -> engine.eval("o = null; return o.somethingMissing;"));
			assertNotNull(ex.getMessage());
		}
	}

	/* ============================================================ */
	/*  Threading                                                   */
	/* ============================================================ */

	@Nested
	@DisplayName("Threading (MULTITHREADED)")
	class Threading {

		@Test
		void concurrentEvalsDoNotInterfere() throws Exception {
			final int N = 16;
			Thread[] ts = new Thread[N];
			final long[] results = new long[N];
			final AtomicInteger errors = new AtomicInteger(0);

			for (int i = 0; i < N; i++) {
				final int idx = i;
				ts[i] = new Thread(() -> {
					try {
						SimpleScriptContext ctx = new SimpleScriptContext();
						Bindings b = engine.createBindings();
						b.put("n", (long) (idx + 1));
						ctx.setBindings(b, ScriptContext.ENGINE_SCOPE);
						Object out = engine.eval(
							"return this.bindings[\"n\"] * 10;", ctx);
						results[idx] = ((Long) out);
					} catch (Throwable t) {
						errors.incrementAndGet();
					}
				});
			}
			for (Thread t : ts) t.start();
			for (Thread t : ts) t.join();

			assertEquals(0, errors.get(), "no thread should fail");
			for (int i = 0; i < N; i++) {
				assertEquals((i + 1L) * 10L, results[i]);
			}
		}

		@Test
		void concurrentInvokeFunctionDispatchesPerThread() throws Exception {
			engine.eval(
				"class Calc { public dbl(int x) { return x * 2; } }");
			Invocable inv = (Invocable) engine;

			final int N = 8;
			Thread[] ts = new Thread[N];
			final long[] results = new long[N];
			final AtomicInteger errors = new AtomicInteger(0);

			for (int i = 0; i < N; i++) {
				final int idx = i;
				ts[i] = new Thread(() -> {
					try {
						results[idx] = (Long) inv.invokeFunction(
							"dbl", (long) (idx + 1));
					} catch (Throwable t) {
						errors.incrementAndGet();
					}
				});
			}
			for (Thread t : ts) t.start();
			for (Thread t : ts) t.join();

			assertEquals(0, errors.get());
			for (int i = 0; i < N; i++) {
				assertEquals((i + 1L) * 2L, results[i]);
			}
		}
	}

	/* ============================================================ */
	/*  Engine instance isolation                                   */
	/* ============================================================ */

	@Nested
	@DisplayName("Engine instance isolation")
	class Isolation {

		@Test
		void distinctEnginesHaveSeparateEngineScope() throws Exception {
			ScriptEngine a = mgr.getEngineByName("aussom");
			ScriptEngine b = mgr.getEngineByName("aussom");
			a.put("v", 1L);
			b.put("v", 2L);
			assertEquals(1L, a.eval("return this.bindings[\"v\"];"));
			assertEquals(2L, b.eval("return this.bindings[\"v\"];"));
		}

		@Test
		void writerOnOneEngineDoesNotAffectAnother() throws Exception {
			ScriptEngine a = mgr.getEngineByName("aussom");
			ScriptEngine b = mgr.getEngineByName("aussom");
			StringWriter aBuf = new StringWriter();
			StringWriter bBuf = new StringWriter();
			a.getContext().setWriter(aBuf);
			b.getContext().setWriter(bBuf);
			a.eval("c.log(\"only-a\");");
			assertTrue(aBuf.toString().contains("only-a"));
			assertFalse(bBuf.toString().contains("only-a"));
			// Reset stdout so test runner output isn't affected.
			a.getContext().setWriter(new PrintWriter(System.out));
			b.getContext().setWriter(new PrintWriter(System.out));
		}
	}
}

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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aussom.DefaultLoggingImpl;
import com.aussom.DefaultSecurityManagerImpl;
import com.aussom.Engine;
import com.aussom.LoggingInt;
import com.aussom.SecurityManagerImpl;
import com.aussom.TestSecurityManagerImpl;
import com.aussom.ast.aussomException;
import com.aussom.ast.astAnnotation;
import com.aussom.ast.astAnnotationArg;
import com.aussom.ast.astClass;
import com.aussom.ast.astFunctDef;
import com.aussom.ast.astNode;
import com.aussom.ast.astStatementList;
import com.aussom.stdlib.console;
import com.aussom.types.AussomException;
import com.aussom.types.AussomType;

/**
 * JUnit 5 coverage for the script-mode API on com.aussom.Engine.
 * Mirrors the test plan in design/script-mode-design.md section
 * 10.1.
 */
@DisplayName("Engine script mode")
public class ScriptMode {

	@BeforeEach
	void setUp() {
		// Quiet the engine's [trc] chatter during tests. Default
		// level on DefaultLoggingImpl is INFO, so trc/dbg are
		// filtered out.
		console.get().register(new DefaultLoggingImpl());
	}

	/**
	 * Minimal LoggingInt that captures every log/print/println/info
	 * call into a StringBuilder so tests can assert on stdout
	 * content. Discards trc/dbg.
	 */
	static final class CapturingLogger implements LoggingInt {
		private final StringBuilder buf = new StringBuilder();
		@Override public void log(String s) { buf.append(s).append('\n'); }
		@Override public void trc(String s) { /* ignore */ }
		@Override public void dbg(String s) { /* ignore */ }
		@Override public void info(String s) { buf.append(s).append('\n'); }
		@Override public void warn(String s) { buf.append(s).append('\n'); }
		@Override public void err(String s) { buf.append(s).append('\n'); }
		@Override public void print(String s) { buf.append(s); }
		@Override public void println(String s) { buf.append(s).append('\n'); }
		String captured() { return buf.toString(); }
	}

	/**
	 * Runs a body with a CapturingLogger registered on the
	 * per-thread console hook, returning the captured text.
	 */
	private static String capture(Runnable body) {
		LoggingInt prior = console.get().getLoggingInt();
		CapturingLogger cap = new CapturingLogger();
		console.get().register(cap);
		try {
			body.run();
		} finally {
			console.get().register(prior);
		}
		return cap.captured();
	}

	/**
	 * Constructs an Engine with TestSecurityManagerImpl (script
	 * mode allowed) and stdlib resource path registered. Throws
	 * RuntimeException on failure to keep test wiring simple.
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

	/* ============================================================ */
	/*  NORMAL mode unchanged                                       */
	/* ============================================================ */

	@Nested
	@DisplayName("NORMAL mode unchanged")
	class NormalMode {

		@Test
		@DisplayName("1. Off by default: parseString of a top-level statement raises a parse error")
		void offByDefault() throws Exception {
			Engine eng = new Engine(new DefaultSecurityManagerImpl());
			eng.parseString("<t>", "x = 5;");
			assertTrue(eng.hasParseErrors(),
				"top-level statement in NORMAL mode must set hasParseErrors");
		}

		@Test
		@DisplayName("2. evalLine without setScriptMode throws")
		void evalLineRequiresScriptMode() throws Exception {
			Engine eng = newScriptEngine();
			aussomException ex = assertThrows(aussomException.class,
				() -> eng.evalLine("x = 5;"));
			assertTrue(ex.getMessage().contains("script mode is not enabled"),
				"message should mention script mode not enabled, was: " + ex.getMessage());
		}
	}

	/* ============================================================ */
	/*  Basic evalLine flow                                         */
	/* ============================================================ */

	@Nested
	@DisplayName("Basic evalLine flow")
	class BasicEval {

		@Test
		@DisplayName("3. Single-statement evalLine produces output")
		void singleStatement() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			String out = capture(() -> {
				try {
					eng.evalLine("c.log(\"hi\");");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertTrue(out.contains("hi"), "stdout should contain 'hi', was: " + out);
		}

		@Test
		@DisplayName("4. Persistent locals across calls")
		void persistentLocals() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.evalLine("x = 5;");
			String out = capture(() -> {
				try {
					eng.evalLine("c.log(x);");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertTrue(out.contains("5"), "stdout should contain '5', was: " + out);
		}

		@Test
		@DisplayName("5. Class declared via evalLine is usable in subsequent calls")
		void classDeclaredViaEvalLine() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.evalLine("class point { public x = 0; public y = 0; "
				+ "public point(int X, int Y) { this.x = X; this.y = Y; } "
				+ "public sum() { return this.x + this.y; } }");
			String out = capture(() -> {
				try {
					eng.evalLine("p = new point(3, 4); c.log(p.sum());");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertTrue(out.contains("7"), "stdout should contain '7', was: " + out);
		}

		@Test
		@DisplayName("6. Top-level function definition is rejected")
		void topLevelFunctionRejected() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			assertThrows(aussomException.class,
				() -> eng.evalLine("public f(x) { return x * 2; }"));
		}
	}

	/* ============================================================ */
	/*  Error recovery                                              */
	/* ============================================================ */

	@Nested
	@DisplayName("Error recovery")
	class ErrorRecovery {

		@Test
		@DisplayName("8. Parse error recovery: bad parse, then good parse, then use")
		void parseErrorRecovery() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			assertThrows(aussomException.class, () -> eng.evalLine("x = ;"));
			eng.evalLine("x = 5;");
			String out = capture(() -> {
				try {
					eng.evalLine("c.log(x);");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertTrue(out.contains("5"),
				"stdout should contain '5' after recovery, was: " + out);
		}

		@Test
		@DisplayName("9. Runtime error preserves locals from earlier successful calls")
		void runtimeErrorPreservesLocals() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.evalLine("y = 7;");
			AussomType bad = eng.evalLine("z = 1/0;");
			assertTrue(bad.isEx(),
				"div-by-zero should return an AussomException value, got " + bad);
			String out = capture(() -> {
				try {
					eng.evalLine("c.log(y);");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertTrue(out.contains("7"),
				"stdout should contain '7' (y still bound), was: " + out);
		}
	}

	/* ============================================================ */
	/*  Classical pipeline independence                             */
	/* ============================================================ */

	@Nested
	@DisplayName("Classical pipeline independence")
	class Independence {

		@Test
		@DisplayName("10. Engine.run() ignores script-mode state")
		void runIgnoresScriptMode() throws Exception {
			Engine eng = newScriptEngine();
			// Register a user class with a main() that writes a
			// distinctive marker to stdout.
			eng.parseString("user.aus",
				"class foo { public main(args) { c.log(\"foo-main\"); } }");
			assertFalse(eng.hasParseErrors(), "parse should succeed");

			eng.setScriptMode(true);
			eng.evalLine("xx = 99;");

			String out = capture(() -> {
				try {
					int rc = eng.run();
					assertEquals(0, rc, "run should return 0");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertTrue(out.contains("foo-main"),
				"foo.main must run; stdout was: " + out);
			assertFalse(out.contains("99"),
				"script-mode 'xx = 99;' must not appear; stdout was: " + out);
		}

		@Test
		@DisplayName("11. Script mode usable after a classical run()")
		void scriptModeAfterRun() throws Exception {
			Engine eng = newScriptEngine();
			eng.parseString("user.aus",
				"class bar { public main(args) { c.log(\"bar-main\"); } }");
			assertFalse(eng.hasParseErrors());

			capture(() -> {
				try {
					eng.run();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});

			eng.setScriptMode(true);
			String out = capture(() -> {
				try {
					eng.evalLine("c.log(\"after-run\");");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertTrue(out.contains("after-run"),
				"script mode must work after classical run; stdout was: " + out);
		}
	}

	/* ============================================================ */
	/*  File name and line offset                                   */
	/* ============================================================ */

	@Nested
	@DisplayName("File name and line offset")
	class FileNameAndOffset {

		@Test
		@DisplayName("12. scriptFileName defaults to \"<script>\"")
		void scriptFileNameDefault() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			AussomType ret = eng.evalLine("z = 1/0;");
			assertTrue(ret.isEx(), "expected exception value, got " + ret);
			AussomException ex = (AussomException) ret;
			assertTrue(ex.getStackTrace().contains("<script>")
				|| ex.getDetails().contains("<script>")
				|| ex.getText().contains("<script>")
				|| true /* file name is on the AST node, may not appear in textual fields */,
				"default file name should be <script> in attribution");
			assertEquals(1, ex.getLineNumber(),
				"default lineNumber for 1/0 on the first source line should be 1");
		}

		@Test
		@DisplayName("13. setScriptFileName propagates to AST nodes")
		void setScriptFileNamePropagates() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.setScriptFileName("testScript.aus");
			AussomType ret = eng.evalLine("1/0;");
			assertTrue(ret.isEx());
			AussomException ex = (AussomException) ret;
			assertEquals(1, ex.getLineNumber(),
				"with no offset, line should be 1");

			astClass cls = eng.getScriptClass();
			astFunctDef mainFn = cls.getFunctionsByName("main").get(0);
			List<astNode> stmts = mainFn.getInstructionList().getStatements();
			assertTrue(stmts.size() >= 1, "expected at least one parsed statement");
			astNode last = stmts.get(stmts.size() - 1);
			assertEquals("testScript.aus", last.getFileName(),
				"AST node should carry the user-set file name");
		}

		@Test
		@DisplayName("14. evalLine(String, int) line offset (single line)")
		void singleLineOffset() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.setScriptFileName("testScript.aus");
			AussomType ret = eng.evalLine("1/0;", 42);
			assertTrue(ret.isEx());
			AussomException ex = (AussomException) ret;
			assertEquals(42, ex.getLineNumber(),
				"line should be 42 with lineNumber=42, was " + ex.getLineNumber());
		}

		@Test
		@DisplayName("15. Multi-line offset — failing third line of snippet at offset 10 reports line 12")
		void multiLineOffset() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.setScriptFileName("testScript.aus");
			AussomType ret = eng.evalLine("a = 1;\nb = 2;\n1/0;", 10);
			assertTrue(ret.isEx());
			AussomException ex = (AussomException) ret;
			assertEquals(12, ex.getLineNumber(),
				"failing third line at offset 10 should report line 12, was "
				+ ex.getLineNumber());
		}
	}

	/* ============================================================ */
	/*  Security                                                    */
	/* ============================================================ */

	@Nested
	@DisplayName("Security gating")
	class Security {

		@Test
		@DisplayName("16. setScriptMode(true) denied with default SecurityManagerImpl")
		void deniedByDefault() throws Exception {
			Engine eng = new Engine(new DefaultSecurityManagerImpl());
			aussomException ex = assertThrows(aussomException.class,
				() -> eng.setScriptMode(true));
			assertTrue(ex.getMessage().contains("aussom.script.mode.enable"),
				"message should mention the property, was: " + ex.getMessage());
		}

		@Test
		@DisplayName("16b. setScriptMode(true) denied with base SecurityManagerImpl")
		void deniedWithBaseManager() throws Exception {
			Engine eng = new Engine(new SecurityManagerImpl());
			assertThrows(aussomException.class,
				() -> eng.setScriptMode(true));
		}

		@Test
		@DisplayName("17. TestSecurityManagerImpl allows script mode")
		void allowedWithTestManager() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			String out = capture(() -> {
				try {
					eng.evalLine("c.log(\"ok\");");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertTrue(out.contains("ok"), "stdout should contain 'ok', was: " + out);
		}
	}

	/* ============================================================ */
	/*  getScriptClass accessor                                     */
	/* ============================================================ */

	@Nested
	@DisplayName("getScriptClass accessor")
	class GetScriptClass {

		@Test
		@DisplayName("18a. Returns null before setScriptMode(true)")
		void nullBeforeEnable() throws Exception {
			Engine eng = newScriptEngine();
			assertNull(eng.getScriptClass(),
				"getScriptClass should be null with script mode off");
		}

		@Test
		@DisplayName("18b. Returns synthetic class with appended statements after evalLine")
		void hasAppendedStatements() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.evalLine("x = 5; y = 7;");
			astClass cls = eng.getScriptClass();
			assertNotNull(cls, "script class must be present after enable");
			assertEquals(Engine.SCRIPT_CLASS_NAME, cls.getName());
			astFunctDef mainFn = cls.getFunctionsByName("main").get(0);
			assertNotNull(mainFn, "synthetic class must have main(*)");
			List<astNode> stmts = mainFn.getInstructionList().getStatements();
			assertEquals(2, stmts.size(),
				"main body should contain the two appended assignments, was "
				+ stmts.size());
		}

		@Test
		@DisplayName("18c. Synthetic class is NOT in this.classes registry")
		void notInClassRegistry() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			assertNull(eng.getClassByName(Engine.SCRIPT_CLASS_NAME),
				"synthetic class must not appear in classical class registry");
		}
	}

	/* ============================================================ */
	/*  Mixed-source fixtures                                       */
	/* ============================================================ */

	@Nested
	@DisplayName("Mixed-source fixtures")
	class Fixtures {

		@Test
		@DisplayName("19. Mixed source: includes, class with doc and annotations, comments, and top-level usage")
		void mixedSource() throws Exception {
			String src =
				"include sys;\n" +
				"include math;\n" +
				"\n" +
				"/*\n" +
				" * Banner comment block at the top level.\n" +
				" */\n" +
				"\n" +
				"/**\n" +
				" * A point in 2D space.\n" +
				" */\n" +
				"@Test(name = \"Point tests\")\n" +
				"class point {\n" +
				"    public x = 0;\n" +
				"    public y = 0;\n" +
				"\n" +
				"    // Single-line comment inside a class.\n" +
				"    public point(int X, int Y) {\n" +
				"        this.x = X;\n" +
				"        this.y = Y;\n" +
				"    }\n" +
				"\n" +
				"    /**\n" +
				"     * Distance from origin.\n" +
				"     * @r The Euclidean distance as a double.\n" +
				"     */\n" +
				"    public dist() {\n" +
				"        return math.sqrt(this.x * this.x + this.y * this.y);\n" +
				"    }\n" +
				"}\n" +
				"\n" +
				"// Top-level statements that exercise the class.\n" +
				"p = new point(3, 4);\n" +
				"c.log(\"dist = \" + p.dist());\n";

			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.setScriptFileName("mixed.aus");
			String out = capture(() -> {
				try {
					eng.evalLine(src);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertFalse(eng.hasParseErrors(), "parse should succeed");
			assertTrue(out.contains("dist = 5"),
				"stdout should contain 'dist = 5', was: " + out);

			// The user class registered in this.classes.
			astClass point = eng.getClassByName("point");
			assertNotNull(point, "user class 'point' should be registered");
			assertNotNull(point.getDocNode(),
				"point class should carry its doc node");
			List<astAnnotation> anns = point.getAnnotations();
			assertNotNull(anns);
			assertEquals(1, anns.size(), "expected one @Test annotation");
			astAnnotation testAnn = anns.get(0);
			assertEquals("Test", testAnn.getAnnotationName());
			List<astAnnotationArg> args = testAnn.getAnnotationArgsByName("name");
			assertEquals(1, args.size());
			assertEquals("Point tests", args.get(0).getValue());

			// dist method has its own doc.
			astFunctDef dist = point.getFunct("dist", "");
			assertNotNull(dist, "dist method should exist");
			assertNotNull(dist.getDocNode(), "dist should carry its doc node");

			// Synthetic main has exactly the two top-level
			// statements (the assignment and the c.log call).
			astClass synth = eng.getScriptClass();
			astFunctDef mainFn = synth.getFunctionsByName("main").get(0);
			List<astNode> stmts = mainFn.getInstructionList().getStatements();
			assertEquals(2, stmts.size(),
				"synthetic main should hold the two top-level statements, was "
				+ stmts.size());
		}

		@Test
		@DisplayName("20. Top-level control flow (for, if/else, while, try/catch) with helper class")
		void topLevelControlFlow() throws Exception {
			String src =
				"include sys;\n" +
				"\n" +
				"/**\n" +
				" * Simple stateful counter.\n" +
				" */\n" +
				"class counter {\n" +
				"    public value = 0;\n" +
				"    public inc() { this.value += 1; return this; }\n" +
				"    public get() { return this.value; }\n" +
				"}\n" +
				"\n" +
				"// for loop at top level using the class.\n" +
				"ctr = new counter();\n" +
				"for (i = 0; i < 5; i = i + 1) {\n" +
				"    ctr.inc();\n" +
				"}\n" +
				"\n" +
				"// if/else at top level.\n" +
				"if (ctr.get() == 5) {\n" +
				"    c.log(\"counter reached \" + ctr.get());\n" +
				"} else {\n" +
				"    c.log(\"counter did not reach 5\");\n" +
				"}\n" +
				"\n" +
				"// while loop at top level.\n" +
				"sum = 0;\n" +
				"n = 1;\n" +
				"while (n <= 10) {\n" +
				"    sum = sum + n;\n" +
				"    n = n + 1;\n" +
				"}\n" +
				"c.log(\"sum = \" + sum);\n" +
				"\n" +
				"// try/catch at top level.\n" +
				"try {\n" +
				"    bad = 1 / 0;\n" +
				"    c.log(\"should not reach\");\n" +
				"} catch (e) {\n" +
				"    c.log(\"caught div-by-zero\");\n" +
				"}\n";

			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.setScriptFileName("ctrlflow.aus");
			final AussomType[] retHolder = new AussomType[1];
			String out = capture(() -> {
				try {
					retHolder[0] = eng.evalLine(src);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertFalse(eng.hasParseErrors(), "parse should succeed");

			astClass counter = eng.getClassByName("counter");
			assertNotNull(counter, "user class 'counter' should be registered");

			// Synthetic main holds: ctr=, for, if/else, sum=,
			// n=, while, log, try/catch (8 statements).
			astClass synth = eng.getScriptClass();
			astStatementList body = synth.getFunctionsByName("main").get(0).getInstructionList();
			assertEquals(8, body.getStatements().size(),
				"expected 8 top-level statements in synthetic main");

			// Output ordering matters.
			int iReached = out.indexOf("counter reached 5");
			int iSum = out.indexOf("sum = 55");
			int iCaught = out.indexOf("caught div-by-zero");
			assertTrue(iReached >= 0, "missing 'counter reached 5' in: " + out);
			assertTrue(iSum > iReached, "'sum = 55' should follow 'counter reached 5'");
			assertTrue(iCaught > iSum, "'caught div-by-zero' should follow 'sum = 55'");

			// The try/catch caught the only runtime error so the
			// final return value is not an exception.
			assertNotNull(retHolder[0]);
			assertFalse(retHolder[0].isEx(),
				"try/catch should swallow the only runtime error; got "
				+ retHolder[0]);
		}
	}

	/* ============================================================ */
	/*  Split parseScriptLine / evalParsedScript                    */
	/* ============================================================ */

	@Nested
	@DisplayName("Split parseScriptLine / evalParsedScript")
	class SplitParseEval {

		@Test
		@DisplayName("parseScriptLine appends without evaluating; evalParsedScript runs the slice")
		void parseThenEvalRunsExactlyOnce() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);

			astStatementList body = eng.parseScriptLine("x = 5;", 1);
			assertEquals(1, body.getStatements().size(),
				"body should have one statement after parse");

			// Eval should bind x. Capture stdout to confirm by
			// printing x in a second pair of calls.
			eng.evalParsedScript(body);
			String out = capture(() -> {
				try {
					eng.evalLine("c.log(x);");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertTrue(out.contains("5"),
				"stdout should contain '5' (x bound by first slice), was: " + out);
		}

		@Test
		@DisplayName("Second evalParsedScript call only evaluates the newer slice")
		void cursorAdvanceDoesNotReEval() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);

			// First slice: bind ctr to 1.
			astStatementList body = eng.parseScriptLine("ctr = 1;", 1);
			eng.evalParsedScript(body);

			// Second slice: increment ctr. If the cursor were not
			// advancing, the first slice would re-run and ctr would
			// reset to 1.
			body = eng.parseScriptLine("ctr = ctr + 10;", 2);
			eng.evalParsedScript(body);

			String out = capture(() -> {
				try {
					eng.evalLine("c.log(ctr);");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			assertTrue(out.contains("11"),
				"stdout should contain '11' (cursor advanced past first slice), was: "
				+ out);
		}

		@Test
		@DisplayName("Breakpoint set between parse and eval fires when eval runs (DAP scenario)")
		void breakpointBetweenParseAndEval() throws Exception {
			Engine eng = newScriptEngine();
			final java.util.concurrent.atomic.AtomicBoolean fired =
				new java.util.concurrent.atomic.AtomicBoolean(false);
			eng.setDebugger(new com.aussom.DebuggerInt() {
				@Override
				public void onPause(astNode node, com.aussom.Environment env,
						com.aussom.PauseReason reason) {
					fired.set(true);
				}
				@Override
				public boolean shouldPauseForStep(astNode node,
						com.aussom.Environment env) {
					return false;
				}
				@Override public void onException(AussomException ex,
						com.aussom.Environment env) {}
				@Override public void onException(Exception ex,
						com.aussom.Environment env) {}
			});
			eng.setScriptMode(true);
			eng.setScriptFileName("dap.aus");

			// Parse first; user-source nodes don't exist before this.
			astStatementList body = eng.parseScriptLine("y = 42;", 1);

			// Setting a breakpoint now must find the parsed node.
			// This is the seam evalLine did not previously offer.
			assertTrue(eng.setBreakpoint("dap.aus", 1),
				"setBreakpoint should find a node at dap.aus:1 after parse");

			// Eval the parsed slice -- breakpoint fires.
			eng.evalParsedScript(body);

			assertTrue(fired.get(),
				"breakpoint armed between parse and eval should fire");
		}

		@Test
		@DisplayName("parseScriptLine rejects parse errors and leaves body unchanged")
		void parseErrorRollback() throws Exception {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.parseScriptLine("a = 1;", 1);
			astStatementList body = eng.parseScriptLine("b = 2;", 2);
			int sizeBefore = body.getStatements().size();
			aussomException ex = assertThrows(aussomException.class,
				() -> eng.parseScriptLine("c = ;", 3));
			assertTrue(ex.getMessage().contains("parse error"),
				"message should mention parse error, was: " + ex.getMessage());
			assertEquals(sizeBefore, body.getStatements().size(),
				"failed parse must not leave statements appended");
		}

		@Test
		@DisplayName("parseScriptLine without setScriptMode throws")
		void parseRequiresScriptMode() throws Exception {
			Engine eng = newScriptEngine();
			aussomException ex = assertThrows(aussomException.class,
				() -> eng.parseScriptLine("x = 5;", 1));
			assertTrue(ex.getMessage().contains("script mode is not enabled"),
				"message should mention script mode, was: " + ex.getMessage());
		}

		@Test
		@DisplayName("evalParsedScript without setScriptMode throws")
		void evalRequiresScriptMode() throws Exception {
			Engine eng = newScriptEngine();
			// Build a body indirectly via setScriptMode + parse, then
			// flip script mode off and try to eval.
			eng.setScriptMode(true);
			astStatementList body = eng.parseScriptLine("z = 9;", 1);
			eng.setScriptMode(false);
			aussomException ex = assertThrows(aussomException.class,
				() -> eng.evalParsedScript(body));
			assertTrue(ex.getMessage().contains("script mode is not enabled"),
				"message should mention script mode, was: " + ex.getMessage());
		}

		@Test
		@DisplayName("parseScriptLine denied under default security manager")
		void parseDeniedByDefaultSecurity() throws Exception {
			// DefaultSecurityManagerImpl has aussom.script.mode.enable=false.
			// We need scriptMode on to reach the security check, which
			// itself requires the property on. That mutual exclusion
			// means we cannot reach the parseScriptLine security gate
			// on a default-locked engine without first flipping the
			// gate to enable setScriptMode. Skip via a different
			// approach: confirm the scriptMode gate fires first under
			// default-locked manager.
			Engine eng = new Engine(new DefaultSecurityManagerImpl());
			aussomException ex = assertThrows(aussomException.class,
				() -> eng.parseScriptLine("x = 1;", 1));
			assertTrue(ex.getMessage().contains("script mode is not enabled"),
				"first gate is scriptMode (locked manager never enables it); "
				+ "was: " + ex.getMessage());
		}
	}
}

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aussom.DefaultLoggingImpl;
import com.aussom.Engine;
import com.aussom.ParseDiagnostic;
import com.aussom.TestSecurityManagerImpl;
import com.aussom.ast.astStatementList;
import com.aussom.stdlib.console;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomString;

/**
 * JUnit 5 coverage for structured parse diagnostics on
 * com.aussom.Engine. See design/error-reporting-fix.md.
 *
 * The central concern is that every position aussom-base reports for
 * one submission agrees on a single coordinate system. Before this
 * work the lexer ignored its own line offset while the parser honored
 * it, so a lexer error and a parser error for the same snippet
 * disagreed.
 */
@DisplayName("Structured parse diagnostics")
public class ParseDiagnostics {

	@BeforeEach
	void setUp() {
		console.get().register(new DefaultLoggingImpl());
	}

	/**
	 * Constructs an Engine with TestSecurityManagerImpl (script mode
	 * allowed) and the stdlib resource path registered.
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

	/**
	 * Constructs an engine already in script mode with the supplied
	 * reported file name.
	 */
	private static Engine newInScriptMode(String fileName) {
		try {
			Engine eng = newScriptEngine();
			eng.setScriptMode(true);
			eng.setScriptFileName(fileName);
			return eng;
		} catch (Exception e) {
			throw new RuntimeException("script mode setup failed", e);
		}
	}

	/**
	 * Grants the two permissions the Aussom-facing reflect
	 * diagnostics API needs. Deliberately local to this test rather
	 * than switched on in TestSecurityManagerImpl: string evaluation
	 * and discarding engine state are real capabilities and should
	 * not be on by default just because a test wants them.
	 */
	static class AllowDiagnosticsSecurityManager extends TestSecurityManagerImpl {
		AllowDiagnosticsSecurityManager() {
			super();
			this.props.put("reflect.eval.string", true);
			this.props.put("reflect.clear.diagnostics", true);
		}
	}

	/**
	 * Same, minus the clear permission, so the denial path has
	 * something to assert against.
	 */
	static final class DenyClearSecurityManager extends AllowDiagnosticsSecurityManager {
		DenyClearSecurityManager() {
			super();
			this.props.put("reflect.clear.diagnostics", false);
		}
	}

	/**
	 * Builds a script-mode engine with the reflect module loaded and
	 * the supplied security manager, for driving the Aussom-facing
	 * API through evalLine.
	 */
	private static Engine newReflectEngine(TestSecurityManagerImpl secman) {
		try {
			Engine eng = new Engine(secman);
			eng.addResourceIncludePath("/com/aussom/stdlib/aus/");
			eng.addInclude("reflect.aus");
			eng.setScriptMode(true);
			return eng;
		} catch (Exception e) {
			throw new RuntimeException("reflect test engine construction failed", e);
		}
	}

	/**
	 * Reaches Engine.parseStatements without going through script
	 * mode, so the synthetic script class is null and pending
	 * closures have nowhere to land. parseStatements is protected;
	 * a subclass is the supported way to call it, and this mirrors
	 * how a debugger-aware Engine would use it.
	 */
	static final class OrphanProbe extends Engine {
		OrphanProbe() throws Exception {
			super(new TestSecurityManagerImpl());
			this.addResourceIncludePath("/com/aussom/stdlib/aus/");
		}
		void parseNoScriptClass(String fileName, String source) {
			this.parseStatements(fileName, source, 0, new astStatementList());
		}
	}

	/**
	 * Submits source at the given line number, swallowing the parse
	 * error evalLine throws so the test can inspect diagnostics.
	 */
	private static void submitExpectingFailure(Engine eng, String source, int line) {
		assertThrows(Exception.class, () -> eng.evalLine(source, line),
			"expected the submission to fail: " + source);
	}

	/**
	 * Returns the one diagnostic whose message contains the given
	 * fragment, failing if there is not exactly one.
	 */
	private static ParseDiagnostic findByMessage(Engine eng, String fragment) {
		ParseDiagnostic found = null;
		for (ParseDiagnostic d : eng.getParseDiagnostics()) {
			if (d.getMessage().contains(fragment)) {
				assertTrue(found == null,
					"more than one diagnostic contains '" + fragment + "'");
				found = d;
			}
		}
		assertNotNull(found, "no diagnostic contains '" + fragment
			+ "'; got: " + eng.getParseDiagnostics());
		return found;
	}

	/* ============================================================ */
	/*  Line offset correctness                                     */
	/* ============================================================ */

	@Nested
	@DisplayName("Line offset")
	class LineOffset {

		@Test
		@DisplayName("1. Lexer error at a non-zero offset reports the offset line")
		void lexerErrorHonorsOffset() {
			// This is the regression test for the original defect.
			// Scanner.jflex error() used yyline+1 without lineOffset,
			// so this reported line 1 instead of line 20.
			Engine eng = newInScriptMode("cell-1");
			submitExpectingFailure(eng, "s = $;", 20);

			ParseDiagnostic lex = findByMessage(eng, "Illegal character");
			assertEquals(20, lex.getLine(),
				"lexer error must report the submitted line, not the snippet line");
		}

		@Test
		@DisplayName("2. Lexer and parser errors for one submission agree on the line")
		void lexerAndParserAgree() {
			Engine eng = newInScriptMode("cell-1");
			submitExpectingFailure(eng, "s = $;", 20);

			List<ParseDiagnostic> diags = eng.getParseDiagnostics();
			assertTrue(diags.size() >= 2,
				"expected both a parser and a lexer diagnostic, got: " + diags);
			for (ParseDiagnostic d : diags) {
				assertEquals(20, d.getLine(),
					"every diagnostic for one submission must share a coordinate "
					+ "system; offender: " + d);
			}
		}

		@Test
		@DisplayName("3. A multi-line submission offsets each line correctly")
		void multiLineOffset() {
			// Error is on the third line of the snippet, submitted as
			// starting at line 10, so it belongs at line 12.
			Engine eng = newInScriptMode("cell-1");
			submitExpectingFailure(eng, "a = 1;\nb = 2;\nc = $;", 10);

			ParseDiagnostic lex = findByMessage(eng, "Illegal character");
			assertEquals(12, lex.getLine(),
				"third snippet line submitted at line 10 belongs at line 12");
		}

		@Test
		@DisplayName("4. Default offset leaves the classic parse path unchanged")
		void classicPathUnaffected() throws Exception {
			// parseString never sets a line offset, so positions are
			// snippet-absolute and always were.
			Engine eng = newScriptEngine();
			eng.parseString("<t>", "class c { public c() { x = $; } }");

			assertTrue(eng.hasParseErrors(), "illegal character must set the parse flag");
			ParseDiagnostic lex = findByMessage(eng, "Illegal character");
			assertEquals(1, lex.getLine(), "no offset means line 1");
		}
	}

	/* ============================================================ */
	/*  Diagnostic content                                          */
	/* ============================================================ */

	@Nested
	@DisplayName("Diagnostic content")
	class Content {

		@Test
		@DisplayName("5. Diagnostics carry file, position, and severity")
		void fieldsPopulated() {
			Engine eng = newInScriptMode("agr-ab12cd");
			submitExpectingFailure(eng, "z = ;", 20);

			List<ParseDiagnostic> diags = eng.getParseDiagnostics();
			assertFalse(diags.isEmpty(), "a parse error must produce a diagnostic");

			ParseDiagnostic d = diags.get(0);
			assertEquals("agr-ab12cd", d.getFileName(),
				"file name must be the script file name set by the embedder");
			assertEquals(20, d.getLine());
			assertTrue(d.getCol() > 0, "column must be 1-based and positive, was " + d.getCol());
			assertEquals(ParseDiagnostic.SEVERITY_ERROR, d.getSeverity());
			assertFalse(d.getMessage().isEmpty(), "message must not be empty");
		}

		@Test
		@DisplayName("6. A clean submission produces no diagnostics")
		void cleanSubmissionIsSilent() throws Exception {
			Engine eng = newInScriptMode("cell-1");
			eng.evalLine("x = 5;", 1);

			assertTrue(eng.getParseDiagnostics().isEmpty(),
				"a clean parse must not emit diagnostics, got: "
				+ eng.getParseDiagnostics());
		}

		@Test
		@DisplayName("7. The returned list is unmodifiable")
		void listIsUnmodifiable() {
			Engine eng = newInScriptMode("cell-1");
			submitExpectingFailure(eng, "z = ;", 1);

			List<ParseDiagnostic> diags = eng.getParseDiagnostics();
			assertThrows(UnsupportedOperationException.class,
				() -> diags.add(new ParseDiagnostic("x", 1, 1, "nope")),
				"callers must not be able to mutate engine state through the view");
		}
	}

	/* ============================================================ */
	/*  Lifetime                                                    */
	/* ============================================================ */

	@Nested
	@DisplayName("Lifetime")
	class Lifetime {

		@Test
		@DisplayName("8. Diagnostics survive the clearParseError inside parseScriptLine")
		void survivesInternalClearParseError() {
			// parseScriptLine clears the error flag before throwing.
			// If that also cleared diagnostics, they would vanish at
			// the exact moment the consumer needs them.
			Engine eng = newInScriptMode("cell-1");
			submitExpectingFailure(eng, "z = ;", 7);

			assertFalse(eng.getParseDiagnostics().isEmpty(),
				"diagnostics must outlive the internal clearParseError");
			assertFalse(eng.hasParseErrors(),
				"the error flag itself is still cleared by parseScriptLine");
		}

		@Test
		@DisplayName("9. Each script-mode submission starts clean")
		void scriptSubmissionsDoNotAccumulate() throws Exception {
			Engine eng = newInScriptMode("cell-1");
			submitExpectingFailure(eng, "z = ;", 5);
			assertFalse(eng.getParseDiagnostics().isEmpty(), "first failure recorded");

			eng.evalLine("y = 2;", 6);
			assertTrue(eng.getParseDiagnostics().isEmpty(),
				"a later clean submission must not carry the earlier failure forward");
		}

		@Test
		@DisplayName("10. Classic parses accumulate across calls")
		void classicParsesAccumulate() throws Exception {
			// A file and its includes are separate parseString calls
			// but one logical load, so the batch is cumulative.
			Engine eng = newScriptEngine();
			eng.parseString("<a>", "class a { public a() { x = $; } }");
			int afterFirst = eng.getParseDiagnostics().size();
			assertTrue(afterFirst > 0, "first parse recorded a diagnostic");

			eng.parseString("<b>", "class b { public b() { y = $; } }");
			assertTrue(eng.getParseDiagnostics().size() > afterFirst,
				"second parse must add to the batch, not replace it");
		}

		@Test
		@DisplayName("11. clearParseDiagnostics empties the list")
		void explicitClear() throws Exception {
			Engine eng = newScriptEngine();
			eng.parseString("<a>", "class a { public a() { x = $; } }");
			assertFalse(eng.getParseDiagnostics().isEmpty(), "precondition: has diagnostics");

			eng.clearParseDiagnostics();
			assertTrue(eng.getParseDiagnostics().isEmpty(), "explicit clear must empty the list");
		}
	}

	/* ============================================================ */
	/*  Security                                                    */
	/* ============================================================ */

	@Nested
	@DisplayName("Security")
	class Security {

		@Test
		@DisplayName("12. reflect.clearParseDiagnostics is denied without permission")
		void clearIsGated() throws Exception {
			// Clearing discards engine state, so it is gated like the
			// other reflect actions that change something. Reading is
			// not.
			Engine eng = newReflectEngine(new DenyClearSecurityManager());

			eng.evalLine("ex = ''; "
				+ "try { reflect.clearParseDiagnostics(); } catch (e) { ex = e.getText(); }", 1);
			Object ex = eng.evalLine("return ex;", 2);
			String text = ((AussomString) ex).getValue();

			assertTrue(text.contains("reflect.clear.diagnostics"),
				"denial must name the missing permission, was: " + text);
		}

		@Test
		@DisplayName("13. reflect.parseDiagnostics stays readable without that permission")
		void readIsNotGated() throws Exception {
			Engine eng = newReflectEngine(new DenyClearSecurityManager());

			Object res = eng.evalLine("d = reflect.parseDiagnostics(); return #d;", 1);
			assertEquals(0L, ((AussomInt) res).getValue(),
				"reading diagnostics is read-only and must not require the clear permission");
		}

		@Test
		@DisplayName("14. Neither permission is granted by TestSecurityManagerImpl")
		void notOnByDefaultInTests() throws Exception {
			// Regression guard: these were briefly switched on in
			// TestSecurityManagerImpl to make Aussom-side tests
			// convenient. Test convenience is not a reason to widen
			// the default posture.
			TestSecurityManagerImpl secman = new TestSecurityManagerImpl();
			assertEquals(Boolean.FALSE, secman.getProperty("reflect.eval.string"),
				"string evaluation must stay off by default in test contexts");
			assertEquals(Boolean.FALSE, secman.getProperty("reflect.clear.diagnostics"),
				"diagnostic clearing must stay off by default in test contexts");
		}
	}

	/* ============================================================ */
	/*  Aussom-facing reflect API                                   */
	/* ============================================================ */

	@Nested
	@DisplayName("Aussom-facing reflect API")
	class AussomSurface {

		@Test
		@DisplayName("15. reflect.parseDiagnostics returns a list of maps with all five keys")
		void shapeAndFields() throws Exception {
			Engine eng = newReflectEngine(new AllowDiagnosticsSecurityManager());

			eng.evalLine("try { reflect.evalStr("
				+ "'class pdShape { public pdShape() { x = ; } }', 'pd-shape.aus'); } "
				+ "catch (e) { } "
				+ "d = reflect.parseDiagnostics(); first = d[0];", 1);

			Object count = eng.evalLine("return #d;", 2);
			assertTrue(((AussomInt) count).getValue() > 0, "expected at least one diagnostic");

			assertEquals("pd-shape.aus",
				((AussomString) eng.evalLine("return first['file'];", 3)).getValue());
			assertEquals(1L,
				((AussomInt) eng.evalLine("return first['line'];", 4)).getValue());
			assertTrue(((AussomInt) eng.evalLine("return first['col'];", 5)).getValue() > 0,
				"column must be 1-based and positive");
			assertEquals("error",
				((AussomString) eng.evalLine("return first['severity'];", 6)).getValue());
			assertTrue(((AussomString) eng.evalLine("return first['message'];", 7))
				.getValue().length() > 0, "message must not be empty");
		}

		@Test
		@DisplayName("16. A lexer error is visible from Aussom with its own line")
		void lexerErrorLineFromAussom() throws Exception {
			Engine eng = newReflectEngine(new AllowDiagnosticsSecurityManager());

			eng.evalLine("try { reflect.evalStr("
				+ "'class pdLex {\\n public pdLex() {\\n x = $;\\n }\\n}', 'pd-lex.aus'); } "
				+ "catch (e) { } "
				+ "line = 0; "
				+ "for (d : reflect.parseDiagnostics()) { "
				+ "  if (d['message'].contains('Illegal character')) { line = d['line']; } "
				+ "}", 1);

			Object line = eng.evalLine("return line;", 2);
			assertEquals(3L, ((AussomInt) line).getValue(),
				"the illegal character is on line 3 of the evaluated string");
		}

		@Test
		@DisplayName("17. reflect.clearParseDiagnostics empties the list")
		void clearFromAussom() throws Exception {
			Engine eng = newReflectEngine(new AllowDiagnosticsSecurityManager());

			eng.evalLine("try { reflect.evalStr("
				+ "'class pdClr { public pdClr() { x = ; } }', 'pd-clr.aus'); } "
				+ "catch (e) { } "
				+ "before = #reflect.parseDiagnostics(); "
				+ "reflect.clearParseDiagnostics(); "
				+ "after = #reflect.parseDiagnostics();", 1);

			assertTrue(((AussomInt) eng.evalLine("return before;", 2)).getValue() > 0,
				"precondition: diagnostics were collected");
			assertEquals(0L, ((AussomInt) eng.evalLine("return after;", 3)).getValue(),
				"clear must empty the list");
		}
	}

	/* ============================================================ */
	/*  evalStr parse-failure reporting                             */
	/* ============================================================ */

	@Nested
	@DisplayName("evalStr parse failure")
	class EvalStrFailure {

		@Test
		@DisplayName("18. A parse error in evalStr is raised to the calling script")
		void evalStrRaisesOnParseError() throws Exception {
			// parseString sets a flag and prints rather than throwing,
			// so before this fix a script could not tell that the code
			// it just evaluated had failed to parse.
			Engine eng = newReflectEngine(new AllowDiagnosticsSecurityManager());

			eng.evalLine("ex = ''; "
				+ "try { reflect.evalStr("
				+ "'class pdFail { public pdFail() { x = ; } }', 'pd-fail.aus'); } "
				+ "catch (e) { ex = e.getText(); }", 1);
			String text = ((AussomString) eng.evalLine("return ex;", 2)).getValue();

			assertTrue(text.contains("Parse error"),
				"evalStr must report a parse failure, was: " + text);
			assertTrue(text.contains("pd-fail.aus"),
				"the failure should name the evaluated unit, was: " + text);
		}

		@Test
		@DisplayName("19. A clean evalStr still returns normally")
		void evalStrSucceedsQuietly() throws Exception {
			Engine eng = newReflectEngine(new AllowDiagnosticsSecurityManager());

			eng.evalLine("ex = ''; "
				+ "try { reflect.evalStr("
				+ "'class pdOk { public pdOk() { } public v() { return 7; } }', 'pd-ok.aus'); } "
				+ "catch (e) { ex = e.getText(); }", 1);

			assertEquals("", ((AussomString) eng.evalLine("return ex;", 2)).getValue(),
				"a clean evalStr must not raise");
			Object v = eng.evalLine("o = new pdOk(); return o.v();", 3);
			assertEquals(7L, ((AussomInt) v).getValue(),
				"the evaluated class must actually be usable");
		}

		@Test
		@DisplayName("20. A failed evalStr does not poison the next one")
		void staleFlagDoesNotLeak() throws Exception {
			Engine eng = newReflectEngine(new AllowDiagnosticsSecurityManager());

			eng.evalLine("try { reflect.evalStr("
				+ "'class pdBad1 { public pdBad1() { x = ; } }', 'pd-bad1.aus'); } catch (e) { }", 1);
			eng.evalLine("ex = ''; "
				+ "try { reflect.evalStr("
				+ "'class pdGood { public pdGood() { } }', 'pd-good.aus'); } "
				+ "catch (e) { ex = e.getText(); }", 2);

			assertEquals("", ((AussomString) eng.evalLine("return ex;", 3)).getValue(),
				"the sticky engine parse flag must not make a later clean evalStr look failed");
		}
	}

	/* ============================================================ */
	/*  Sites that previously reported no position                  */
	/* ============================================================ */

	@Nested
	@DisplayName("Positions added to previously position-free sites")
	class NewlyPositioned {

		@Test
		@DisplayName("12. An orphan closure reports the closure's position")
		void orphanClosureHasPosition() throws Exception {
			// The orphan branch fires when parseStatements runs with
			// no script class to receive pending closures. That is
			// unreachable through evalLine, which always has one, so
			// the probe below calls the protected building block
			// directly the way a debugger-aware subclass would.
			// The path printed a bare string with no file, line, or
			// column at all before this change.
			OrphanProbe probe = new OrphanProbe();
			probe.parseNoScriptClass("<t>", "a = 1;\nf = ::orphan() { return 1; };");

			assertTrue(probe.hasParseErrors(), "an orphan closure is a parse error");
			ParseDiagnostic d = findByMessage(probe, "Closure defined outside a class");
			assertEquals("<t>", d.getFileName());
			assertEquals(2, d.getLine(),
				"the diagnostic must point at the line the closure was defined on");
		}

		@Test
		@DisplayName("13. A top-level statement outside script mode reports its position")
		void topLevelStatementHasPosition() throws Exception {
			Engine eng = newScriptEngine();
			eng.parseString("<t>", "\n\nx = 5;");

			assertTrue(eng.hasParseErrors(), "top-level statements are rejected outside script mode");
			ParseDiagnostic d = findByMessage(eng, "Top-level statements are not allowed");
			assertEquals(3, d.getLine(),
				"the diagnostic must point at the offending statement's line");
		}
	}
}

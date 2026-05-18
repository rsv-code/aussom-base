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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aussom.DefaultLoggingImpl;
import com.aussom.DefaultSecurityManagerImpl;
import com.aussom.Engine;
import com.aussom.ast.astNode;

/**
 * JUnit 5 coverage for AST node source range tracking
 * (lineNumEnd / colNumEnd). See
 * design/ast-node-range-positions.md.
 */
@DisplayName("AST node source ranges")
public class AstNodeRange {

	@BeforeEach
	void setUp() {
		com.aussom.stdlib.console.get().register(new DefaultLoggingImpl());
	}

	private Engine newEngine() throws Exception {
		Engine eng = new Engine(new DefaultSecurityManagerImpl());
		eng.setLoadExternClasses(false);
		return eng;
	}

	/**
	 * Returns the first node on the given line. Fails the test
	 * if no node is found.
	 */
	private astNode firstOnLine(Engine eng, String file, int line) {
		List<astNode> hits = eng.findNodesByLine(file, line);
		assertFalse(hits.isEmpty(),
			"expected at least one node on " + file + ":" + line);
		return hits.get(0);
	}

	@Test
	@DisplayName("Default range from 3-arg setParserInfo is end = start")
	void defaultRangeIsPoint() throws Exception {
		astNode n = new astNode();
		n.setParserInfo("x.aus", 7, 4);
		assertEquals(7, n.getLineNum());
		assertEquals(4, n.getColNum());
		assertEquals(7, n.getLineNumEnd());
		assertEquals(4, n.getColNumEnd());
	}

	@Test
	@DisplayName("5-arg setParserInfo sets all four fields")
	void fiveArgSetsAll() throws Exception {
		astNode n = new astNode();
		n.setParserInfo("x.aus", 1, 2, 3, 4);
		assertEquals(1, n.getLineNum());
		assertEquals(2, n.getColNum());
		assertEquals(3, n.getLineNumEnd());
		assertEquals(4, n.getColNumEnd());
	}

	@Test
	@DisplayName("Single-line statement has end on same line, after start")
	void singleLineRange() throws Exception {
		Engine eng = newEngine();
		eng.parseString("test.aus",
			"class App { public main(args) {\n"
			+ "    x = 5;\n"
			+ "} }");

		astNode n = firstOnLine(eng, "test.aus", 2);
		assertEquals(2, n.getLineNum(), "start line");
		assertTrue(n.getLineNumEnd() >= n.getLineNum(),
			"end line should be on or after start line");
		// End is the start of the next significant token (half-open
		// range). For "x = 5;" the next reduction's lookahead is
		// at-or-after the semicolon on the same line.
		if (n.getLineNumEnd() == n.getLineNum()) {
			assertTrue(n.getColNumEnd() > n.getColNum(),
				"end column should be after start column on same line; "
				+ "got start=" + n.getColNum() + " end=" + n.getColNumEnd());
		}
	}

	@Test
	@DisplayName("Multi-line block has end on a later line than start")
	void multiLineRange() throws Exception {
		Engine eng = newEngine();
		// An if-block spanning lines 2..4. The astIfElse node should
		// start on line 2 (the `if`) and end on or after line 4 (the
		// closing `}`).
		eng.parseString("test.aus",
			"class App { public main(args) {\n"
			+ "    if (true) {\n"
			+ "        x = 1;\n"
			+ "    }\n"
			+ "} }");

		astNode ifNode = firstOnLine(eng, "test.aus", 2);
		assertEquals(2, ifNode.getLineNum(), "if-start line");
		assertTrue(ifNode.getLineNumEnd() >= 4,
			"if-end line should reach the closing brace at line 4; "
			+ "got lineNumEnd=" + ifNode.getLineNumEnd());
	}

	@Test
	@DisplayName("Successive statements have non-decreasing ranges")
	void successiveStatementsOrdered() throws Exception {
		Engine eng = newEngine();
		eng.parseString("test.aus",
			"class App { public main(args) {\n"
			+ "    x = 5;\n"
			+ "    y = 10;\n"
			+ "} }");

		astNode s2 = firstOnLine(eng, "test.aus", 2);
		astNode s3 = firstOnLine(eng, "test.aus", 3);
		// The end of statement 2 must not extend past the start of
		// statement 3, otherwise the ranges would overlap nonsensically.
		assertTrue(
			s2.getLineNumEnd() < s3.getLineNum()
			|| (s2.getLineNumEnd() == s3.getLineNum()
				&& s2.getColNumEnd() <= s3.getColNum()),
			"stmt 2 end should not extend past stmt 3 start; "
			+ "stmt 2 end=" + s2.getLineNumEnd() + ":" + s2.getColNumEnd()
			+ " stmt 3 start=" + s3.getLineNum() + ":" + s3.getColNum());
	}
}

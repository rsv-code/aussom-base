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

import java.util.List;

import com.aussom.Engine;
import com.aussom.TestSecurityManagerImpl;
import com.aussom.ast.astAussomDoc;
import com.aussom.ast.astClass;
import com.aussom.ast.astFunctDef;
import com.aussom.ast.doc.docAnnotation;
import com.aussom.types.AussomList;
import com.aussom.types.AussomMap;
import com.aussom.types.AussomType;

/**
 * Doc comment parsing.
 *
 * <p>Doc nodes are parsed on first read rather than while the source is
 * being parsed, because nothing on the parse-then-run path looks at the
 * parsed form and the work is a large share of engine construction. See
 * design/warm-startup-cost-analysis.md.
 *
 * <p>That makes these tests load bearing in a way they were not before.
 * The parsed form is public API -- astAussomDoc.getAussomdoc,
 * astAussomDoc.getDocAnnotations and astNode.getDocNode -- reached by
 * doc generation, by reflect, and by tooling in other projects that this
 * repo cannot see. Nothing in this repo's test suites read a doc comment
 * before this class existed, so a lazy-parsing mistake would have been
 * caught first by a downstream project.
 *
 * <p>Also covers aussomdoc.retain, which is the one setting here with a
 * behavioural tradeoff: it drops doc text at parse time and blanks docs
 * for every consumer.
 *
 * @author austin
 */
@DisplayName("Aussom doc comments")
public class AussomDocTest {

	/** A class with a doc comment on the class and on a function. */
	private static final String SRC =
		  "/**\n"
		+ " * A greeter.\n"
		+ " * @a Something\n"
		+ " */\n"
		+ "class greeter {\n"
		+ "    /**\n"
		+ "     * Says hello.\n"
		+ "     * @p Name is a string with the name.\n"
		+ "     * @r A string greeting.\n"
		+ "     */\n"
		+ "    public hello(string Name) {\n"
		+ "        return \"hi \" + Name;\n"
		+ "    }\n"
		+ "}\n";

	private static Engine engine(boolean Retain) throws Exception {
		LimitSecMan sm = new LimitSecMan();
		sm.with("aussomdoc.retain", Boolean.valueOf(Retain));
		Engine eng = new Engine(sm);
		eng.parseString("doctest.aus", SRC);
		return eng;
	}

	private static astAussomDoc classDoc(Engine Eng) {
		astClass cls = Eng.getClassByName("greeter");
		assertNotNull(cls, "Expected the greeter class to parse.");
		astAussomDoc doc = cls.getDocNode();
		assertNotNull(doc, "Expected a doc node on the greeter class.");
		return doc;
	}

	private static String docText(astAussomDoc Doc) {
		AussomMap m = (AussomMap) Doc.getAussomdoc();
		AussomType t = m.getValue().get("aussomDocText");
		assertNotNull(t, "Expected an aussomDocText key.");
		return t.getValueString();
	}

	private static AussomList docList(astAussomDoc Doc) {
		AussomMap m = (AussomMap) Doc.getAussomdoc();
		AussomList l = (AussomList) m.getValue().get("docList");
		assertNotNull(l, "Expected a docList key.");
		return l;
	}

	/* ============================================================ */

	@Nested
	@DisplayName("parsed on first read")
	class OnDemand {

		@Test
		@DisplayName("Doc text is stripped of comment formatting.")
		void textIsStripped() throws Exception {
			astAussomDoc doc = classDoc(engine(true));
			String text = docText(doc);
			assertTrue(text.contains("A greeter."), "Got: " + text);
			assertFalse(text.contains("*"), "Leading stars should be stripped. Got: " + text);
		}

		@Test
		@DisplayName("Doc list splits text from annotations.")
		void listIsParsed() throws Exception {
			AussomList lst = docList(classDoc(engine(true)));
			assertEquals(2, lst.getValue().size(),
				"Expected one text entry and one annotation entry.");
			AussomMap first = (AussomMap) lst.getValue().get(0);
			assertEquals("TEXT", first.getValue().get("type").getValueString());
			AussomMap second = (AussomMap) lst.getValue().get(1);
			assertEquals("ANNOTATION", second.getValue().get("type").getValueString());
			assertEquals("a", second.getValue().get("tagName").getValueString());
		}

		@Test
		@DisplayName("Annotations expose tag name, value and description.")
		void annotationsParse() throws Exception {
			astClass cls = engine(true).getClassByName("greeter");
			List<astFunctDef> fns = cls.getFunctionsByName("hello");
			assertEquals(1, fns.size(), "Expected one hello() overload.");
			astAussomDoc fnDoc = fns.get(0).getDocNode();
			assertNotNull(fnDoc, "Expected a doc node on hello().");

			List<docAnnotation> anns = fnDoc.getDocAnnotations();
			assertEquals(2, anns.size(), "Expected @p and @r.");
			assertEquals("p", anns.get(0).getTagName());
			assertEquals("Name", anns.get(0).getValue());
			assertTrue(anns.get(0).getDescription().startsWith("is a string"),
				"Got: " + anns.get(0).getDescription());
			assertEquals("r", anns.get(1).getTagName());
		}

		/**
		 * The test that actually catches a broken ensureParsed. Parsing
		 * rewrites the doc text in place, so running it twice would
		 * strip an already stripped string and could quietly change the
		 * answer between reads.
		 */
		@Test
		@DisplayName("Reading twice gives the same answer.")
		void repeatedReadsAgree() throws Exception {
			astAussomDoc doc = classDoc(engine(true));
			String first = docText(doc);
			int firstCount = docList(doc).getValue().size();
			List<docAnnotation> firstAnns = doc.getDocAnnotations();

			String second = docText(doc);
			int secondCount = docList(doc).getValue().size();
			List<docAnnotation> secondAnns = doc.getDocAnnotations();

			assertEquals(first, second, "Doc text changed between reads.");
			assertEquals(firstCount, secondCount, "Doc list size changed between reads.");
			assertEquals(firstAnns.size(), secondAnns.size(),
				"Annotation count changed between reads.");
		}

		@Test
		@DisplayName("toString also triggers parsing.")
		void toStringParses() throws Exception {
			String s = classDoc(engine(true)).toString(0);
			assertTrue(s.contains("A greeter."), "Got: " + s);
		}
	}

	/* ============================================================ */

	@Nested
	@DisplayName("aussomdoc.retain")
	class Retain {

		@Test
		@DisplayName("Defaults to true, so docs are kept unless a host says otherwise.")
		void defaultsToTrue() throws Exception {
			Engine eng = new Engine(new TestSecurityManagerImpl());
			eng.parseString("doctest.aus", SRC);
			assertTrue(docText(classDoc(eng)).contains("A greeter."),
				"Doc text should be kept when nothing sets aussomdoc.retain.");
		}

		@Test
		@DisplayName("False drops the text but still attaches a doc node.")
		void falseKeepsTheNode() throws Exception {
			Engine eng = engine(false);

			astClass cls = eng.getClassByName("greeter");
			assertNotNull(cls, "Expected the greeter class to parse.");
			assertNotNull(cls.getDocNode(),
				"The node must stay so getDocNode().getAussomdoc() cannot NPE.");
			assertEquals("", docText(cls.getDocNode()),
				"Doc text should be dropped when retention is off.");
			assertTrue(cls.getDocNode().getDocAnnotations().isEmpty(),
				"No annotations survive when the text is dropped.");
		}

		@Test
		@DisplayName("False does not stop the source parsing.")
		void falseStillParsesCode() throws Exception {
			Engine eng = engine(false);
			assertNotNull(eng.getClassByName("greeter"));
			assertEquals(1, eng.getClassByName("greeter").getFunctionsByName("hello").size(),
				"Dropping doc text must not affect the code around it.");
		}
	}
}

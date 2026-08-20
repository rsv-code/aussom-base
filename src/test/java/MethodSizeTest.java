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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The generated parser and lexer must stay JIT compilable.
 *
 * <p>HotSpot never compiles a method of 8000 bytecodes or more. Not at
 * any tier, not however hot it gets, for the life of the process. It
 * runs in the bytecode interpreter instead, which is roughly ten to
 * fifty times slower than compiled code. The limit is not tunable in a
 * release JVM: HugeMethodLimit is a debug only flag, and
 * -XX:-DontCompileHugeMethods is not something an embedded library can
 * ask a host to set.
 *
 * <p>This is not a hypothetical. CUP emitted every grammar action into
 * one 36341 bytecode method, and it was never compiled once in the life
 * of any process that used this parser. That cost roughly 18% of warm
 * engine startup. src/build/SplitParserActions.java now repartitions it
 * during the build, and this test is what keeps it that way as the
 * grammar grows.
 *
 * <p>The check is deterministic and takes milliseconds, which is why it
 * belongs in the JUnit suite while the timing benchmark
 * (com.aussom.StartupBench) deliberately does not.
 *
 * <p>Method sizes are read straight out of the class files rather than
 * through a bytecode library, so the suite gains no dependency. The
 * walk below agrees exactly with javap.
 *
 * <p>See design/warm-startup-cost-analysis.md and
 * design/starup-perf-improvements.md section 2.
 *
 * @author austin
 */
@DisplayName("Generated parser and lexer method sizes")
public class MethodSizeTest {

	/**
	 * A method whose bytecode reaches this length is never JIT compiled.
	 * See DontCompileHugeMethods and HugeMethodLimit in the HotSpot
	 * sources; 8000 is the value and it is not settable in a release JVM.
	 */
	private static final int HOTSPOT_HUGE_METHOD_LIMIT = 8000;

	/**
	 * Class files to check. The generated parser holds the grammar
	 * actions, and the generated lexer holds the DFA loop; both grow
	 * with the language and neither is written by hand.
	 */
	private static final String[] CLASS_FILES = {
		"com/aussom/parser.class",
		"com/aussom/parser$CUP$parser$actions.class",
		"com/aussom/Lexer.class",
	};

	@Test
	@DisplayName("No generated method reaches the 8000 bytecode JIT ceiling.")
	void generatedMethodsAreCompilable() throws IOException {
		Path classes = Paths.get("target", "classes");
		assumeTrue(Files.isDirectory(classes),
			"target/classes is not built; run mvn test rather than the test alone.");

		List<String> tooBig = new ArrayList<String>();
		int checked = 0;
		for (String rel : CLASS_FILES) {
			Path f = classes.resolve(rel);
			assertTrue(Files.isRegularFile(f), "Expected generated class file: " + f);
			for (Map.Entry<String, Integer> e : methodSizes(f).entrySet()) {
				checked++;
				if (e.getValue().intValue() >= HOTSPOT_HUGE_METHOD_LIMIT) {
					tooBig.add(rel + " " + e.getKey() + " is " + e.getValue()
						+ " bytecodes");
				}
			}
		}

		assertTrue(checked > 0, "Read no methods at all; the class files look wrong.");
		assertTrue(tooBig.isEmpty(),
			"These generated methods are at or over the " + HOTSPOT_HUGE_METHOD_LIMIT
			+ " bytecode ceiling and will never be JIT compiled:\n  "
			+ String.join("\n  ", tooBig)
			+ "\n\nIf this is the CUP action method, lower DEFAULT_CASES_PER_PART in"
			+ "\nsrc/build/SplitParserActions.java and rebuild. If it is the lexer,"
			+ "\nthe DFA in src/main/jflex/Scanner.jflex has outgrown one method and"
			+ "\nneeds its own answer. See design/starup-perf-improvements.md section 2.");
	}

	/* ============================================================
	 * Class file reading
	 * ============================================================ */

	/**
	 * Bytecode length of every method in a class file, keyed by name and
	 * descriptor. Walks the constant pool, skips the fields, then reads
	 * the Code attribute length of each method. Nothing here needs the
	 * class to be loadable, so it works on any class file the build
	 * produced.
	 * @param File is the class file to read.
	 * @return A Map of method name and descriptor to bytecode length.
	 * @throws IOException on a read failure or a malformed class file.
	 */
	private static Map<String, Integer> methodSizes(Path File) throws IOException {
		InputStream in = Files.newInputStream(File);
		try {
			DataInputStream d = new DataInputStream(in);
			if (d.readInt() != 0xCAFEBABE) {
				throw new IOException("not a class file: " + File);
			}
			d.readUnsignedShort();                      // minor version
			d.readUnsignedShort();                      // major version

			int cpCount = d.readUnsignedShort();
			String[] utf8 = new String[cpCount];
			for (int i = 1; i < cpCount; i++) {
				int tag = d.readUnsignedByte();
				if (tag == 1) {
					utf8[i] = d.readUTF();
				} else if (tag == 7 || tag == 8 || tag == 16 || tag == 19 || tag == 20) {
					d.skipBytes(2);
				} else if (tag == 15) {
					d.skipBytes(3);
				} else if (tag == 5 || tag == 6) {
					// long and double take two constant pool slots.
					d.skipBytes(8);
					i++;
				} else if (tag == 3 || tag == 4 || tag == 9 || tag == 10 || tag == 11
						|| tag == 12 || tag == 17 || tag == 18) {
					d.skipBytes(4);
				} else {
					throw new IOException("unknown constant pool tag " + tag + " in " + File);
				}
			}

			d.readUnsignedShort();                      // access flags
			d.readUnsignedShort();                      // this class
			d.readUnsignedShort();                      // super class
			d.skipBytes(d.readUnsignedShort() * 2);     // interfaces
			skipMembers(d);                             // fields

			Map<String, Integer> out = new LinkedHashMap<String, Integer>();
			int methods = d.readUnsignedShort();
			for (int i = 0; i < methods; i++) {
				d.readUnsignedShort();                  // access flags
				String name = utf8[d.readUnsignedShort()];
				String desc = utf8[d.readUnsignedShort()];
				int attrs = d.readUnsignedShort();
				int codeLen = 0;
				for (int a = 0; a < attrs; a++) {
					String an = utf8[d.readUnsignedShort()];
					int alen = d.readInt();
					if ("Code".equals(an)) {
						d.readUnsignedShort();          // max stack
						d.readUnsignedShort();          // max locals
						codeLen = d.readInt();
						d.skipBytes(alen - 8);
					} else {
						d.skipBytes(alen);
					}
				}
				out.put(name + desc, Integer.valueOf(codeLen));
			}
			return out;
		} finally {
			in.close();
		}
	}

	private static void skipMembers(DataInputStream d) throws IOException {
		int n = d.readUnsignedShort();
		for (int i = 0; i < n; i++) {
			d.skipBytes(6);                             // flags, name, descriptor
			int attrs = d.readUnsignedShort();
			for (int a = 0; a < attrs; a++) {
				d.readUnsignedShort();                  // attribute name
				d.skipBytes(d.readInt());
			}
		}
	}
}

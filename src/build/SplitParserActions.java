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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits the CUP generated action method into several smaller ones so
 * HotSpot will compile them.
 *
 * <p><b>Why this exists.</b> A method of 8000 bytecodes or more is never
 * JIT compiled, at any tier, for the life of the process. CUP emits
 * every grammar action into one dispatch method and splits it only when
 * a grammar has more productions than java_cup.emit.UPPERLIMIT, which is
 * 300 and is a compile time constant in the generator with no command
 * line option. This grammar has 226 productions, so it lands in a single
 * method of about 36000 bytecodes that runs interpreted forever.
 *
 * <p><b>Why the actions themselves cannot be shrunk instead.</b>
 * Measured: replacing every action body in the grammar with
 * {@code RESULT = null;} still leaves about 27800 bytecodes, and doing
 * that <i>and</i> generating with -nopositions still leaves about 13700.
 * Roughly 77% of the method is CUP's own per case emission -- the right
 * hand side operand extraction, the position variables, and the
 * newSymbol call -- none of which a grammar rewrite can remove. No
 * change to aussom.cup can bring one method with 226 productions under
 * the limit. Splitting is the only fix. See
 * design/warm-startup-cost-analysis.md and
 * design/starup-perf-improvements.md section 2.
 *
 * <p><b>What it does.</b> Nothing to the action code. It repartitions
 * the same case blocks across several methods with the same signature
 * and rewrites the generated dispatcher to route by action number, which
 * is the shape CUP itself emits when it does split.
 *
 * <p>Run from the build between cup:generate and compile; see the
 * exec-maven-plugin execution in pom.xml. Safe to run by hand: handed an
 * already split file it reports that and changes nothing.
 *
 * <p>It fails loudly rather than quietly doing nothing, because a silent
 * no-op would hand back the huge method with no signal. MethodSizeTest
 * is the backstop that catches it either way.
 *
 * @author austin
 */
public final class SplitParserActions {

	/**
	 * Case blocks per generated method.
	 *
	 * Measured over this grammar: 40 gives 6 parts with the largest at
	 * 7955 bytecodes, which clears the 8000 limit by 45 bytes and one
	 * fat production would erase. 25 gives 9 parts with the largest at
	 * 5394, leaving room for the grammar to grow without anyone having
	 * to think about it. Lower this if MethodSizeTest ever fails.
	 */
	private static final int DEFAULT_CASES_PER_PART = 25;

	/** The single part CUP emits for a grammar this size. */
	private static final String FIRST_PART = "do_action_part00000000";

	/** Marks a file this tool has already processed. */
	private static final String SECOND_PART = "do_action_part00000001";

	private static final String END_OF_METHOD = "    } /* end of method */";

	/**
	 * Notice stamped onto the generated parser.
	 *
	 * CUP does not disclaim its output the way JFlex does. Its license
	 * says the portions of CUP output hard-coded into the CUP source are
	 * covered by the CUP license, so this file is part Aussom grammar
	 * actions under Apache 2.0 and part CUP boilerplate under the CUP
	 * license, and the CUP notice has to travel with it. See
	 * THIRD-PARTY.md.
	 */
	private static final String CUP_NOTICE =
		  "/*\n"
		+ " * GENERATED FILE. Do not edit; edit src/main/cup/aussom.cup instead.\n"
		+ " *\n"
		+ " * The grammar actions in this file are generated from aussom.cup and\n"
		+ " * are licensed under the Apache License, Version 2.0, like the rest of\n"
		+ " * Aussom. The surrounding parser boilerplate is emitted by the CUP\n"
		+ " * Parser Generator and remains under the CUP license:\n"
		+ " *\n"
		+ " *   CUP Parser Generator Copyright Notice, License, and Disclaimer\n"
		+ " *   Copyright 1996-2015 by Scott Hudson, Frank Flannery,\n"
		+ " *   C. Scott Ananian, Michael Petter\n"
		+ " *\n"
		+ " *   Permission to use, copy, modify, and distribute this software and\n"
		+ " *   its documentation for any purpose and without fee is hereby\n"
		+ " *   granted, provided that the above copyright notice appear in all\n"
		+ " *   copies and that both the copyright notice and this permission\n"
		+ " *   notice and warranty disclaimer appear in supporting documentation.\n"
		+ " *\n"
		+ " *   Full text and disclaimer:\n"
		+ " *   https://github.com/DrMichaelPetter/cup/blob/master/licence.txt\n"
		+ " *   and META-INF/THIRD-PARTY-NOTICES.txt in the built jar.\n"
		+ " */\n";

	private static final String DISPATCH_DOC =
		"  /** Method splitting the generated action code into several parts. */";

	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			fail("usage: SplitParserActions <parser.java> [casesPerPart]");
		}
		Path file = Paths.get(args[0]);
		int perPart = DEFAULT_CASES_PER_PART;
		if (args.length > 1) {
			perPart = Integer.parseInt(args[1]);
		}
		if (perPart < 1) {
			fail("casesPerPart must be at least 1, got " + perPart);
		}

		if (!Files.isRegularFile(file)) {
			fail("no such file: " + file);
		}
		String src = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

		if (src.contains(SECOND_PART)) {
			System.out.println("SplitParserActions: " + file
				+ " is already split, nothing to do.");
			return;
		}

		String out = split(src, perPart, file.toString());
		if (!out.startsWith("/*\n * GENERATED FILE.")) {
			out = CUP_NOTICE + out;
		}
		Files.write(file, out.getBytes(StandardCharsets.UTF_8));
	}

	private static String split(String src, int perPart, String name) {
		/*
		 * The generated part, from its javadoc line through the open
		 * brace of the method body. Everything about this shape comes
		 * from java_cup.emit; if any of it stops matching, the generator
		 * changed and this tool must be revisited rather than guessed at.
		 */
		Pattern head = Pattern.compile(
			"( *)/\\*\\* Method 0 with the actual generated action code[^\\n]*\\n"
			+ "( *public final java_cup\\.runtime\\.Symbol (CUP\\$\\w+\\$)"
			+ FIRST_PART + "\\([^)]*\\)\\s*\\n"
			+ " *throws java\\.lang\\.Exception\\s*\\n"
			+ " *\\{\\n)", Pattern.DOTALL);
		Matcher m = head.matcher(src);
		if (!m.find()) {
			fail("could not find the generated action part in " + name
				+ ". The CUP generator's output shape changed; see this file's javadoc.");
		}
		String signature = m.group(2);
		String prefix = m.group(3);

		int bodyStart = m.end();
		int bodyEnd = src.indexOf(END_OF_METHOD, bodyStart);
		if (bodyEnd < 0) {
			fail("could not find the end of the action part in " + name + ".");
		}
		String body = src.substring(bodyStart, bodyEnd);

		int switchAt = body.indexOf("switch (" + prefix + "act_num)");
		if (switchAt < 0) {
			fail("could not find the action switch in " + name + ".");
		}
		String prologue = body.substring(0, switchAt);
		String afterSwitch = body.substring(switchAt);
		int braceAt = afterSwitch.indexOf('{');
		if (braceAt < 0) {
			fail("could not find the switch body in " + name + ".");
		}
		String switchOpen = afterSwitch.substring(0, braceAt + 1);
		String rest = afterSwitch.substring(braceAt + 1);

		int defaultAt = rest.indexOf("          default:");
		if (defaultAt < 0) {
			fail("could not find the switch default in " + name + ".");
		}
		// Keep the separator comment that precedes default: with it.
		int sepAt = rest.lastIndexOf("/*", defaultAt);
		int cutAt = defaultAt;
		if (sepAt > 0 && rest.substring(sepAt, defaultAt).trim().endsWith("*/")) {
			cutAt = rest.lastIndexOf('\n', sepAt) + 1;
		}
		String casesText = rest.substring(0, cutAt);
		String defaultText = rest.substring(cutAt);

		List<Block> blocks = cases(casesText, name);
		List<List<Block>> parts = group(blocks, perPart);

		StringBuilder methods = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			List<Block> grp = parts.get(i);
			methods.append("  /** Actions ").append(grp.get(0).number)
				.append(" to ").append(grp.get(grp.size() - 1).number)
				.append(". Split from CUP's single part so HotSpot will compile it;\n")
				.append("      see src/build/SplitParserActions.java. */\n");
			methods.append(signature.replace(FIRST_PART, partName(i)));
			methods.append(prologue);
			methods.append(switchOpen);
			for (Block b : grp) {
				methods.append(b.text);
			}
			methods.append(defaultText);
			methods.append(END_OF_METHOD).append("\n\n");
		}

		StringBuilder dispatch = new StringBuilder();
		dispatch.append(DISPATCH_DOC).append("\n");
		dispatch.append("  public final java_cup.runtime.Symbol ").append(prefix)
			.append("do_action(\n");
		dispatch.append("    int                        ").append(prefix).append("act_num,\n");
		dispatch.append("    java_cup.runtime.lr_parser ").append(prefix).append("parser,\n");
		dispatch.append("    java.util.Stack            ").append(prefix).append("stack,\n");
		dispatch.append("    int                        ").append(prefix).append("top)\n");
		dispatch.append("    throws java.lang.Exception\n");
		dispatch.append("    {\n");
		for (int i = 0; i < parts.size() - 1; i++) {
			List<Block> grp = parts.get(i);
			dispatch.append("      if (").append(prefix).append("act_num <= ")
				.append(grp.get(grp.size() - 1).number).append(")\n");
			dispatch.append("        return ").append(call(prefix, partName(i)));
		}
		dispatch.append("      return ").append(call(prefix, partName(parts.size() - 1)));
		dispatch.append("    }\n");

		// Replace CUP's own dispatcher, which follows the part.
		int dispatchAt = src.indexOf(DISPATCH_DOC, bodyEnd);
		if (dispatchAt < 0) {
			fail("could not find the generated dispatcher in " + name + ".");
		}
		int dispatchEnd = src.indexOf("\n    }\n", dispatchAt);
		if (dispatchEnd < 0) {
			fail("could not find the end of the generated dispatcher in " + name + ".");
		}
		dispatchEnd += "\n    }\n".length();

		System.out.println("SplitParserActions: " + blocks.size() + " actions into "
			+ parts.size() + " methods of at most " + perPart + " in " + name);

		return src.substring(0, m.start()) + methods + dispatch + src.substring(dispatchEnd);
	}

	/**
	 * A call to one part. The method name carries the same
	 * CUP$parser$ prefix the generator puts on everything it emits, so
	 * the prefix goes on the name as well as on the arguments.
	 */
	private static String call(String prefix, String method) {
		return prefix + method + "(" + prefix + "act_num, " + prefix + "parser, "
			+ prefix + "stack, " + prefix + "top);\n";
	}

	private static String partName(int index) {
		return String.format("do_action_part%08d", index);
	}

	/** One case block, from its "case N:" line to the start of the next. */
	private static final class Block {
		final int number;
		final String text;

		Block(int Number, String Text) {
			this.number = Number;
			this.text = Text;
		}
	}

	private static List<Block> cases(String casesText, String name) {
		Pattern p = Pattern.compile("^ +case (\\d+): //", Pattern.MULTILINE);
		Matcher m = p.matcher(casesText);
		List<Integer> starts = new ArrayList<Integer>();
		List<Integer> numbers = new ArrayList<Integer>();
		while (m.find()) {
			starts.add(Integer.valueOf(m.start()));
			numbers.add(Integer.valueOf(Integer.parseInt(m.group(1))));
		}
		if (starts.isEmpty()) {
			fail("found no case blocks in " + name + ".");
		}
		List<Block> out = new ArrayList<Block>();
		for (int i = 0; i < starts.size(); i++) {
			int from = starts.get(i).intValue();
			int to = casesText.length();
			if (i + 1 < starts.size()) {
				to = starts.get(i + 1).intValue();
			}
			out.add(new Block(numbers.get(i).intValue(), casesText.substring(from, to)));
		}
		/*
		 * The dispatcher routes by "act_num <= last of this part", which
		 * is only correct while the numbers ascend. CUP emits them in
		 * order; check rather than trust, because a wrong route here
		 * would run the wrong action rather than fail.
		 */
		for (int i = 1; i < out.size(); i++) {
			if (out.get(i).number <= out.get(i - 1).number) {
				fail("case numbers are not ascending in " + name
					+ " (" + out.get(i - 1).number + " then " + out.get(i).number
					+ "); the range dispatch would be wrong.");
			}
		}
		return out;
	}

	private static List<List<Block>> group(List<Block> blocks, int perPart) {
		List<List<Block>> out = new ArrayList<List<Block>>();
		for (int i = 0; i < blocks.size(); i += perPart) {
			out.add(new ArrayList<Block>(
				blocks.subList(i, Math.min(i + perPart, blocks.size()))));
		}
		return out;
	}

	private static void fail(String message) {
		System.err.println("SplitParserActions: " + message);
		System.exit(1);
	}
}

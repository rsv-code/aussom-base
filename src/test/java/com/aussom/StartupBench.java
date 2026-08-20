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

package com.aussom;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aussom.stdlib.LangRegistry;
import com.sun.management.ThreadMXBean;

/**
 * Warm-startup benchmark for an Aussom Engine.
 *
 * <p>This is a main class, not a JUnit test, and it is deliberately
 * absent from the Surefire include list in pom.xml: timing is noisy and
 * machine dependent, so it must not decide whether a build passes. Run
 * it on demand with run-startup-bench.sh.
 *
 * <p>Unlike every other test source in this project it declares a
 * package rather than sitting in the default one. That is not a
 * preference: Lexer is package private (see Lexer.java, "class Lexer
 * extends sym"), as are its constructors, so a benchmark that measures
 * the lexer and the parser separately has to live in com.aussom.
 *
 * <p>What it measures, in phases, so a regression can be attributed
 * rather than just noticed:
 *
 * <ul>
 * <li><b>A shell</b> - Engine construction over a registry whose
 *     lang.aus is a one line stub. Pays every fixed construction cost
 *     and parses nothing.</li>
 * <li><b>B full</b> - Engine construction with the real standard
 *     library. This is warm startup, and it is the headline number.</li>
 * <li><b>C shell+lex</b> - A, plus draining the Lexer over lang.aus
 *     with no parser attached.</li>
 * <li><b>D shell+parse</b> - A, plus a full parser.parse() over
 *     lang.aus.</li>
 * <li><b>E retained</b> - bytes of heap held by one live engine,
 *     measured by holding many and settling the collector. This is a
 *     memory number rather than a time number, and it is the only phase
 *     that can see a retention change.</li>
 * </ul>
 *
 * <p>Derived from those: lex is C - A, parse is D - A, and whatever
 * construction does after parsing is B - D.
 *
 * <p><b>Read the minimum, not the mean.</b> For a JIT warmed phase the
 * minimum is the least noisy estimator; the mean is dominated by GC
 * pauses and by whatever else the machine is doing. p90 is printed as a
 * noise indicator: if it sits far from the minimum the run was dirty and
 * the numbers should not be recorded.
 *
 * <p>See design/warm-startup-cost-analysis.md for how these phases were
 * arrived at, design/starup-perf-improvements.md for what they are used
 * to justify, and design/startup-perf-improvements-measurements.md for
 * the recorded runs.
 *
 * @author austin
 */
public class StartupBench {

	/** Iterations run before measurement starts, to let the JIT settle. */
	private static final int WARMUP = 80;

	/** Iterations actually measured. */
	private static final int MEASURE = 80;

	/** Live engines held at once by the retained heap phase. */
	private static final int HELD_ENGINES = 60;

	/**
	 * Default regression tolerance for --check, as a fraction. Wide
	 * enough to survive ordinary machine noise and narrow enough to
	 * catch a real regression.
	 */
	private static final double DEFAULT_TOLERANCE = 0.25;

	/**
	 * Source parsed by every phase, the real lang.aus taken from a seed
	 * engine's registry rather than read off disk, so the benchmark
	 * measures what an embedder actually pays for.
	 */
	private static String SRC;

	/**
	 * A registry whose lang.aus is a stub. The shell phase builds
	 * engines over this, so subtracting it isolates the parse.
	 */
	private static LangRegistry STUB;

	/**
	 * Engine.initComplete, cleared reflectively on each shell engine.
	 *
	 * Engine.addClass instantiates a static class as soon as the grammar
	 * action registers it, but only once initComplete is set, and the
	 * real constructor parses with it still false. Without clearing it
	 * the parse phase walks a different path from the constructor and
	 * throws. A package private test hook on Engine would read better
	 * than reflection; reflection is the smaller change and keeps the
	 * benchmark out of the production API.
	 */
	private static Field INIT_COMPLETE;

	/**
	 * Whether the engines built here retain doc comment text, so a run
	 * can measure what aussomdoc.retain buys. Set with --no-doc-retain;
	 * the default matches what an embedder gets out of the box.
	 */
	private static boolean docRetain = true;

	public static void main(String[] rawArgs) throws Exception {
		List<String> args = new ArrayList<String>(Arrays.asList(rawArgs));
		if (args.remove("--no-doc-retain")) { docRetain = false; }

		INIT_COMPLETE = Engine.class.getDeclaredField("initComplete");
		INIT_COMPLETE.setAccessible(true);

		Engine seed = new Engine(secman());
		SRC = seed.getLangRegistry().get("lang.aus");
		STUB = new LangRegistry();
		STUB.put("lang.aus", "class _benchShell_ { public noop() { return 1; } }");

		Map<String, Result> results = runAll();
		report(results, System.out);

		String mode = "";
		if (args.size() > 0) mode = args.get(0);

		if (mode.equals("--baseline")) {
			if (args.size() < 2) { fail("--baseline needs a file path"); }
			writeBaseline(results, args.get(1));
			System.out.println();
			System.out.println("Baseline written to " + args.get(1));
		} else if (mode.equals("--check")) {
			if (args.size() < 2) { fail("--check needs a file path"); }
			double tol = DEFAULT_TOLERANCE;
			if (args.size() > 2) { tol = Double.parseDouble(args.get(2)); }
			System.exit(check(results, args.get(1), tol));
		} else if (!mode.isEmpty()) {
			fail("unknown option '" + mode + "'. Use --baseline <file> or --check <file> "
				+ "[tolerance], optionally with --no-doc-retain");
		}
	}

	/**
	 * The security manager every engine here is built with. Doc
	 * retention is the only thing it changes from the default, so a run
	 * with --no-doc-retain differs from one without in exactly that.
	 *
	 * Reaches the property map directly rather than through a setter,
	 * which is what a host does too: there is no Java side setter on
	 * SecurityManagerImpl on purpose, and props is protected so a
	 * benchmark in this package can write it without new API. See
	 * LimitSecMan in the test sources for the same approach.
	 */
	private static DefaultSecurityManagerImpl secman() {
		DefaultSecurityManagerImpl sm = new DefaultSecurityManagerImpl();
		if (!docRetain) { sm.props.put("aussomdoc.retain", Boolean.FALSE); }
		return sm;
	}

	/* ============================================================
	 * Phases
	 * ============================================================ */

	private static Map<String, Result> runAll() throws Exception {
		Map<String, Result> out = new LinkedHashMap<String, Result>();
		out.put("A shell", time(new Phase() {
			public void once() throws Exception { shell(); }
		}));
		out.put("B full", time(new Phase() {
			public void once() throws Exception { new Engine(secman()); }
		}));
		out.put("C shell+lex", time(new Phase() {
			public void once() throws Exception { shell(); lex(); }
		}));
		out.put("D shell+parse", time(new Phase() {
			public void once() throws Exception { parse(); }
		}));
		out.put("E retained", retained());
		return out;
	}

	private interface Phase {
		void once() throws Exception;
	}

	/**
	 * Builds an engine over the stub registry and returns it to the
	 * pre-init state, so parsing into it takes exactly the path the real
	 * constructor takes. See INIT_COMPLETE.
	 */
	private static Engine shell() throws Exception {
		Engine e = new Engine(secman(), STUB);
		INIT_COMPLETE.setBoolean(e, false);
		return e;
	}

	private static void lex() throws Exception {
		Lexer l = new Lexer(new StringReader(SRC), "lang.aus");
		while (l.next_token().sym != sym.EOF) { }
	}

	private static void parse() throws Exception {
		Engine e = shell();
		Lexer l = new Lexer(new StringReader(SRC), "lang.aus");
		new parser(l, e, "lang.aus", false).parse();
	}

	private static final ThreadMXBean TMX =
		(ThreadMXBean) ManagementFactory.getThreadMXBean();

	private static Result time(Phase p) throws Exception {
		for (int i = 0; i < WARMUP; i++) { p.once(); }

		long id = Thread.currentThread().getId();
		long allocBefore = TMX.getThreadAllocatedBytes(id);
		long[] samples = new long[MEASURE];
		for (int i = 0; i < MEASURE; i++) {
			long t0 = System.nanoTime();
			p.once();
			samples[i] = System.nanoTime() - t0;
		}
		long alloc = (TMX.getThreadAllocatedBytes(id) - allocBefore) / MEASURE;

		Arrays.sort(samples);
		Result r = new Result();
		r.minMs = samples[0] / 1e6;
		r.p50Ms = samples[MEASURE / 2] / 1e6;
		r.p90Ms = samples[(int) (MEASURE * 0.9)] / 1e6;
		r.allocBytes = alloc;
		return r;
	}

	/**
	 * Heap held by one live engine. Engines are constructed and kept
	 * referenced, so what the collector cannot reclaim is what an engine
	 * retains. Engine.measureRetainedFootprint is not used here: it
	 * walks runtime values and does not visit the AST, so it cannot see
	 * a change in what the parse holds.
	 */
	private static Result retained() throws Exception {
		for (int i = 0; i < 20; i++) { new Engine(secman()); }

		long before = settledHeap();
		List<Engine> held = new ArrayList<Engine>();
		for (int i = 0; i < HELD_ENGINES; i++) {
			held.add(new Engine(secman()));
		}
		long after = settledHeap();

		Result r = new Result();
		r.retainedBytes = (after - before) / held.size();
		return r;
	}

	/**
	 * Used heap once repeated collection stops moving it, so the number
	 * reflects what is reachable rather than what has not been collected
	 * yet.
	 */
	private static long settledHeap() throws Exception {
		long last = -1L;
		for (int i = 0; i < 12; i++) {
			System.gc();
			Thread.sleep(60L);
			Runtime rt = Runtime.getRuntime();
			long used = rt.totalMemory() - rt.freeMemory();
			if (last >= 0L && Math.abs(used - last) < 64L * 1024L) { return used; }
			last = used;
		}
		return last;
	}

	/* ============================================================
	 * Reporting
	 * ============================================================ */

	private static class Result {
		double minMs;
		double p50Ms;
		double p90Ms;
		long allocBytes;
		long retainedBytes = -1L;
	}

	private static void report(Map<String, Result> r, PrintStream out) {
		out.println("Aussom warm startup benchmark");
		out.println("  jdk        : " + System.getProperty("java.version")
			+ " (" + System.getProperty("java.vm.name") + ")");
		out.println("  cpus       : " + Runtime.getRuntime().availableProcessors());
		out.println("  max heap   : " + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " MB");
		out.println("  aussom     : " + Engine.getAussomVersion());
		out.println("  lang.aus   : " + SRC.length() + " chars");
		out.println("  doc retain : " + docRetain);
		out.println("  iterations : " + WARMUP + " warmup, " + MEASURE + " measured");
		out.println();

		for (Map.Entry<String, Result> e : r.entrySet()) {
			Result v = e.getValue();
			if (v.retainedBytes >= 0L) {
				out.printf("  %-16s %s retained per engine%n",
					e.getKey(), kb(v.retainedBytes));
			} else {
				out.printf("  %-16s min %7.3f  p50 %7.3f  p90 %7.3f ms   alloc %s%n",
					e.getKey(), v.minMs, v.p50Ms, v.p90Ms, kb(v.allocBytes));
			}
		}

		double a = r.get("A shell").minMs;
		out.println();
		out.printf("  lex        = C - A = %7.3f ms%n", r.get("C shell+lex").minMs - a);
		out.printf("  parse      = D - A = %7.3f ms%n", r.get("D shell+parse").minMs - a);
		out.printf("  post-parse = B - D = %7.3f ms%n",
			r.get("B full").minMs - r.get("D shell+parse").minMs);
	}

	private static String kb(long bytes) {
		return String.format("%,10.1f KB", bytes / 1024.0);
	}

	/* ============================================================
	 * Baseline and check
	 * ============================================================ */

	private static void writeBaseline(Map<String, Result> r, String path) throws IOException {
		File f = new File(path);
		if (f.getParentFile() != null) { f.getParentFile().mkdirs(); }
		PrintStream out = new PrintStream(new FileOutputStream(f), true, "UTF-8");
		try {
			report(r, out);
			out.println();
			out.println("# machine readable, read by --check");
			for (Map.Entry<String, Result> e : r.entrySet()) {
				Result v = e.getValue();
				if (v.retainedBytes >= 0L) {
					out.println("KEY\t" + e.getKey() + "\tretained\t" + v.retainedBytes);
				} else {
					out.println("KEY\t" + e.getKey() + "\tmin_ns\t"
						+ (long) (v.minMs * 1e6) + "\talloc\t" + v.allocBytes);
				}
			}
		} finally {
			out.close();
		}
	}

	/**
	 * Compares the current run against a stored baseline and reports
	 * anything that regressed by more than the tolerance.
	 * @return 0 when nothing regressed, 1 when something did.
	 */
	private static int check(Map<String, Result> now, String path, double tol) throws IOException {
		Map<String, Double> base = readBaseline(path);
		System.out.println();
		System.out.println("Compared against " + path + " at " + (int) (tol * 100) + "% tolerance:");

		boolean bad = false;
		for (Map.Entry<String, Result> e : now.entrySet()) {
			Double b = base.get(e.getKey());
			if (b == null) {
				System.out.println("  " + e.getKey() + ": not in baseline, skipped");
				continue;
			}
			Result v = e.getValue();
			double was = b.doubleValue();
			double is = v.minMs;
			String unit = "ms";
			if (v.retainedBytes >= 0L) {
				was = b.doubleValue() / 1024.0;
				is = v.retainedBytes / 1024.0;
				unit = "KB";
			}
			double delta = (is - was) / was;
			String verdict = "ok";
			if (delta > tol) { verdict = "REGRESSED"; bad = true; }
			System.out.printf("  %-16s %8.3f -> %8.3f %s  (%+.1f%%)  %s%n",
				e.getKey(), was, is, unit, delta * 100.0, verdict);
		}

		if (bad) {
			System.out.println();
			System.out.println("At least one phase regressed. If the change was intended,");
			System.out.println("re-record with --baseline " + path + ".");
			return 1;
		}
		return 0;
	}

	private static Map<String, Double> readBaseline(String path) throws IOException {
		Map<String, Double> out = new LinkedHashMap<String, Double>();
		BufferedReader in = new BufferedReader(
			new InputStreamReader(new FileInputStream(path), "UTF-8"));
		try {
			String line;
			while ((line = in.readLine()) != null) {
				if (!line.startsWith("KEY\t")) continue;
				String[] p = line.split("\t");
				if (p.length < 4) continue;
				if (p[2].equals("retained")) {
					out.put(p[1], Double.valueOf(Double.parseDouble(p[3])));
				} else {
					out.put(p[1], Double.valueOf(Double.parseDouble(p[3]) / 1e6));
				}
			}
		} finally {
			in.close();
		}
		return out;
	}

	private static void fail(String msg) {
		System.err.println("StartupBench: " + msg);
		System.exit(2);
	}
}

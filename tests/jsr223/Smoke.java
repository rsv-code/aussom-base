/*
 * JSR 223 smoke test for the Aussom scripting engine.
 *
 * Compile and run from the project root:
 *
 *   javac -cp target/aussom.base-1.2.2-jar-with-dependencies.jar tests/jsr223/Smoke.java -d tests/jsr223
 *   java  -cp target/aussom.base-1.2.2-jar-with-dependencies.jar:tests/jsr223 Smoke
 */

import javax.script.*;
import java.io.StringWriter;
import java.util.Arrays;

public class Smoke {
	private static int passed = 0;
	private static int failed = 0;

	public static void main(String[] args) throws Exception {
		ScriptEngineManager mgr = new ScriptEngineManager();

		// 1. Discovery via SPI.
		ScriptEngine eng = mgr.getEngineByName("aussom");
		check("getEngineByName('aussom')", eng != null);
		check("getEngineByExtension('aus')", mgr.getEngineByExtension("aus") != null);
		check("getEngineByMimeType('application/x-aussom')",
			mgr.getEngineByMimeType("application/x-aussom") != null);

		// 2. Factory metadata.
		ScriptEngineFactory f = eng.getFactory();
		check("factory engine name", "Aussom Scripting Engine".equals(f.getEngineName()));
		check("factory language", "Aussom".equals(f.getLanguageName()));
		check("factory THREADING", "MULTITHREADED".equals(f.getParameter("THREADING")));
		check("factory NAME",      "aussom".equals(f.getParameter(ScriptEngine.NAME)));
		check("factory extensions contains 'aus'", f.getExtensions().contains("aus"));
		check("getMethodCallSyntax", "o.m(a, b)".equals(f.getMethodCallSyntax("o","m","a","b")));

		// 3. Trivial eval, return value.
		Object r = eng.eval("return 1 + 2;");
		check("1 + 2 returns 3 (Long)", Long.valueOf(3L).equals(r));

		// 4. Bindings IN.
		eng.put("name", "world");
		r = eng.eval("return \"hello \" + this.bindings[\"name\"] + \"!\";");
		check("bindings flow in", "hello world!".equals(r));

		// 5. Bindings OUT.
		eng.put("counter", 10L);
		eng.eval("this.bindings[\"counter\"] = this.bindings[\"counter\"] + 5;");
		Object c = eng.get("counter");
		check("bindings write back", Long.valueOf(15L).equals(c));

		// 6. Marshalling: list, map, double, bool.
		eng.put("nums", Arrays.asList(1L, 2L, 3L));
		r = eng.eval(
			"xs = this.bindings[\"nums\"];\n" +
			"sum = 0;\n" +
			"for (v : xs) { sum = sum + v; }\n" +
			"return sum;\n");
		check("list marshalling sum", Long.valueOf(6L).equals(r));

		// 7. ScriptContext writer routing.
		StringWriter buf = new StringWriter();
		eng.getContext().setWriter(buf);
		eng.eval("c.log(\"hello-from-aussom\");");
		check("c.log routes through ScriptContext writer",
			buf.toString().contains("hello-from-aussom"));
		eng.getContext().setWriter(new java.io.PrintWriter(System.out));

		// 8. Compilable: compile once, run many.
		Compilable comp = (Compilable) eng;
		CompiledScript cs = comp.compile("return this.bindings[\"x\"] * this.bindings[\"x\"];");
		eng.put("x", 7L);
		check("compiled eval x=7 -> 49", Long.valueOf(49L).equals(cs.eval()));
		eng.put("x", 9L);
		check("compiled eval x=9 -> 81", Long.valueOf(81L).equals(cs.eval()));

		// 9. Invocable.invokeFunction on a freshly compiled class.
		eng.eval("class Calc { public add(int a, int b) { return a + b; } }");
		Invocable inv = (Invocable) eng;
		Object sum = inv.invokeFunction("add", 2L, 3L);
		check("invokeFunction add(2,3) -> 5", Long.valueOf(5L).equals(sum));

		// 10. Errors: bad parse should throw ScriptException.
		try {
			eng.eval("class @@@ broken {");
			check("parse error throws ScriptException", false);
		} catch (ScriptException expected) {
			check("parse error throws ScriptException", true);
		}

		// 11. Concurrent eval (THREADING == MULTITHREADED).
		Thread[] ts = new Thread[8];
		final int[] results = new int[ts.length];
		for (int i = 0; i < ts.length; i++) {
			final int idx = i;
			ts[i] = new Thread(() -> {
				try {
					Bindings b = eng.createBindings();
					b.put("n", (long) (idx + 1));
					Object out = eng.eval("return this.bindings[\"n\"] * 10;",
						new SimpleScriptContext() {{
							setBindings(b, ScriptContext.ENGINE_SCOPE);
						}});
					results[idx] = ((Long) out).intValue();
				} catch (Exception e) {
					results[idx] = -1;
				}
			});
		}
		for (Thread t : ts) t.start();
		for (Thread t : ts) t.join();
		boolean allOk = true;
		for (int i = 0; i < ts.length; i++) {
			if (results[i] != (i + 1) * 10) { allOk = false; break; }
		}
		check("concurrent eval x 8 threads", allOk);

		System.out.println();
		System.out.println("PASSED " + passed + "  FAILED " + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	static void check(String name, boolean ok) {
		if (ok) {
			passed++;
			System.out.println("  OK   " + name);
		} else {
			failed++;
			System.out.println("  FAIL " + name);
		}
	}
}

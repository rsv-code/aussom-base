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

package com.aussom.script;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import com.aussom.CallStack;
import com.aussom.Engine;
import com.aussom.Environment;
import com.aussom.LoggingInt;
import com.aussom.ast.astClass;
import com.aussom.ast.astFunctDef;
import com.aussom.ast.aussomException;
import com.aussom.stdlib.console;
import com.aussom.types.AussomException;
import com.aussom.types.AussomList;
import com.aussom.types.AussomMap;
import com.aussom.types.AussomNull;
import com.aussom.types.AussomObject;
import com.aussom.types.AussomType;
import com.aussom.types.Members;

/**
 * JSR 223 ScriptEngine for Aussom. Wraps one com.aussom.Engine and
 * exposes it through the standard javax.script contract: eval,
 * Compilable, Invocable, ScriptContext bindings.
 *
 * Threading: advertises THREADING = "MULTITHREADED". The parse path
 * is serialized on a per-engine lock; the run path builds a fresh
 * Environment + CallStack + Members per call so concurrent evals do
 * not share mutable state. Console output is routed through the
 * per-thread ThreadLocal in com.aussom.stdlib.console.
 */
public class AussomScriptEngine extends AbstractScriptEngine
		implements Compilable, Invocable {

	/**
	 * Matches an Aussom top-level class declaration: optional
	 * modifiers (static, extern, ...) followed by 'class Name'. Used
	 * to decide whether the user already supplied a full program or
	 * just a snippet that needs the synthetic main wrapper.
	 */
	private static final Pattern HAS_TOP_CLASS =
		Pattern.compile("(?m)^\\s*(?:[a-zA-Z]+\\s+)*class\\s+\\w+");

	private final ScriptEngineFactory factory;
	private final Engine engine;

	/** Serializes parse-path mutations on the underlying Engine. */
	private final Object parseLock = new Object();

	/**
	 * Per-engine counter used to mint unique synthetic class names.
	 * Atomic so two threads asking the engine to compile distinct
	 * snippets never pick the same name.
	 */
	private final AtomicInteger synthCounter = new AtomicInteger(0);

	/**
	 * Last synthetic class compiled by an eval-of-source call. Used
	 * by Invocable.invokeFunction when the host runs the typical
	 * "eval then call" shape and we want to look in the most-recent
	 * snippet first.
	 */
	private volatile String lastClassName = null;

	AussomScriptEngine(ScriptEngineFactory factory, Engine engine) {
		this.factory = factory;
		this.engine = engine;
	}

	/* ------------------------------------------------------------ */
	/*  Required AbstractScriptEngine surface                       */
	/* ------------------------------------------------------------ */

	@Override
	public Object eval(String script, ScriptContext context) throws ScriptException {
		if (script == null) throw new NullPointerException("script");
		AussomCompiledScript cs = compileInternal(script);
		return runCompiled(cs.getClassName(), cs.isWrapped(), context);
	}

	@Override
	public Object eval(Reader reader, ScriptContext context) throws ScriptException {
		return eval(readAll(reader), context);
	}

	@Override
	public Bindings createBindings() {
		return new SimpleBindings();
	}

	@Override
	public ScriptEngineFactory getFactory() {
		return this.factory;
	}

	/* ------------------------------------------------------------ */
	/*  Compilable                                                  */
	/* ------------------------------------------------------------ */

	@Override
	public CompiledScript compile(String script) throws ScriptException {
		if (script == null) throw new NullPointerException("script");
		return compileInternal(script);
	}

	@Override
	public CompiledScript compile(Reader script) throws ScriptException {
		return compile(readAll(script));
	}

	private AussomCompiledScript compileInternal(String source) throws ScriptException {
		boolean wrapped = !HAS_TOP_CLASS.matcher(source).find();
		String className = "__jsr223_eval_"
			+ Integer.toHexString(System.identityHashCode(this))
			+ "_" + synthCounter.incrementAndGet();
		String parsed = wrapped ? wrapSnippet(source, className) : source;
		String fileName = wrapped ? (className + ".aus") : "<jsr223:" + className + ">";

		String runClass;
		synchronized (this.parseLock) {
			// Clear any sticky parse-error state from prior failures
			// so this compile evaluates against a clean slate.
			this.engine.clearParseError();

			Set<String> beforeClasses = new HashSet<String>(this.engine.getClasses().keySet());
			try {
				this.engine.parseString(fileName, parsed);
			} catch (aussomException ae) {
				this.engine.clearParseError();
				throw new AussomScriptException(ae, fileName);
			} catch (Exception e) {
				this.engine.clearParseError();
				ScriptException se = new ScriptException(
					"Aussom parse error: " + e.getMessage(), fileName, -1);
				se.initCause(e);
				throw se;
			}
			if (this.engine.hasParseErrors()) {
				this.engine.clearParseError();
				throw new AussomScriptException(
					"Aussom parse error in '" + fileName + "'.", fileName, -1);
			}

			// Identify the classes registered by THIS parse. For
			// snippet wrappers that's the synthetic class. For full
			// programs it's whatever the user declared. Earlier
			// classes from prior eval/compile calls are excluded so
			// findFirstMainClass-style fallbacks don't cross-talk.
			Set<String> addedClasses = new HashSet<String>(this.engine.getClasses().keySet());
			addedClasses.removeAll(beforeClasses);

			// Pre-warm just-added classes so the first concurrent
			// eval does not race on instantiateInheritedClasses
			// (audit row 3 in design/aussom-jsr-223.md section 12).
			for (String name : addedClasses) {
				preWarm(name);
			}

			if (wrapped) {
				runClass = className;
			} else {
				// Full programs: run the FIRST just-added class that
				// declares a main. If none, the parse just registered
				// classes -- there is nothing to run.
				runClass = null;
				for (String name : addedClasses) {
					astClass c = this.engine.getClassByName(name);
					if (c != null && c.hasAnyFunctionByName("main")) {
						runClass = name;
						break;
					}
				}
			}
		}

		this.lastClassName = runClass;
		return new AussomCompiledScript(this, runClass, wrapped);
	}

	private void preWarm(String className) throws ScriptException {
		astClass cls = this.engine.getClassByName(className);
		if (cls == null) return;
		try {
			Environment env = new Environment(this.engine);
			env.setEnvironment(null, new Members(), new CallStack());
			AussomType inst = cls.instantiate(env, false, new AussomList());
			if (inst.isEx()) {
				AussomException ex = (AussomException) inst;
				throw new AussomScriptException(ex, className + ".aus");
			}
		} catch (aussomException ae) {
			throw new AussomScriptException(ae, className + ".aus");
		}
	}

	/* ------------------------------------------------------------ */
	/*  Run path                                                    */
	/* ------------------------------------------------------------ */

	/**
	 * Per-call runner used by both eval(String/Reader, ScriptContext)
	 * (via compileInternal) and AussomCompiledScript.eval.
	 *
	 * Builds a fresh Environment + CallStack + Members so two
	 * concurrent calls on different threads do not share mutable
	 * state. Bindings flow in as a single 'bindings' member on the
	 * synthetic instance (wrapped path) or are skipped (full
	 * program path -- the script defines its own class).
	 */
	Object runCompiled(String className, boolean wrapped, ScriptContext context)
			throws ScriptException {
		if (className == null) {
			// Full-program parse that registered classes but had no
			// main. There is nothing to run; the side effect of
			// loading the class definitions is the value.
			return null;
		}
		astClass cls = this.engine.getClassByName(className);
		if (cls == null) {
			throw new ScriptException(
				"Aussom engine: class '" + className + "' not found.");
		}

		AussomMap inBindings = collectBindingsAsMap(context);

		LoggingInt prior = console.get().getLoggingInt();
		console.get().register(new AussomScriptContextLogger(context));
		try {
			Environment env = new Environment(this.engine);
			env.setEnvironment(null, new Members(), new CallStack());

			AussomType instTy;
			try {
				instTy = cls.instantiate(env, false, new AussomList());
			} catch (aussomException ae) {
				throw new AussomScriptException(ae, className + ".aus");
			}
			if (instTy.isEx()) {
				throw new AussomScriptException((AussomException) instTy,
					className + ".aus");
			}
			AussomObject inst = (AussomObject) instTy;

			if (wrapped) {
				inst.getMembers().getMap().put("bindings", inBindings);
			}

			env.setClassInstance(inst);
			env.setCurObj(inst);

			AussomList args = new AussomList();
			args.add(buildArgvList(context));

			AussomType ret;
			try {
				ret = cls.call(env, false, "main", args);
			} catch (aussomException ae) {
				throw new AussomScriptException(ae, className + ".aus");
			}
			if (ret == null) ret = new AussomNull();
			if (ret.isEx()) {
				throw new AussomScriptException((AussomException) ret,
					className + ".aus");
			}

			if (wrapped) {
				writeBindingsBack(inst, context);
			}

			return AussomBindingsMarshaller.fromAussom(ret);
		} finally {
			console.get().register(prior);
		}
	}

	private AussomMap collectBindingsAsMap(ScriptContext context) {
		AussomMap out = new AussomMap();
		Bindings global = context.getBindings(ScriptContext.GLOBAL_SCOPE);
		Bindings engineScope = context.getBindings(ScriptContext.ENGINE_SCOPE);
		if (global != null) {
			for (Map.Entry<String, Object> e : global.entrySet()) {
				out.put(e.getKey(), AussomBindingsMarshaller.toAussom(e.getValue()));
			}
		}
		if (engineScope != null) {
			for (Map.Entry<String, Object> e : engineScope.entrySet()) {
				out.put(e.getKey(), AussomBindingsMarshaller.toAussom(e.getValue()));
			}
		}
		return out;
	}

	private AussomList buildArgvList(ScriptContext context) {
		AussomList al = new AussomList();
		Bindings es = context.getBindings(ScriptContext.ENGINE_SCOPE);
		if (es == null) return al;
		Object argv = es.get(ScriptEngine.ARGV);
		if (argv == null) return al;
		AussomType marshalled = AussomBindingsMarshaller.toAussom(argv);
		if (marshalled instanceof AussomList) {
			return (AussomList) marshalled;
		}
		al.add(marshalled);
		return al;
	}

	/**
	 * Walks the synthetic instance's 'bindings' member after the
	 * script returns and writes any new or changed entries back into
	 * ENGINE_SCOPE. Mirrors what hosts expect when a script
	 * reassigns a binding.
	 */
	private void writeBindingsBack(AussomObject inst, ScriptContext context) {
		AussomType bm = inst.getMembers().getMap().get("bindings");
		if (!(bm instanceof AussomMap)) return;
		Bindings es = context.getBindings(ScriptContext.ENGINE_SCOPE);
		if (es == null) return;
		AussomMap am = (AussomMap) bm;
		for (Map.Entry<String, AussomType> e : am.getValue().entrySet()) {
			es.put(e.getKey(), AussomBindingsMarshaller.fromAussom(e.getValue()));
		}
	}

	/* ------------------------------------------------------------ */
	/*  Invocable                                                   */
	/* ------------------------------------------------------------ */

	@Override
	public Object invokeFunction(String name, Object... args)
			throws ScriptException, NoSuchMethodException {
		if (name == null) throw new NullPointerException("name");
		String cls = findClassWithFunction(name, args == null ? 0 : args.length);
		if (cls == null) {
			throw new NoSuchMethodException(
				"Aussom engine: no function '" + name + "' found with arity "
				+ (args == null ? 0 : args.length) + ".");
		}
		return invokeOnClass(cls, name, args);
	}

	@Override
	public Object invokeMethod(Object thiz, String name, Object... args)
			throws ScriptException, NoSuchMethodException {
		if (name == null) throw new NullPointerException("name");
		if (!(thiz instanceof AussomObject)) {
			throw new IllegalArgumentException(
				"Aussom engine: invokeMethod receiver must be an AussomObject; got "
				+ (thiz == null ? "null" : thiz.getClass().getName()));
		}
		AussomObject recv = (AussomObject) thiz;
		astClass cls = recv.getClassDef();
		if (cls == null || !cls.hasAnyFunctionByName(name)) {
			throw new NoSuchMethodException(
				"Aussom engine: method '" + name + "' not found on receiver.");
		}
		return invokeOnInstance(cls, recv, name, args);
	}

	@Override
	public <T> T getInterface(Class<T> clasz) {
		return getInterfaceImpl(null, clasz);
	}

	@Override
	public <T> T getInterface(Object thiz, Class<T> clasz) {
		if (!(thiz instanceof AussomObject)) {
			throw new IllegalArgumentException(
				"Aussom engine: getInterface receiver must be an AussomObject.");
		}
		return getInterfaceImpl((AussomObject) thiz, clasz);
	}

	@SuppressWarnings("unchecked")
	private <T> T getInterfaceImpl(final AussomObject recv, final Class<T> clasz) {
		if (clasz == null || !clasz.isInterface()) {
			throw new IllegalArgumentException(
				"Aussom engine: getInterface requires an interface class.");
		}

		// Verify every interface method has a matching Aussom method.
		for (Method m : clasz.getMethods()) {
			if (m.isDefault()) continue;
			String mname = m.getName();
			boolean found;
			if (recv != null) {
				astClass cd = recv.getClassDef();
				found = cd != null && cd.hasAnyFunctionByName(mname);
			} else {
				found = findClassWithFunction(mname, m.getParameterCount()) != null;
			}
			if (!found) return null;
		}

		Object proxy = Proxy.newProxyInstance(
			clasz.getClassLoader(),
			new Class<?>[] { clasz },
			new InvocationHandler() {
				@Override
				public Object invoke(Object p, Method m, Object[] a) throws Throwable {
					if (recv != null) {
						return invokeMethod(recv, m.getName(), a == null ? new Object[0] : a);
					}
					return invokeFunction(m.getName(), a == null ? new Object[0] : a);
				}
			});
		return (T) proxy;
	}

	/**
	 * Resolves the first class that declares an overload of `name`
	 * with an arity that fits `argc`. Lookup order:
	 *   1. lastClassName (most-recent compiled snippet).
	 *   2. Any class with a 'main' method (the program's default).
	 *   3. Any other class in the engine that declares the name.
	 */
	private String findClassWithFunction(String name, int argc) {
		String last = this.lastClassName;
		if (last != null) {
			astClass c = this.engine.getClassByName(last);
			if (c != null && classHasArity(c, name, argc)) return last;
		}
		for (String cname : this.engine.getClasses().keySet()) {
			astClass c = this.engine.getClassByName(cname);
			if (c == null) continue;
			if (!c.hasAnyFunctionByName("main")) continue;
			if (classHasArity(c, name, argc)) return cname;
		}
		for (String cname : this.engine.getClasses().keySet()) {
			astClass c = this.engine.getClassByName(cname);
			if (c == null) continue;
			if (classHasArity(c, name, argc)) return cname;
		}
		return null;
	}

	private static boolean classHasArity(astClass c, String name, int argc) {
		if (!c.hasAnyFunctionByName(name)) return false;
		List<astFunctDef> defs = c.getFunctionsByName(name);
		for (astFunctDef d : defs) {
			if (d.getMinArity() <= argc && d.getMaxArity() >= argc) return true;
		}
		return false;
	}

	private Object invokeOnClass(String className, String name, Object[] args)
			throws ScriptException {
		astClass cls = this.engine.getClassByName(className);
		if (cls == null) {
			throw new ScriptException(
				"Aussom engine: class '" + className + "' not found.");
		}
		LoggingInt prior = console.get().getLoggingInt();
		console.get().register(new AussomScriptContextLogger(getContext()));
		try {
			Environment env = new Environment(this.engine);
			env.setEnvironment(null, new Members(), new CallStack());
			AussomType instTy;
			try {
				instTy = cls.instantiate(env, false, new AussomList());
			} catch (aussomException ae) {
				throw new AussomScriptException(ae, className + ".aus");
			}
			if (instTy.isEx()) {
				throw new AussomScriptException((AussomException) instTy,
					className + ".aus");
			}
			AussomObject inst = (AussomObject) instTy;
			return invokeOnInstance(cls, inst, name, args);
		} finally {
			console.get().register(prior);
		}
	}

	private Object invokeOnInstance(astClass cls, AussomObject inst,
			String name, Object[] args) throws ScriptException {
		LoggingInt prior = console.get().getLoggingInt();
		boolean weRegistered = false;
		if (prior == null) {
			console.get().register(new AussomScriptContextLogger(getContext()));
			weRegistered = true;
		}
		try {
			Environment env = new Environment(this.engine);
			env.setEnvironment(inst, new Members(), new CallStack());
			env.setCurObj(inst);

			AussomList aArgs = new AussomList();
			if (args != null) {
				for (Object o : args) {
					aArgs.add(AussomBindingsMarshaller.toAussom(o));
				}
			}

			AussomType ret;
			try {
				ret = cls.call(env, false, name, aArgs);
			} catch (aussomException ae) {
				throw new AussomScriptException(ae, cls.getName() + ".aus");
			}
			if (ret == null) ret = new AussomNull();
			if (ret.isEx()) {
				throw new AussomScriptException((AussomException) ret,
					cls.getName() + ".aus");
			}
			return AussomBindingsMarshaller.fromAussom(ret);
		} finally {
			if (weRegistered) {
				console.get().register(prior);
			}
		}
	}

	/* ------------------------------------------------------------ */
	/*  Helpers                                                     */
	/* ------------------------------------------------------------ */

	private static String wrapSnippet(String src, String className) {
		StringBuilder sb = new StringBuilder(src.length() + 96);
		sb.append("class ").append(className).append(" {\n");
		sb.append("\tpublic bindings = null;\n");
		sb.append("\tpublic main(args) {\n");
		sb.append(src).append("\n");
		sb.append("\t}\n");
		sb.append("}\n");
		return sb.toString();
	}

	private static String readAll(Reader r) throws ScriptException {
		if (r == null) throw new NullPointerException("reader");
		StringBuilder sb = new StringBuilder();
		char[] buf = new char[2048];
		try (BufferedReader br = (r instanceof BufferedReader)
				? (BufferedReader) r : new BufferedReader(r)) {
			int n;
			while ((n = br.read(buf)) >= 0) sb.append(buf, 0, n);
		} catch (IOException e) {
			ScriptException se = new ScriptException("Failed to read script: " + e.getMessage());
			se.initCause(e);
			throw se;
		}
		return sb.toString();
	}

	/** Underlying Engine, exposed for advanced embedders / tests. */
	public Engine getAussomEngine() {
		return this.engine;
	}
}

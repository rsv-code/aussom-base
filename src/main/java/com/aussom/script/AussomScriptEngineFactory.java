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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import com.aussom.DefaultSecurityManagerImpl;
import com.aussom.Engine;
import com.aussom.Universe;

/**
 * JSR 223 factory for the Aussom scripting language. Discovered via
 * META-INF/services/javax.script.ScriptEngineFactory.
 *
 * Engines are returned with a default DefaultSecurityManagerImpl.
 * Hosts that need a custom SecurityManagerInt should subclass this
 * factory and override getScriptEngine().
 */
public class AussomScriptEngineFactory implements ScriptEngineFactory {

	private static final String ENGINE_NAME = "Aussom Scripting Engine";
	private static final String LANGUAGE_NAME = "Aussom";

	private static final List<String> NAMES =
		Collections.unmodifiableList(Arrays.asList("aussom", "aus", "Aussom"));
	private static final List<String> EXTENSIONS =
		Collections.unmodifiableList(Arrays.asList("aus"));
	private static final List<String> MIME_TYPES =
		Collections.unmodifiableList(Arrays.asList(
			"application/x-aussom", "text/x-aussom"));

	/**
	 * Closes the cold race in Universe.init() / Lang.get(): the very
	 * first 'new Engine(...)' in the JVM is racy. Holding a class
	 * lock while the factory builds its first engine guarantees only
	 * one thread enters the cold path. Subsequent constructions hit
	 * the post-init fast path and don't need the lock.
	 */
	private static final Object FIRST_INIT_LOCK = new Object();

	@Override public String getEngineName() { return ENGINE_NAME; }
	@Override public String getEngineVersion() { return Universe.getAussomVersion(); }
	@Override public List<String> getExtensions() { return EXTENSIONS; }
	@Override public List<String> getMimeTypes() { return MIME_TYPES; }
	@Override public List<String> getNames() { return NAMES; }
	@Override public String getLanguageName() { return LANGUAGE_NAME; }
	@Override public String getLanguageVersion() { return Universe.getAussomVersion(); }

	@Override
	public Object getParameter(String key) {
		if (key == null) return null;
		switch (key) {
			case ScriptEngine.ENGINE:           return ENGINE_NAME;
			case ScriptEngine.ENGINE_VERSION:   return Universe.getAussomVersion();
			case ScriptEngine.LANGUAGE:         return LANGUAGE_NAME;
			case ScriptEngine.LANGUAGE_VERSION: return Universe.getAussomVersion();
			case ScriptEngine.NAME:             return "aussom";
			case "THREADING":                   return "MULTITHREADED";
			default:                            return null;
		}
	}

	@Override
	public String getMethodCallSyntax(String obj, String m, String... args) {
		StringBuilder sb = new StringBuilder();
		sb.append(obj).append(".").append(m).append("(");
		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				if (i > 0) sb.append(", ");
				sb.append(args[i]);
			}
		}
		sb.append(")");
		return sb.toString();
	}

	/**
	 * Aussom's stdlib console is exposed as 'c'. log() is the most
	 * idiomatic output call.
	 */
	@Override
	public String getOutputStatement(String toDisplay) {
		String s = toDisplay == null ? "" : toDisplay;
		return "c.log(\"" + escape(s) + "\");";
	}

	private static String escape(String s) {
		StringBuilder sb = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			switch (ch) {
				case '\\': sb.append("\\\\"); break;
				case '"':  sb.append("\\\""); break;
				case '\n': sb.append("\\n");  break;
				case '\r': sb.append("\\r");  break;
				case '\t': sb.append("\\t");  break;
				default:   sb.append(ch);
			}
		}
		return sb.toString();
	}

	@Override
	public String getProgram(String... statements) {
		StringBuilder sb = new StringBuilder();
		sb.append("class jsr223Program {\n");
		sb.append("\tpublic main(args) {\n");
		if (statements != null) {
			for (String s : statements) {
				sb.append("\t\t").append(s).append(";\n");
			}
		}
		sb.append("\t}\n");
		sb.append("}\n");
		return sb.toString();
	}

	@Override
	public ScriptEngine getScriptEngine() {
		Engine eng;
		try {
			synchronized (FIRST_INIT_LOCK) {
				eng = new Engine(new DefaultSecurityManagerImpl());
				eng.addResourceIncludePath("/com/aussom/stdlib/aus/");
			}
		} catch (Exception e) {
			throw new RuntimeException(
				"Aussom engine: failed to construct underlying Engine: " + e.getMessage(), e);
		}
		return new AussomScriptEngine(this, eng);
	}
}

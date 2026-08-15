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

import java.io.IOException;
import java.io.Writer;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;

import com.aussom.LoggingInt;

/**
 * Routes Aussom console output to the writers on the script engine's
 * current ScriptContext. Installed on the underlying Engine once, when
 * the script engine is built, and never replaced.
 *
 * <p>The context is resolved on every write rather than captured at
 * construction, so a host that calls {@code getContext().setWriter(...)}
 * between evaluations, or replaces the context outright with
 * {@code setContext(...)}, sees output follow.
 *
 * <p>Deliberately bound to the engine's context rather than to the
 * context passed to a single {@code eval(script, context)} call. Routing
 * per call meant swapping the Engine's logger field for the duration of
 * each call, and that field is shared: two concurrent callers overwrote
 * each other's destination, and the restoring finally of whichever
 * finished first sent the other's remaining output to the default
 * logger. Output destination is a property of the script engine here,
 * so there is no per-call state to race over. See
 * design/security-evaluation-f1-f3.md section 5.
 *
 * Output channels map as follows:
 *   info / trc / dbg / log / warn / print / println -> getWriter()
 *   err                                              -> getErrorWriter()
 */
final class AussomScriptContextLogger implements LoggingInt {
	private final ScriptEngine eng;

	AussomScriptContextLogger(ScriptEngine Eng) {
		this.eng = Eng;
	}

	/**
	 * Gets the context to write to. Never null in practice:
	 * AbstractScriptEngine always holds a context.
	 * @return A ScriptContext, or null if the engine has none.
	 */
	private ScriptContext context() {
		return this.eng.getContext();
	}

	private void writeOut(String s) {
		ScriptContext ctx = this.context();
		if (ctx == null) {
			System.out.print(s);
			return;
		}
		Writer w = ctx.getWriter();
		if (w == null) {
			System.out.print(s);
			return;
		}
		try {
			w.write(s);
			w.flush();
		} catch (IOException ignored) {
			// JSR 223 spec gives no channel for writer-side IO
			// failures; downgrade to silent. The host's writer
			// owns its own error policy.
		}
	}

	private void writeErr(String s) {
		ScriptContext ctx = this.context();
		if (ctx == null) {
			System.err.print(s);
			return;
		}
		Writer w = ctx.getErrorWriter();
		if (w == null) {
			System.err.print(s);
			return;
		}
		try {
			w.write(s);
			w.flush();
		} catch (IOException ignored) {
			// See note in writeOut().
		}
	}

	@Override public void log(String s)   { writeOut(s + "\n"); }
	@Override public void trc(String s)   { writeOut(s + "\n"); }
	@Override public void dbg(String s)   { writeOut(s + "\n"); }
	@Override public void info(String s)  { writeOut(s + "\n"); }
	@Override public void warn(String s)  { writeOut(s + "\n"); }
	@Override public void err(String s)   { writeErr(s + "\n"); }
	@Override public void print(String s) { writeOut(s); }
	@Override public void println(String s) { writeOut(s + "\n"); }
}

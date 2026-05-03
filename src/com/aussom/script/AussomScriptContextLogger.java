/*
 * Copyright 2026 Austin Lehman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.aussom.script;

import java.io.IOException;
import java.io.Writer;

import javax.script.ScriptContext;

import com.aussom.LoggingInt;

/**
 * Routes Aussom console output to the writers on a JSR 223
 * ScriptContext. Registered on the eval thread's per-thread
 * console.get() instance for the lifetime of one eval / Invocable
 * call, then de-registered (or replaced with the prior logger) in a
 * finally block.
 *
 * Output channels map as follows:
 *   info / trc / dbg / log / warn / print / println -> getWriter()
 *   err                                              -> getErrorWriter()
 */
final class AussomScriptContextLogger implements LoggingInt {
	private final ScriptContext ctx;

	AussomScriptContextLogger(ScriptContext ctx) {
		this.ctx = ctx;
	}

	private void writeOut(String s) {
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

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

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * A pre-parsed Aussom script. Holds the synthetic class name that
 * was registered in the engine's class registry at compile time,
 * along with the bound name that any 'bindings' member is exposed
 * under. Re-running is just re-binding inputs and re-invoking main.
 */
public final class AussomCompiledScript extends CompiledScript {

	private final AussomScriptEngine engine;
	private final String className;
	private final boolean wrapped;

	AussomCompiledScript(AussomScriptEngine engine, String className, boolean wrapped) {
		this.engine = engine;
		this.className = className;
		this.wrapped = wrapped;
	}

	@Override
	public Object eval(ScriptContext context) throws ScriptException {
		return this.engine.runCompiled(this.className, this.wrapped, context);
	}

	@Override
	public ScriptEngine getEngine() {
		return this.engine;
	}

	String getClassName() {
		return this.className;
	}

	boolean isWrapped() {
		return this.wrapped;
	}
}

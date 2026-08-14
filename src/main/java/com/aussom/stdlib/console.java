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

package com.aussom.stdlib;

import java.util.ArrayList;

import com.aussom.Environment;
import com.aussom.types.AussomType;
import com.aussom.types.AussomTypeInt;

/**
 * Extern backing for the Aussom standard library class {@code c}.
 *
 * <p>This class is a binding and nothing more. Every method takes the
 * calling {@link Environment}, so output is written to the logger owned
 * by the {@code Engine} that ran the script. Two engines in one JVM
 * therefore never see each other's output, on any thread.
 *
 * <p>There is deliberately no static accessor and no registration hook
 * here. An earlier version kept the registered logger in a
 * {@code ThreadLocal}, which bound output to whichever thread happened
 * to be running rather than to the engine that produced it. Java code
 * that wants to log should hold its own {@link com.aussom.LoggingInt}
 * or reach the engine's with {@code engine.getLogger()}. See
 * {@code design/multitenancy-safety.md} section 7.3.
 */
public class console {

	/**
	 * Default constructor. Required so the extern linkage can build the
	 * singleton instance for the Aussom {@code c} class.
	 */
	public console() { }

	public AussomType log(Environment env, ArrayList<AussomType> args) {
		env.getEngine().getLogger().log(((AussomTypeInt)args.get(0)).str());
		return env.getClassInstance();
	}

	public AussomType trc(Environment env, ArrayList<AussomType> args) {
		env.getEngine().getLogger().trc(((AussomTypeInt)args.get(0)).str());
		return env.getClassInstance();
	}

	public AussomType dbg(Environment env, ArrayList<AussomType> args) {
		env.getEngine().getLogger().dbg(((AussomTypeInt)args.get(0)).str());
		return env.getClassInstance();
	}

	public AussomType info(Environment env, ArrayList<AussomType> args) {
		env.getEngine().getLogger().info(((AussomTypeInt)args.get(0)).str());
		return env.getClassInstance();
	}

	public AussomType warn(Environment env, ArrayList<AussomType> args) {
		env.getEngine().getLogger().warn(((AussomTypeInt)args.get(0)).str());
		return env.getClassInstance();
	}

	public AussomType err(Environment env, ArrayList<AussomType> args) {
		env.getEngine().getLogger().err(((AussomTypeInt)args.get(0)).str());
		return env.getClassInstance();
	}

	public AussomType print(Environment env, ArrayList<AussomType> args) {
		env.getEngine().getLogger().print(((AussomTypeInt)args.get(0)).str());
		return env.getClassInstance();
	}

	public AussomType println(Environment env, ArrayList<AussomType> args) {
		env.getEngine().getLogger().println(((AussomTypeInt)args.get(0)).str());
		return env.getClassInstance();
	}
}

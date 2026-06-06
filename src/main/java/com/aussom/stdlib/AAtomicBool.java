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
import java.util.concurrent.atomic.AtomicBoolean;

import com.aussom.Environment;
import com.aussom.types.AussomBool;
import com.aussom.types.AussomType;

/**
 * Aussom AtomicBool runtime. Wraps
 * {@link java.util.concurrent.atomic.AtomicBoolean}. The main use
 * is the one-shot guard: many threads race compareAndSet(false,
 * true) and exactly one wins. Each method call is atomic; a
 * sequence of method calls is not. See
 * design/aussom-concurrency.md.
 */
public class AAtomicBool {
	private final AtomicBoolean value = new AtomicBoolean(false);

	public AAtomicBool() { }

	public AussomType newAtomicBool(Environment env, ArrayList<AussomType> args) {
		if (!args.get(0).isNull()) {
			this.value.set(((AussomBool) args.get(0)).getValue());
		}
		return env.getClassInstance();
	}

	public AussomType get(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.value.get());
	}

	public AussomType set(Environment env, ArrayList<AussomType> args) {
		this.value.set(((AussomBool) args.get(0)).getValue());
		return env.getClassInstance();
	}

	public AussomType getAndSet(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.value.getAndSet(((AussomBool) args.get(0)).getValue()));
	}

	public AussomType compareAndSet(Environment env, ArrayList<AussomType> args) {
		boolean expect = ((AussomBool) args.get(0)).getValue();
		boolean update = ((AussomBool) args.get(1)).getValue();
		return new AussomBool(this.value.compareAndSet(expect, update));
	}
}

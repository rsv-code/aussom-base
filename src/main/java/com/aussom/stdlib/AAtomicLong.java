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
import java.util.concurrent.atomic.AtomicLong;

import com.aussom.Environment;
import com.aussom.types.AussomBool;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomType;

/**
 * Aussom AtomicLong runtime. Wraps
 * {@link java.util.concurrent.atomic.AtomicLong} so scripts get
 * atomic counters and IDs on shared app state. Each method call is
 * atomic; a sequence of method calls is not. See
 * design/aussom-concurrency.md.
 */
public class AAtomicLong {
	private final AtomicLong value = new AtomicLong(0);

	public AAtomicLong() { }

	public AussomType newAtomicLong(Environment env, ArrayList<AussomType> args) {
		if (!args.get(0).isNull()) {
			this.value.set(((AussomInt) args.get(0)).getValue());
		}
		return env.getClassInstance();
	}

	public AussomType get(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.get());
	}

	public AussomType set(Environment env, ArrayList<AussomType> args) {
		this.value.set(((AussomInt) args.get(0)).getValue());
		return env.getClassInstance();
	}

	public AussomType incrementAndGet(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.incrementAndGet());
	}

	public AussomType decrementAndGet(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.decrementAndGet());
	}

	public AussomType addAndGet(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.addAndGet(((AussomInt) args.get(0)).getValue()));
	}

	public AussomType getAndSet(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.getAndSet(((AussomInt) args.get(0)).getValue()));
	}

	public AussomType compareAndSet(Environment env, ArrayList<AussomType> args) {
		long expect = ((AussomInt) args.get(0)).getValue();
		long update = ((AussomInt) args.get(1)).getValue();
		return new AussomBool(this.value.compareAndSet(expect, update));
	}
}

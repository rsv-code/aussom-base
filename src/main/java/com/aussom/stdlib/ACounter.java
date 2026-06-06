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
import java.util.concurrent.atomic.LongAdder;

import com.aussom.Environment;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomType;

/**
 * Aussom Counter runtime. Wraps
 * {@link java.util.concurrent.atomic.LongAdder}, the better counter
 * under write contention: AtomicLong retries a CAS loop when many
 * threads hammer it, while LongAdder stripes writes across cells
 * and sums on read. The documented default for metrics and hit
 * counters. Trade-off: no compareAndSet, and sum() is not a
 * point-in-time snapshot under concurrent writes. Each method call
 * is atomic; a sequence of method calls is not. See
 * design/aussom-concurrency.md.
 */
public class ACounter {
	private final LongAdder value = new LongAdder();

	public ACounter() { }

	public AussomType increment(Environment env, ArrayList<AussomType> args) {
		this.value.increment();
		return env.getClassInstance();
	}

	public AussomType decrement(Environment env, ArrayList<AussomType> args) {
		this.value.decrement();
		return env.getClassInstance();
	}

	public AussomType add(Environment env, ArrayList<AussomType> args) {
		this.value.add(((AussomInt) args.get(0)).getValue());
		return env.getClassInstance();
	}

	public AussomType sum(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.sum());
	}

	public AussomType reset(Environment env, ArrayList<AussomType> args) {
		this.value.reset();
		return env.getClassInstance();
	}
}

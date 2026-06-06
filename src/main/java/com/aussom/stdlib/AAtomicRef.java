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
import java.util.concurrent.atomic.AtomicReference;

import com.aussom.Environment;
import com.aussom.types.AussomBool;
import com.aussom.types.AussomNull;
import com.aussom.types.AussomType;

/**
 * Aussom AtomicRef runtime. Wraps
 * {@link java.util.concurrent.atomic.AtomicReference} holding any
 * Aussom value. The use case is whole-value publication: build a
 * new map or list, then set() it in one step so readers see the old
 * value or the new one, never a mix. compareAndSet matches by
 * reference identity (the same underlying object), with one
 * usability exception: any null matches any null, because each
 * script null literal is its own AussomNull instance. Each method
 * call is atomic; a sequence of method calls is not. See
 * design/aussom-concurrency.md.
 */
public class AAtomicRef {
	private final AtomicReference<AussomType> value =
		new AtomicReference<AussomType>(new AussomNull());

	public AAtomicRef() { }

	public AussomType newAtomicRef(Environment env, ArrayList<AussomType> args) {
		this.value.set(args.get(0));
		return env.getClassInstance();
	}

	public AussomType get(Environment env, ArrayList<AussomType> args) {
		return this.value.get();
	}

	public AussomType set(Environment env, ArrayList<AussomType> args) {
		this.value.set(args.get(0));
		return env.getClassInstance();
	}

	public AussomType getAndSet(Environment env, ArrayList<AussomType> args) {
		return this.value.getAndSet(args.get(0));
	}

	public AussomType compareAndSet(Environment env, ArrayList<AussomType> args) {
		AussomType expect = args.get(0);
		AussomType update = args.get(1);
		AussomType cur = this.value.get();
		// Identity match, except null-to-null which matches by kind
		// because every script null literal is a distinct
		// AussomNull instance. The final CAS is on the exact
		// instance observed, so the swap stays atomic.
		if (cur == expect || (cur.isNull() && expect.isNull())) {
			return new AussomBool(this.value.compareAndSet(cur, update));
		}
		return new AussomBool(false);
	}
}

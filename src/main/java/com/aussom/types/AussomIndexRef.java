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

package com.aussom.types;

/**
 * Assignment target for an overloaded index write, produced when
 * obj[key] appears on the left of an assignment and obj's class
 * defines __opIndexSet__. This is a plain data holder: it carries
 * the target object and the evaluated key, and
 * astExpression.assignment() performs the actual __opIndexSet__
 * call so runtime exceptions from the method can propagate.
 * See design/operator-overloading.md.
 */
public class AussomIndexRef extends AussomRef {
	private AussomObject target = null;
	private AussomType key = null;

	public AussomIndexRef(AussomObject Target, AussomType Key) {
		super();
		this.target = Target;
		this.key = Key;
	}

	public AussomObject getTarget() {
		return this.target;
	}

	public AussomType getKey() {
		return this.key;
	}

	/**
	 * Writes through this ref must call __opIndexSet__, which
	 * needs the Environment; astExpression.assignment() handles it.
	 * A plain assign here would write into the orphaned backing map
	 * the base class allocates, so it is a deliberate no-op.
	 */
	@Override
	public synchronized void assign(AussomType Value) { }

	@Override
	public AussomType getValue() throws Exception {
		throw new Exception("AussomIndexRef.getValue(): Overloaded index reads resolve through __opIndex__, not through the reference.");
	}
}

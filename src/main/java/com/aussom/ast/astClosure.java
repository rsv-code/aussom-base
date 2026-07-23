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

package com.aussom.ast;

import com.aussom.Environment;
import com.aussom.types.AussomCallback;
import com.aussom.types.AussomObject;
import com.aussom.types.AussomType;

/**
 * Definition-site expression node for a closure
 * (::name(args) { ... }). The function definition itself is hoisted
 * onto the enclosing class at parse time as a private astFunctDef with
 * the closure flag set; this node only records the name and, when
 * evaluated, produces an AussomCallback that captures the defining
 * function invocation's locals.
 *
 * Deliberately keeps astNodeType.CALLBACK (via the astCallback super
 * constructor) rather than introducing a new node type: the dispatch
 * switches in astNode.eval throw on unlisted types, and every consumer
 * that checks for a callback node keeps working since a closure node
 * reports as one. Code that needs to tell the two apart uses
 * instanceof astClosure. See design/closures.md.
 */
public class astClosure extends astCallback implements astNodeInt {

	public astClosure() {
		super();
	}

	public astClosure(String FunctName) {
		super(FunctName);
	}

	@Override
	public AussomType evalImpl(Environment env, boolean getref) throws aussomException {
		AussomCallback cb = new AussomCallback(env, (AussomObject)env.getClassInstance(), this.getFunctName());
		cb.setCapturedLocals(env.getLocals());
		if (this.getChild() != null) {
			Environment tenv = env.clone(cb);
			return this.getChild().eval(tenv, getref);
		}
		return cb;
	}
}

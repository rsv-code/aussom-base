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

public class astCallback extends astNode implements astNodeInt {
	private String functName = "";
	
	public astCallback() {
		this.setType(astNodeType.CALLBACK);
	}
	
	public astCallback(String FunctName) {
		this.setType(astNodeType.CALLBACK);
		this.functName = FunctName;
	}
	
	@Override
	public String toString(int Level) {
		String rstr = "";
		rstr += getTabs(Level) + "{\n";
		rstr += this.getNodeStr(Level + 1) + ",\n";
		rstr += getTabs(Level + 1) + "\"functName\":\"" + this.functName + "\"\n";
		if(this.getChild() != null) {
			rstr += getTabs(Level + 1) + "\"child\":\n";
			rstr += ((astNodeInt)this.getChild()).toString(Level + 1) + ",\n";
		}
		rstr += getTabs(Level) + "}";
		return rstr;
	}

	@Override
	public AussomType evalImpl(Environment env, boolean getref) throws aussomException {
		AussomCallback cb = new AussomCallback(env, (AussomObject)env.getClassInstance(), this.functName);
		// When the named function is a closure hoisted onto the
		// current class, ::name captures the current locals so it
		// behaves the same as the closure definition expression.
		// Plain callbacks to regular functions are unaffected.
		AussomObject ci = env.getClassInstance();
		if (ci != null && ci.getClassDef() != null && ci.getClassDef().isClosureFunction(this.functName)) {
			cb.setCapturedLocals(env.getLocals());
		}
		if (this.getChild() != null) {
			Environment tenv = env.clone(cb);
			return this.getChild().eval(tenv, getref);
		}
		return cb;
	}

	public String getFunctName() {
		return functName;
	}

	public void setFunctName(String functName) {
		this.functName = functName;
	}
}

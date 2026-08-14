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

import com.aussom.Environment;
import com.aussom.ast.astClass;
import com.aussom.ast.aussomException;

import java.util.ArrayList;

public class AussomCallback extends AussomObject implements AussomTypeInt {
	private String functName = "";
	private AussomObject obj = null;
	private Environment tenv = null;

	// Non-null only when this callback targets a closure. Holds a
	// reference to the defining function invocation's locals, captured
	// when the closure expression (or ::name on a closure) was
	// evaluated. Dispatch copies it into the closure's fresh locals on
	// every invocation. See design/closures.md.
	private Members capturedLocals = null;

	public AussomCallback() {
		this.setType(cType.cCallback);

		// Setup linkage for string object.
		this.setExternObject(this);
		// No class definition is bound here. A primitive does not need
		// one to exist, only to dispatch, and dispatch always has an
		// Environment to resolve it from. Binding one at construction
		// would mean reaching for a process-wide global, which is what
		// let one engine's classes leak into another's.
		// See design/multitenancy-safety.md section 7.2.
	}
	
	public AussomCallback(Environment Env, AussomObject Obj, String FunctName) {
		this();
		this.tenv = Env;
		this.obj = Obj;
		this.functName = FunctName;
	}
	
	//public AussomType call(AussomList args) {
	//	return this.call(this.tenv, args);
	//}
	
	public AussomType call(Environment env, AussomList args) {
		AussomType ret;
		
		try {
			ret = this.callWithException(env, args);
		} catch(aussomException e) {
			env.getEngine().getLogger().err("\n" + e.getAussomStackTrace());
			return new AussomException(e.getMessage());
		}
		
		return ret;
	}
	
	public AussomType callWithException(Environment env, AussomList args) throws aussomException {
		AussomType ret;

		// Restore the binding that astCallback.evalImpl captured:
		// curObj AND classInstance are the bound owner. Without
		// pinning classInstance the access check in astClass.call
		// sees the env's stale ci (e.g. an extern bridge's target
		// like Element) and rejects private callbacks bound with
		// `this::privateFn`.
		AussomObject tobj = (AussomObject) env.getCurObj();
		AussomObject tci = env.getClassInstance();
		env.setCurObj(this.getObj());
		env.setClassInstance(this.getObj());
		try {
			astClass ac = this.obj.getClassDef(env);
			ret = ac.call(env, false, this.getFunctName(), args, this.capturedLocals);
		} catch(aussomException e) {
			env.setCurObj(tobj);
			env.setClassInstance(tci);
			throw e;
		}
		env.setCurObj(tobj);
		env.setClassInstance(tci);

		return ret;
	}
	
	public String getFunctName() {
		return functName;
	}

	public void setFunctName(String functName) {
		this.functName = functName;
	}

	public Members getCapturedLocals() {
		return this.capturedLocals;
	}

	public void setCapturedLocals(Members CapturedLocals) {
		this.capturedLocals = CapturedLocals;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof AussomCallback)) return false;
		AussomCallback other = (AussomCallback)o;
		// Two callbacks are equal when they name the same function
		// AND are bound to the same object (by reference).
		return this.obj == other.obj && this.functName.equals(other.functName);
	}

	@Override
	public int hashCode() {
		int result = System.identityHashCode(this.obj);
		result = 31 * result + this.functName.hashCode();
		return result;
	}

	// Identifies the bound object by class name and reference
	// identity, e.g. "dog@1b2c3d", or "null" when unbound.
	private String objRef() {
		if (this.obj == null) return "null";
		return this.obj.getTypeName() + "@" + Integer.toHexString(System.identityHashCode(this.obj));
	}

	@Override
	public String toString(int Level) {
		String rstr = "";
		rstr += AussomType.getTabs(Level) + "{\n";
		rstr += AussomType.getTabs(Level + 1) + "\"type\": \"" + this.getType().name() + "\",\n";
		rstr += AussomType.getTabs(Level + 1) + "\"obj\": \"" + this.objRef() + "\",\n";
		rstr += AussomType.getTabs(Level + 1) + "\"functName\": \"" + this.functName + "\"\n";
		rstr += AussomType.getTabs(Level) + "}";
		return rstr;
	}

	@Override
	public String str() {
		return "callback " + this.objRef() + "::" + this.functName;
	}

	public String str(int Level) {
		return this.str();
	}

	public AussomObject getObj() {
		return obj;
	}

	public void setObj(AussomObject obj) {
		this.obj = obj;
	}

	public Environment getEnv() {
		return tenv;
	}

	public void setEnv(Environment env) {
		this.tenv = env;
	}
	
	public AussomType _call(Environment env, ArrayList<AussomType> args) {
		return this.call(env, (AussomList)args.get(0));
	}
}

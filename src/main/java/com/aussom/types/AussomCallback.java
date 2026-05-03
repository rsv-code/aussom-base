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
import com.aussom.Universe;
import com.aussom.ast.astClass;
import com.aussom.ast.aussomException;
import com.aussom.stdlib.console;

import java.util.ArrayList;

public class AussomCallback extends AussomObject implements AussomTypeInt {
	private String functName = "";
	private AussomObject obj = null;
	private Environment tenv = null;
	
	public AussomCallback() {
		this.setType(cType.cCallback);

		// Setup linkage for string object.
		this.setExternObject(this);
		astClass def = Universe.get().CALLBACK_CLASS_DEF;
		if (def != null) this.setClassDef(def);
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
			console.get().err("\n" + e.getAussomStackTrace());
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
			astClass ac = this.obj.getClassDef();
			ret = ac.call(env, false, this.getFunctName(), args);
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

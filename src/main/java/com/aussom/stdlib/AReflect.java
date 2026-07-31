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

import com.aussom.types.AussomType;
import com.aussom.Environment;
import com.aussom.ParseDiagnostic;
import com.aussom.ast.astClass;
import com.aussom.ast.aussomException;
import com.aussom.types.AussomBool;
import com.aussom.types.AussomException;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomList;
import com.aussom.types.AussomMap;
import com.aussom.types.AussomNull;
import com.aussom.types.AussomObject;
import com.aussom.types.AussomString;

public class AReflect {
	public static AussomType evalStr(Environment env, ArrayList<AussomType> args) {
		if ((Boolean)env.getEngine().getSecurityManager().getProperty("reflect.eval.string")) {
			String code = ((AussomString)args.get(0)).getValue();
			String name = ((AussomString)args.get(1)).getValue();
			try {
				// parseString reports a parse error by setting the
				// engine flag and printing; it does not throw. Without
				// the check below a script had no way at all to learn
				// that the code it just evaluated failed to parse.
				//
				// The flag is engine-wide and sticky, so clear it
				// first and observe only what this parse sets.
				env.getEngine().clearParseError();
				env.getEngine().parseString(name, code);
				if (env.getEngine().hasParseErrors()) {
					// Clear again before returning. The failure is
					// reported to the caller as an exception, so the
					// flag has done its job. Leaving it set poisons
					// the engine: parseScriptLine consults it, so
					// every later evalLine would throw a spurious
					// parse error. Diagnostics are deliberately left
					// in place for reflect.parseDiagnostics().
					env.getEngine().clearParseError();
					return new AussomException("reflect.evalStr(): Parse error in evaluated string '" + name + "'. Call reflect.parseDiagnostics() for the position and message.");
				}
				return env.getClassInstance();
			} catch (aussomException e) {
				return new AussomException(e.getMessage());
			} catch (Exception e) {
				return new AussomException(e.getMessage());
			}
		} else {
			return new AussomException("reflect.evalStr(): Security exception, action 'reflect.eval.string' not permitted.");
		}
	}
	
	public static AussomType evalFile(Environment env, ArrayList<AussomType> args) {
		if ((Boolean)env.getEngine().getSecurityManager().getProperty("reflect.eval.file")) {
			String FileName = ((AussomString)args.get(0)).getValue();
			try {
				env.getEngine().parseFile(FileName);
			} catch (Exception e) {
				return new AussomException(e.getMessage());
			}
			return new AussomNull();
		} else {
			return new AussomException("reflect.evalFile(): Security exception, action 'reflect.eval.file' not permitted.");
		}
	}
	
	
	
	public static AussomType includeModule(Environment env, ArrayList<AussomType> args) {
		if ((Boolean)env.getEngine().getSecurityManager().getProperty("reflect.include.module")) {
			String incFile = ((AussomString)args.get(0)).getValue().replace(".", "/") + ".aus";
			try {
				env.getEngine().parseFile(incFile);
			} catch (Exception e) {
				return new AussomException(e.getMessage());
			}
			return env.getClassInstance();
		} else {
			return new AussomException("reflect.includeModule(): Security exception, action 'reflect.include.module' not permitted.");
		}
	}
	
	public static AussomType loadedModules(Environment env, ArrayList<AussomType> args) {
		AussomList list = new AussomList();
		for (String mod : env.getEngine().getIncludes()) {
			list.add(new AussomString(mod));
		}
		return list;
	}
	
	public static AussomType parseDiagnostics(Environment env, ArrayList<AussomType> args) {
		AussomList list = new AussomList();
		for (ParseDiagnostic diag : env.getEngine().getParseDiagnostics()) {
			AussomMap entry = new AussomMap();
			entry.put("file", new AussomString(diag.getFileName()));
			entry.put("line", new AussomInt((long)diag.getLine()));
			entry.put("col", new AussomInt((long)diag.getCol()));
			entry.put("severity", new AussomString(diag.getSeverity()));
			entry.put("message", new AussomString(diag.getMessage()));
			list.add(entry);
		}
		return list;
	}

	public static AussomType clearParseDiagnostics(Environment env, ArrayList<AussomType> args) {
		if ((Boolean)env.getEngine().getSecurityManager().getProperty("reflect.clear.diagnostics")) {
			env.getEngine().clearParseDiagnostics();
			return env.getClassInstance();
		} else {
			return new AussomException("reflect.clearParseDiagnostics(): Security exception, action 'reflect.clear.diagnostics' not permitted.");
		}
	}

	public static AussomType loadedClasses(Environment env, ArrayList<AussomType> args) {
		AussomList list = new AussomList();
		for (String cls : env.getEngine().getClasses().keySet()) {
			list.add(new AussomString(cls));
		}
		return list;
	}
	
	public static AussomType isModuleLoaded(Environment env, ArrayList<AussomType> args) {
		String incFile = ((AussomString)args.get(0)).getValue().replace(".", "/") + ".aus";
		return new AussomBool(env.getEngine().getIncludes().contains(incFile));
	}
	
	public static AussomType classExists(Environment env, ArrayList<AussomType> args) {
		AussomString name = (AussomString)args.get(0);
		return new AussomBool(env.getEngine().getClasses().containsKey(name.getValue()));
	}
	
	public static AussomType getClassDef(Environment env, ArrayList<AussomType> args) {
		String ClassName = ((AussomString)args.get(0)).getValue();
		if(env.getEngine().getClasses().containsKey(ClassName)) {
			astClass tc = env.getEngine().getClasses().get(ClassName);
			astClass cc = env.getEngine().getClasses().get("RClass");
			try {
				AussomObject co = (AussomObject) cc.instantiate(env);
				AClass ccobj = (AClass)co.getExternObject();
				ccobj.setClass(tc);
				return co;
			} catch (aussomException e) {
				return new AussomException("reflect.getClassDef(): Class '" + ClassName + "'.\n");
			}
		} else {
			return new AussomException("reflect.getClassDef(): Class '" + ClassName + "' not found.");
		}
	}
	
	public static AussomType instantiate(Environment env, ArrayList<AussomType> args) {
		String ClassName = ((AussomString)args.get(0)).getValue();

		// Reflection must not be a back door around the `new` restriction:
		// a static class is a singleton and is reached by class name, so
		// refuse it here with a message that names the actual reason.
		astClass target = env.getEngine().getClassByName(ClassName);
		if (target != null && target.getStatic()) {
			return new AussomException("reflect.instantiate(): Cannot instantiate static class '" + ClassName + "'. Static classes are singletons and are accessed by class name.");
		}

		try {
			AussomObject co = env.getEngine().instantiateObject(ClassName);
			return co;
		} catch (aussomException e) {
			return new AussomException("reflect.instantiate(): Failed to instantiate class  '" + ClassName + "'.\n");
		}
	}
	
	public static AussomType invoke(Environment env, ArrayList<AussomType> args) {
		AussomObject obj = (AussomObject)args.get(0);
		String fname = ((AussomString)args.get(1)).getValue();
		AussomList alist = (AussomList) args.get(2);
		
		try {
			astClass ac = obj.getClassDef();
			Environment tenv = env.clone(obj);
			return ac.call(tenv, false, fname, alist);
		} catch (aussomException e) {
			return new AussomException(e.getAussomMessage());
		} catch (Exception e) {
			return new AussomException("reflect.invoke(): Unhandled exception occurred while calling '" + fname + "'. " + e.getMessage());
		}
	}
}

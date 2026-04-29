/*
 * Copyright 2017 Austin Lehman
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.aussom.Util;
import com.aussom.ast.astClass;
import com.aussom.ast.astFunctDef;
import com.aussom.ast.aussomException;

import java.util.ArrayList;
import java.util.List;

public class AussomObject extends AussomType implements AussomTypeInt, AussomTypeObjectInt {
	private astClass classDef;

	/*
	 * Members and mock are lazy. Most AussomObject instances created
	 * by the runtime are primitive types (AussomNull, AussomInt, etc.)
	 * that never carry user-defined members and are never mocked, so
	 * allocating an empty Members + Mock per construction is wasted
	 * work. These fields are populated on first write through
	 * getMembers() / getMock(); read-only paths use the short-circuit
	 * predicates (containsMember, isMockSet, ...) that return false
	 * when the field is still null.
	 */
	private Members members;
	private Mock mock;

	private Object externObject = null;

	public AussomObject(astClass classDef) {}

	public AussomObject() {
		this(true);
	}

	public AussomObject(boolean LinkClass) {
		this.setType(cType.cObject);

		if (LinkClass) {
			// Setup linkage for string object.
			this.setExternObject(this);
			astClass def = Universe.get().OBJECT_CLASS_DEF;
			if (def != null) this.setClassDef(def);
		}
	}

	@Override
	public AussomType clone() {
		AussomObject n = new AussomObject();
		n.setClassDef(this.classDef);
		if (this.members != null) {
			Members nMembers = n.getMembers();
			for (String name : this.members.getMap().keySet()) {
				nMembers.getMap().put(name, this.members.get(name).clone());
			}
		}
		return n;
	}

	public astClass getClassDef() {
		return classDef;
	}

	public void setClassDef(astClass classDef) {
		this.classDef = classDef;
	}

	public Object getExternObject() {
		return externObject;
	}

	public void setExternObject(Object externObject) {
		this.externObject = externObject;
	}

	public void addMember(String Key, AussomType Value) {
		this.getMembers().add(Key, Value);
	}

	/**
	 * Lazy accessor. The Members instance is allocated on first
	 * call. Use this when you need to write a member or when you
	 * specifically need a non-null Members handle. For read-only
	 * checks prefer containsMember / getMember which short-circuit
	 * without allocating.
	 */
	public Members getMembers() {
		if (this.members == null) this.members = new Members();
		return this.members;
	}

	/**
	 * Returns the underlying Members or null when no member has been
	 * added. Read-only paths can branch on null to skip work; this
	 * never allocates.
	 */
	public Members getMembersOrNull() {
		return this.members;
	}

	/**
	 * True iff a member with the given name has been added. Does
	 * not allocate when no member has been added.
	 */
	public boolean containsMember(String name) {
		return this.members != null && this.members.contains(name);
	}

	/**
	 * Returns the value bound to name or null when absent. Does not
	 * allocate when no member has been added.
	 */
	public AussomType getMember(String name) {
		if (this.members == null) return null;
		return this.members.get(name);
	}

	@Override
	public String toString(int Level) {
		String rstr = "";

		rstr += getTabs(Level);
		rstr += "line ";
		rstr += this.getClassDef().getLineNum();
		rstr += ": ";
		rstr += "[";
		rstr += this.getType().name();
		rstr += "] classDef='";
		if(this.classDef != null)
			rstr += this.getClassDef().getName();
		else
			rstr += "undef";
		rstr += "'";
		if(this.getClassDef().getName() != "")
			rstr += " name='" + this.getClassDef().getName() + "'";
		rstr += "\n";

		if (this.getClassDef().getExtern() && this.getClassDef().getExternClass() != AussomObject.class && this.externObject instanceof AussomTypeInt) {
			AussomTypeInt ati = (AussomTypeInt)this.externObject;
			System.out.println(ati.str());
			rstr += getTabs(Level) + "value=" + ati.toString(Level + 1);
			rstr += "\n";
		}

		if(this.members != null)
			rstr += this.members.toString(Level);

		return rstr;
	}

	@Override
	public String str() {
		return this.str(0);
	}


	public String str(int Level) {
		if (this.members != null && this.members.getMap().size() > 0) {
			String rstr = "{\n";
			int count = 0;
			for (String name : this.members.getMap().keySet()) {
				rstr += getTabs(Level + 1) + "'" + name + "': ";
				AussomType child = this.members.get(name);
				rstr += ((AussomObject)child).str(Level + 1);
				count++;
				if (count < this.members.getMap().size()) {
					rstr += ",";
				}
				rstr += "\n";
			}
			rstr += getTabs(Level) + "}";
			return rstr;
		} else if (this.externObject != null && this.externObject instanceof AussomTypeInt) {
			AussomTypeInt ati = (AussomTypeInt)this.externObject;
			return ati.str(Level);
		} else {
			return "{}";
		}
	}

	public String str(Environment env) throws aussomException {
		if (this.getClassDef().containsFunction("toString", "")) {
			astClass ac = this.getClassDef();
			Environment tenv = env.clone(this);
			AussomType ret = ac.call(tenv, false, "toString", new AussomList());
			if (ret.getType() == cType.cString) {
				return ((AussomString)ret).getValue();
			} else if (ret.isEx()) {
			  System.out.println(((AussomException)ret).stackTraceToString());
			}
		  }
		return "cObject@" + Integer.toHexString(System.identityHashCode(this));
	}

	@Override
	public AussomType toJson(Environment env, ArrayList<AussomType> args) {
		String clsName = this.getClassDef().getExternClass().getName();
		if (this.getClassDef().getExtern() && this.getClassDef().getExternClass() != AussomObject.class && this.externObject instanceof AussomTypeObjectInt) {
			AussomTypeObjectInt atoi = (AussomTypeObjectInt)this.externObject;
			return atoi.toJson(env, args);
		} else {
			ArrayList<String> parts = new ArrayList<String>();
			if (this.members != null) {
				for (String key : this.members.getMap().keySet()) {
					AussomType ct = this.members.get(key);
					if (
							ct instanceof AussomBool
									|| ct instanceof AussomNull
									|| ct instanceof AussomInt
									|| ct instanceof AussomDouble
									|| ct instanceof AussomString
									|| ct instanceof AussomList
									|| ct instanceof AussomMap
									|| ct instanceof AussomObject
					) {
						parts.add("\"" + key + "\":" + ((AussomTypeObjectInt) ct).toJson(env, new ArrayList<AussomType>()).getValueString());

					} else {
						return new AussomException("Unexpected type found '" + ct.getType().name() + "' when converting to JSON.");
					}
				}
			}
			return new AussomString("{" + Util.join(parts, ",") + "}");
		}
	}

	public AussomType pack(Environment env, ArrayList<AussomType> args) {
		if (this.getClassDef().getExtern() && this.getClassDef().getExternClass() != AussomObject.class  && this.externObject instanceof AussomTypeObjectInt) {
			AussomTypeObjectInt atoi = (AussomTypeObjectInt)this.externObject;
			return atoi.pack(env, args);
		} else {
			ArrayList<String> parts = new ArrayList<String>();
			// Object metadata.
			parts.add("\"type\":\"" + this.getClassDef().getName() + "\"");
			ArrayList<String> mparts = new ArrayList<String>();
			if (this.members != null) {
				for (String key : this.members.getMap().keySet()) {
					AussomType ct = this.members.get(key);
					if (
							ct instanceof AussomBool
									|| ct instanceof AussomNull
									|| ct instanceof AussomInt
									|| ct instanceof AussomDouble
									|| ct instanceof AussomString
									|| ct instanceof AussomList
									|| ct instanceof AussomMap
									|| ct instanceof AussomObject
					) {
						mparts.add("\"" + key + "\":" + ((AussomTypeObjectInt) ct).pack(env, new ArrayList<AussomType>()).getValueString());

					} else {
						return new AussomException("Unexpected type found '" + ct.getType().name() + "' when packing object.");
					}
				}
			}
			parts.add("\"members\":{" + Util.join(mparts, ",") + "}");
			return new AussomString("{" + Util.join(parts, ",") + "}");
		}
	}

	/**
	 * Lazy accessor. The Mock instance is allocated on first call.
	 * Use this for write paths (mock setup, spy record). Hot
	 * read-only paths should use isMockSet / hasFunctionMock /
	 * isSpySet which short-circuit without allocating.
	 */
	public Mock getMock() {
		if (this.mock == null) this.mock = new Mock();
		return this.mock;
	}

	/**
	 * Returns the underlying Mock or null when no mock has been set.
	 * Used by read-only call paths that want to avoid allocation.
	 */
	public Mock getMockOrNull() {
		return this.mock;
	}

	/**
	 * True iff at least one mock function has been set on this
	 * object. Does not allocate when no mock has been set — this
	 * is checked on the dispatch hot path.
	 */
	public boolean isMockSet() {
		return this.mock != null && this.mock.isMockSet();
	}

	/**
	 * True iff a function mock for the given name is registered.
	 * Does not allocate when no mock has been set.
	 */
	public boolean hasFunctionMock(String functionName) {
		return this.mock != null && this.mock.hasFunctionMock(functionName);
	}

	/**
	 * True iff spying is enabled for the given function name. Does
	 * not allocate when no mock has been set.
	 */
	public boolean isSpySet(String functionName) {
		return this.mock != null && this.mock.isSpySet(functionName);
	}

	public AussomType mock(Environment env, ArrayList<AussomType> args) {
		String functionName = ((AussomString)args.get(0)).getValue();
		AussomObject returnObject = ((AussomObject)args.get(1));
		this.getMock().setFunctionMock(functionName, returnObject);
		return env.getClassInstance();
	}

	public AussomType mockWhen(Environment env, ArrayList<AussomType> args) {
		String functionName = ((AussomString)args.get(0)).getValue();
		AussomCallback callback = (AussomCallback)args.get(1);
		AussomObject returnObject = (AussomObject)args.get(2);
		this.getMock().setWhenFunctionMock(functionName, callback, returnObject);
		return env.getClassInstance();
	}

	public AussomType setSpy(Environment env, ArrayList<AussomType> args) {
		String functionName = ((AussomString)args.get(0)).getValue();
		this.getMock().setSpy(functionName);
		return env.getClassInstance();
	}

	public AussomType getSpy(Environment env, ArrayList<AussomType> args) {
		String functionName = ((AussomString)args.get(0)).getValue();

		AussomList ret = new  AussomList();
		List<MockFunctionSpyRecord> spyRecordList = this.getMock().getSpyResults(functionName);
		for (MockFunctionSpyRecord spyRecord : spyRecordList) {
			AussomMap rec = new AussomMap();
			rec.getValue().put("timestamp", new AussomInt(spyRecord.getTimestamp()));
			AussomList recArgs = new AussomList();
			for (AussomType arg : spyRecord.getCallArgs().getValue()) {
				recArgs.getValue().add(arg);
			}
			rec.getValue().put("arguments", recArgs);
			rec.getValue().put("returnValue",  spyRecord.getReturnValue());
			ret.getValue().add(rec);
		}

		return ret;
	}
}

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
	 *
	 * Both fields are volatile and allocated with double-checked
	 * locking. Without that, two threads doing the first write on a
	 * shared object can each allocate their own instance and one
	 * thread's writes silently vanish; a plain field also permits a
	 * stale null read that would replace an already-populated
	 * instance. The volatile read costs nothing on the hot path and
	 * the monitor is touched at most once per object.
	 */
	private volatile Members members;
	private volatile Mock mock;

	private Object externObject = null;

	/**
	 * Builds an object bound to the provided class definition. This is
	 * the way Java code should construct a typed Aussom object when it
	 * has an engine to resolve the definition from, for example
	 * {@code new AussomObject(env.getEngine().getClassByName("Buffer"))}.
	 *
	 * <p>This constructor previously had an empty body: it accepted the
	 * definition and discarded it, which is why callers all followed it
	 * with an explicit setClassDef.
	 *
	 * @param classDef is the astClass definition to bind, or null for none.
	 */
	public AussomObject(astClass classDef) {
		this.setType(cType.cObject);
		this.setExternObject(this);
		if (classDef != null) this.setClassDef(classDef);
	}

	public AussomObject() {
		this(true);
	}

	public AussomObject(boolean LinkClass) {
		this.setType(cType.cObject);

		if (LinkClass) {
			// Setup linkage for string object.
			this.setExternObject(this);
			// No class definition is bound here; see
			// design/multitenancy-safety.md section 7.2. LinkClass still
			// controls the extern self-linkage, which is what callers
			// passing false are actually opting out of.
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

	/**
	 * Gets the class definition for this value, resolving it from the
	 * running engine when the value does not carry one.
	 *
	 * <p>Primitives carry no definition: binding one at construction
	 * would mean reaching for a process-wide global, and that is what
	 * let one engine's classes leak into another's. They do not need
	 * one to exist, only to dispatch, and every dispatch site has an
	 * Environment. This is the accessor those sites use. The no-argument
	 * getClassDef stays for callers that hold an instantiated object and
	 * for null checks.
	 *
	 * @param env is the Environment to resolve from. May be null.
	 * @return The astClass for this value, or null when it has none and
	 *         none can be resolved.
	 */
	public astClass getClassDef(Environment env) {
		astClass def = this.classDef;
		if (def != null) return def;
		if (env == null || env.getEngine() == null) return null;
		return env.getEngine().getPrimitiveClassDef(this.getType());
	}

	/**
	 * Gets the Aussom type name of this value.
	 *
	 * <p>Prefer this over {@code getClassDef().getName()} anywhere only
	 * the name is wanted. It is derived from the value's own
	 * {@link cType} and so answers for primitives, which carry no class
	 * definition, as well as for instantiated objects. It also avoids a
	 * pointless class-definition lookup on the hot paths that build
	 * JSON and debug output.
	 *
	 * @return A String with the Aussom type name.
	 */
	public String getTypeName() {
		cType t = this.getType();
		if (t == null) {
			if (this.classDef != null) return this.classDef.getName();
			return "undef";
		}
		switch (t) {
			case cBool:     return "bool";
			case cInt:      return "int";
			case cDouble:   return "double";
			case cString:   return "string";
			case cList:     return "list";
			case cMap:      return "map";
			case cNull:     return "cnull";
			case cCallback: return "callback";
			case cException: return "exception";
			default:
				// A user-defined or extern object: its name is the
				// class it was instantiated from.
				if (this.classDef != null) return this.classDef.getName();
				return "object";
		}
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
		Members m = this.members;
		if (m == null) {
			synchronized (this) {
				m = this.members;
				if (m == null) {
					m = new Members();
					this.members = m;
				}
			}
		}
		return m;
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

	/*
	 * Debug dump. This runs with no Environment, so it must not assume
	 * a class definition exists -- primitives never carry one, and an
	 * object built with the LinkClass=false constructor does not
	 * either. It previously dereferenced getClassDef() for the line
	 * number before the null check below it, which meant any such
	 * value threw here.
	 */
	@Override
	public String toString(int Level) {
		astClass def = this.classDef;
		String rstr = "";

		rstr += getTabs(Level);
		rstr += "line ";
		if (def != null) rstr += def.getLineNum();
		else rstr += "?";
		rstr += ": ";
		rstr += "[";
		rstr += this.getType().name();
		rstr += "] classDef='";
		if (def != null) rstr += def.getName();
		else rstr += "undef";
		rstr += "'";
		rstr += " name='" + this.getTypeName() + "'";
		rstr += "\n";

		if (def != null && def.getExtern() && def.getExternClass() != AussomObject.class && this.externObject instanceof AussomTypeInt) {
			AussomTypeInt ati = (AussomTypeInt)this.externObject;
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
				env.getEngine().getLogger().err(((AussomException)ret).stackTraceToString());
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
			parts.add("\"type\":\"" + this.getTypeName() + "\"");
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
		Mock m = this.mock;
		if (m == null) {
			synchronized (this) {
				m = this.mock;
				if (m == null) {
					m = new Mock();
					this.mock = m;
				}
			}
		}
		return m;
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

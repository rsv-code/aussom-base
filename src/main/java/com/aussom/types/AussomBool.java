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

import java.util.ArrayList;

public class AussomBool extends AussomObject implements AussomTypeInt, AussomTypeObjectInt, AussomClonable {
	private boolean value = false;

	public AussomBool() {
		this.setType(cType.cBool);

		// Setup linkage for string object.
		this.setExternObject(this);
		// No class definition is bound here. A primitive does not need
		// one to exist, only to dispatch, and dispatch always has an
		// Environment to resolve it from. Binding one at construction
		// would mean reaching for a process-wide global, which is what
		// let one engine's classes leak into another's.
		// See design/multitenancy-safety.md section 7.2.
	}

	@Override
	public AussomType clone() {
		AussomBool n = new AussomBool();
		n.setValue(this.value);
		return n;
	}
	
	public AussomBool(boolean Value) {
		this();
		this.setValue(Value);
	}

	public boolean getValue() {
		return this.value;
	}

	public void setValue(boolean value) {
		this.value = value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof AussomBool)) return false;
		return this.value == ((AussomBool)o).value;
	}

	@Override
	public int hashCode() {
		return Boolean.hashCode(this.value);
	}
	
	@Override
	public String toString(int Level) {
		String rstr = "";
		rstr += AussomType.getTabs(Level) + "{\n";
		rstr += AussomType.getTabs(Level + 1) + "\"type\": \"" + this.getType().name() + "\",\n";
		rstr += AussomType.getTabs(Level + 1) + "\"value\": ";
		if (this.value) rstr += "true";
		else rstr += "false";
		rstr += "\n";
		rstr += AussomType.getTabs(Level) + "}";
		return rstr;
	}

	@Override
	public String str() {
		if (this.value) return new String("true");
		return new String("false");
	}
	
	public String str(int Level) {
		return this.str();
	}
	
	public AussomType toInt(Environment env, ArrayList<AussomType> args) {
		if (this.value) return new AussomInt(1);
		return new AussomInt(0);
	}
	
	public AussomType toDouble(Environment env, ArrayList<AussomType> args) {
		if (this.value) return new AussomDouble(1.0);
		return new AussomDouble(0.0);
	}
	
	public AussomType toString(Environment env, ArrayList<AussomType> args) {
		if (this.value) return new AussomString("true");
		return new AussomString("false");
	}
	
	public AussomType compare(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(Boolean.compare(this.value, ((AussomBool)args.get(0)).getValue()));
	}
	
	public AussomType parse(Environment env, ArrayList<AussomType> args) {
		this.value = Boolean.parseBoolean(((AussomString)args.get(0)).getValue());
		return this;
	}
	
	@Override
	public AussomType toJson(Environment env, ArrayList<AussomType> args) {
		return this.toString(env, args);
	}
	
	@Override
	public AussomType pack(Environment env, ArrayList<AussomType> args) {
		ArrayList<String> parts = new ArrayList<String>();
		parts.add("\"type\":\"" + this.getTypeName() + "\"");
		parts.add("\"value\":" + this.str(0) + "");
		return new AussomString("{" + Util.join(parts, ",") + "}");
	}
}

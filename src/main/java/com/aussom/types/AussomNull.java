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

public class AussomNull extends AussomObject implements AussomTypeInt, AussomTypeObjectInt {
	public AussomNull() {
		this.setType(cType.cNull);

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
		return new AussomNull();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof AussomNull;
	}

	@Override
	public int hashCode() {
		return 0;
	}

	public AussomType isBlank(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(true);
	}

	@Override
	public String toString(int Level) {
		String rstr = "";
		rstr += AussomType.getTabs(Level) + "{\n";
		rstr += AussomType.getTabs(Level + 1) + "\"type\": \"" + this.getType().name() + "\",\n";
		rstr += AussomType.getTabs(Level + 1) + "\"value\": null\n";
		rstr += AussomType.getTabs(Level) + "}";
		return rstr;
	}

	@Override
	public String str() {
		return "null";
	}
	
	public String str(int Level) {
		return this.str();
	}
	
	@Override
	public AussomType toJson(Environment env, ArrayList<AussomType> args) {
		return new AussomString("null");
	}
	
	@Override
	public AussomType pack(Environment env, ArrayList<AussomType> args) {
		ArrayList<String> parts = new ArrayList<String>();
		parts.add("\"type\":\"" + this.getTypeName() + "\"");
		parts.add("\"value\":null");
		return new AussomString("{" + Util.join(parts, ",") + "}");
	}
}

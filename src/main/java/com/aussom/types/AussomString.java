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
import com.aussom.ast.aussomException;
import org.json.simple.JSONValue;

import java.util.ArrayList;

public class AussomString extends AussomObject implements AussomTypeInt, AussomTypeObjectInt {
	private String value = "";

	public AussomString() {
		this.setType(cType.cString);

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
		AussomString n = new AussomString();
		n.setValue(this.value);
		return n;
	}
	
	public AussomString(String Value) {
		this();
		this.value = Value;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof AussomString)) return false;
		return this.value.equals(((AussomString)o).value);
	}

	@Override
	public int hashCode() {
		return this.value.hashCode();
	}

	@Override
	public String toString(int Level) {
		String rstr = "";
		rstr += AussomType.getTabs(Level) + "{\n";
		rstr += AussomType.getTabs(Level + 1) + "\"type\": \"" + this.getType().name() + "\",\n";
		rstr += AussomType.getTabs(Level + 1) + "\"value\": \"" + this.value + "\"\n";
		rstr += AussomType.getTabs(Level) + "}";
		return rstr;
	}

	@Override
	public String str() {
		return this.value;
	}
	
	public String str(int Level) {
		return "\"" + this.str() + "\"";
	}
	
	public AussomType charAt(Environment env, ArrayList<AussomType> args) throws aussomException {
		int index = (int) ((AussomInt)args.get(0)).getValue();
		if (index >= 0 && index < this.value.length()) {
			return new AussomString("" + this.value.charAt(index));
		} else {
			throw new aussomException("Index " + index + " out of bounds." );
		}
	}
	
	public AussomType compare(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.compareTo(((AussomString)args.get(0)).getValue()));
	}
	
	public AussomType compareICase(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.compareToIgnoreCase(((AussomString)args.get(0)).getValue()));
	}
	
	public AussomType concat(Environment env, ArrayList<AussomType> args) {
		this.value += ((AussomString)args.get(0)).getValue();
		return this;
	}
	
	public AussomType contains(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.value.contains(((AussomString)args.get(0)).getValue()));
	}
	
	public AussomType endsWith(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.value.endsWith(((AussomString)args.get(0)).getValue()));
	}
	
	public AussomType equals(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.value.equals(((AussomString)args.get(0)).getValue()));
	}
	
	public AussomType equalsICase(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.value.equalsIgnoreCase(((AussomString)args.get(0)).getValue()));
	}
	
	public AussomType indexOf(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.indexOf(((AussomString)args.get(0)).getValue()));
	}
	
	public AussomType indexOfStart(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.indexOf(((AussomString)args.get(0)).getValue(), (int)((AussomInt)args.get(1)).getValue()));
	}
	
	public AussomType isEmpty(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.value.isEmpty());
	}

	public AussomType isBlank(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.value.trim().isEmpty());
	}
	
	public AussomType lastIndexOf(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.lastIndexOf(((AussomString)args.get(0)).getValue()));
	}
	
	public AussomType lastIndexOfStart(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.lastIndexOf(((AussomString)args.get(0)).getValue(), (int)((AussomInt)args.get(1)).getValue()));
	}
	
	public AussomType length(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.value.length());
	}
	
	public AussomType matches(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.value.matches(((AussomString)args.get(0)).getValue()));
	}
	
	public AussomType replace(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.value.replace(((AussomString)args.get(0)).getValue(), ((AussomString)args.get(1)).getValue()));
	}
	
	public AussomType replaceFirstRegex(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.value.replaceFirst(((AussomString)args.get(0)).getValue(), ((AussomString)args.get(1)).getValue()));
	}
	
	public AussomType replaceRegex(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.value.replaceAll(((AussomString)args.get(0)).getValue(), ((AussomString)args.get(1)).getValue()));
	}
	
	public AussomType split(Environment env, ArrayList<AussomType> args) {
		AussomList ret = new AussomList();
		boolean allowBlanks = ((AussomBool)args.get(1)).getValue();
		String parts[] = this.value.split(((AussomString)args.get(0)).getValue());
		for (String part : parts) {
			if (allowBlanks || !part.trim().equals("")) {
				ret.add(new AussomString(part));
			}
		}
		return ret;
	}
	
	public AussomType startsWith(Environment env, ArrayList<AussomType> args) {
		if (args.size() > 1) {
			return new AussomBool(this.value.startsWith(((AussomString)args.get(0)).getValue(), (int)((AussomInt)args.get(1)).getValue()));
		} else {
			return new AussomBool(this.value.startsWith(((AussomString)args.get(0)).getValue()));
		}
	}
	
	public AussomType substr(Environment env, ArrayList<AussomType> args) {
		if (args.get(1).isNull()) {
			return new AussomString(this.value.substring((int)((AussomInt)args.get(0)).getValue()));
		} else {
			return new AussomString(this.value.substring((int)((AussomInt)args.get(0)).getValue(), (int)((AussomInt)args.get(1)).getValue()));
		}
	}
	
	public AussomType toLower(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.value.toLowerCase());
	}
	
	public AussomType toUpper(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.value.toUpperCase());
	}
	
	public AussomType trim(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.value.trim());
	}

	public AussomType _format(Environment env, ArrayList<AussomType> args) {
		ArrayList<AussomType> fargs = ((AussomList)args.get(0)).getValue();

		// First map argument, if any, supplies named placeholders.
		AussomMap named = null;
		for (AussomType a : fargs) {
			if (a instanceof AussomMap) { named = (AussomMap)a; break; }
		}

		StringBuilder sb = new StringBuilder();
		int autoIndex = 0;
		int i = 0;
		int len = this.value.length();
		while (i < len) {
			char ch = this.value.charAt(i);
			if (ch == '{') {
				// Escaped '{{' -> literal '{'.
				if (i + 1 < len && this.value.charAt(i + 1) == '{') {
					sb.append('{');
					i += 2;
					continue;
				}
				int close = this.value.indexOf('}', i);
				if (close < 0) {
					return new AussomException("string.format(): Unmatched '{' at index " + i + ".");
				}
				String key = this.value.substring(i + 1, close).trim();
				AussomType val;
				if (key.isEmpty()) {
					// Next positional argument.
					if (autoIndex >= fargs.size()) {
						return new AussomException("string.format(): Not enough arguments for placeholder number " + autoIndex + ".");
					}
					val = fargs.get(autoIndex++);
				} else if (isAllDigits(key)) {
					// Indexed positional argument.
					int idx;
					try {
						idx = Integer.parseInt(key);
					} catch (NumberFormatException nfe) {
						return new AussomException("string.format(): Argument index '" + key + "' out of range.");
					}
					if (idx >= fargs.size()) {
						return new AussomException("string.format(): Argument index " + idx + " out of range.");
					}
					val = fargs.get(idx);
				} else {
					// Named argument, looked up in the first map argument.
					if (named == null || !named.getValue().containsKey(key)) {
						return new AussomException("string.format(): No value provided for named placeholder '" + key + "'.");
					}
					val = named.getValue().get(key);
				}
				// Lists and maps render as compact JSON; str() on those
				// is a multi-line debug form. Everything else (including
				// strings, which JSON would quote) uses its plain str().
				if (val instanceof AussomList || val instanceof AussomMap) {
					AussomType json = ((AussomTypeObjectInt)val).toJson(env, new ArrayList<AussomType>());
					if (json.isEx()) {
						return json;
					}
					sb.append(((AussomTypeInt)json).str());
				} else {
					sb.append(((AussomTypeInt)val).str());
				}
				i = close + 1;
			} else if (ch == '}') {
				// Escaped '}}' -> literal '}'.
				if (i + 1 < len && this.value.charAt(i + 1) == '}') {
					sb.append('}');
					i += 2;
					continue;
				}
				return new AussomException("string.format(): Single '}' at index " + i + "; use '}}' for a literal '}'.");
			} else {
				sb.append(ch);
				i++;
			}
		}
		return new AussomString(sb.toString());
	}

	private static boolean isAllDigits(String s) {
		if (s.isEmpty()) { return false; }
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isDigit(s.charAt(i))) { return false; }
		}
		return true;
	}

	@Override
	public AussomType toJson(Environment env, ArrayList<AussomType> args) {
		return new AussomString("\"" + JSONValue.escape(this.str()) + "\"");
	}
	
	@Override
	public AussomType pack(Environment env, ArrayList<AussomType> args) {
		ArrayList<String> parts = new ArrayList<String>();
		parts.add("\"type\":\"" + this.getTypeName() + "\"");
		parts.add("\"value\":" + this.str(0) + "");
		return new AussomString("{" + Util.join(parts, ",") + "}");
	}
}

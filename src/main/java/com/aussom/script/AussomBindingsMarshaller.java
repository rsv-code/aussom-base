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

package com.aussom.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aussom.types.AussomBool;
import com.aussom.types.AussomDouble;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomList;
import com.aussom.types.AussomMap;
import com.aussom.types.AussomNull;
import com.aussom.types.AussomObject;
import com.aussom.types.AussomString;
import com.aussom.types.AussomType;
import com.aussom.types.cType;

/**
 * Bidirectional value marshalling between JSR 223 host values
 * (java.lang / java.util / Bindings entries) and the Aussom runtime
 * type system (AussomType + subclasses).
 *
 * Centralised here so the engine itself stays small and so future
 * type additions land in one place.
 */
final class AussomBindingsMarshaller {

	private AussomBindingsMarshaller() { }

	/* ---------- Java -> Aussom ---------- */

	@SuppressWarnings({"rawtypes", "unchecked"})
	static AussomType toAussom(Object value) {
		if (value == null) {
			return new AussomNull();
		}
		if (value instanceof AussomType) {
			return (AussomType) value;
		}
		if (value instanceof Boolean) {
			return new AussomBool((Boolean) value);
		}
		if (value instanceof Byte || value instanceof Short
				|| value instanceof Integer || value instanceof Long) {
			return new AussomInt(((Number) value).longValue());
		}
		if (value instanceof Float || value instanceof Double) {
			return new AussomDouble(((Number) value).doubleValue());
		}
		if (value instanceof Character) {
			return new AussomString(value.toString());
		}
		if (value instanceof CharSequence) {
			return new AussomString(value.toString());
		}
		if (value instanceof Map) {
			AussomMap am = new AussomMap();
			for (Object e : ((Map) value).entrySet()) {
				Map.Entry me = (Map.Entry) e;
				String k = me.getKey() == null ? "null" : me.getKey().toString();
				am.put(k, toAussom(me.getValue()));
			}
			return am;
		}
		if (value instanceof List) {
			AussomList al = new AussomList();
			for (Object o : (List) value) {
				al.add(toAussom(o));
			}
			return al;
		}
		if (value.getClass().isArray()) {
			AussomList al = new AussomList();
			Object[] arr;
			if (value instanceof Object[]) {
				arr = (Object[]) value;
			} else {
				int len = java.lang.reflect.Array.getLength(value);
				arr = new Object[len];
				for (int i = 0; i < len; i++) {
					arr[i] = java.lang.reflect.Array.get(value, i);
				}
			}
			for (Object o : arr) {
				al.add(toAussom(o));
			}
			return al;
		}
		// Fallback: wrap as opaque external object on a generic
		// AussomObject. Scripts can pass it back unchanged but cannot
		// introspect members.
		AussomObject ao = new AussomObject();
		ao.setExternObject(value);
		return ao;
	}

	/* ---------- Aussom -> Java ---------- */

	static Object fromAussom(AussomType value) {
		if (value == null) {
			return null;
		}
		cType t = value.getType();
		if (t == null) {
			return value;
		}
		switch (t) {
			case cNull:
				return null;
			case cBool:
				return ((AussomBool) value).getValue();
			case cInt:
				return ((AussomInt) value).getValue();
			case cDouble:
				return ((AussomDouble) value).getValue();
			case cString:
				return ((AussomString) value).getValue();
			case cList: {
				AussomList al = (AussomList) value;
				ArrayList<Object> out = new ArrayList<Object>(al.size());
				for (AussomType v : al.getValue()) {
					out.add(fromAussom(v));
				}
				return out;
			}
			case cMap: {
				AussomMap am = (AussomMap) value;
				LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
				for (Map.Entry<String, AussomType> e : am.getValue().entrySet()) {
					out.put(e.getKey(), fromAussom(e.getValue()));
				}
				return out;
			}
			default:
				// Any other AussomType (object, exception, callback,
				// ...) is returned as-is so hosts that know about
				// Aussom can still introspect it. The common case is
				// AussomObject with an externObject already wrapping
				// the host's original value.
				if (value instanceof AussomObject) {
					Object ext = ((AussomObject) value).getExternObject();
					if (ext != null && !(ext instanceof AussomType)) {
						return ext;
					}
				}
				return value;
		}
	}

	/**
	 * Build an AussomMap mirror of the given JSR 223 Bindings-like
	 * map. Used to pass the host's bindings into the synthetic
	 * eval class as a single 'bindings' member.
	 */
	static AussomMap toAussomMap(Map<String, Object> bindings) {
		AussomMap am = new AussomMap();
		if (bindings == null) return am;
		for (Map.Entry<String, Object> e : bindings.entrySet()) {
			am.put(e.getKey(), toAussom(e.getValue()));
		}
		return am;
	}
}

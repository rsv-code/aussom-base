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

package com.aussom;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aussom.stdlib.ABuffer;
import com.aussom.types.AussomList;
import com.aussom.types.AussomMap;
import com.aussom.types.AussomObject;
import com.aussom.types.AussomString;
import com.aussom.types.AussomType;
import com.aussom.types.Members;

/**
 * Estimates how many bytes a set of Aussom values holds.
 *
 * <p>The JVM will not tell you the live size of one tenant's data, so
 * the engine works it out by walking its own values and adding up a
 * size model. The model is published here rather than hidden, because
 * an estimate whose rules are visible is far more useful than one that
 * claims to be exact:
 *
 * <ul>
 *   <li>Simple value (int, double, bool, null): 24 bytes</li>
 *   <li>String: 40 bytes plus 2 per character</li>
 *   <li>List: 48 bytes plus 8 per slot, plus its elements</li>
 *   <li>Map: 48 bytes plus 48 per entry, plus keys and values</li>
 *   <li>Object: 48 bytes, plus its members</li>
 *   <li>Buffer: 16 bytes plus the real array length</li>
 * </ul>
 *
 * <p>Two properties matter more than the exact numbers. A value held in
 * two places is counted <b>once</b>, which is why the engine measures
 * rather than charging a meter on every store: charging would
 * double-count a string held by two lists. And a structure that
 * contains itself is counted once rather than walked forever.
 *
 * <p>What the model does not cover: the class definitions themselves,
 * anything a host handed in through an extern object other than a
 * buffer, and native or JIT memory, which belongs to nobody.
 *
 * <p>Walk it on an engine that is not running. See
 * Engine.measureRetainedFootprint and
 * design/security-evaluation-f4-f5.md section 5.4.
 *
 * @author austin
 */
public class AussomFootprint {
	/** Bytes charged for a simple value: header plus one field. */
	public static final long SIMPLE_BYTES = 24L;
	/** Bytes charged for a string, before its characters. */
	public static final long STRING_BASE_BYTES = 40L;
	/** Bytes charged per character of a string. */
	public static final long STRING_CHAR_BYTES = 2L;
	/** Bytes charged for a list, before its elements. */
	public static final long LIST_BASE_BYTES = 48L;
	/** Bytes charged per list slot, before the element itself. */
	public static final long LIST_SLOT_BYTES = 8L;
	/** Bytes charged for a map, before its entries. */
	public static final long MAP_BASE_BYTES = 48L;
	/** Bytes charged per map entry, before the key and value. */
	public static final long MAP_ENTRY_BYTES = 48L;
	/** Bytes charged for an object, before its members. */
	public static final long OBJECT_BASE_BYTES = 48L;
	/** Bytes charged for a buffer, before its bytes. */
	public static final long BUFFER_BASE_BYTES = 16L;

	/**
	 * Values already counted, by identity rather than by equality, so
	 * two lists holding the same string add it once and a self
	 * referencing structure terminates.
	 *
	 * This is the one place in the engine that does not use a
	 * ConcurrentHashMap, and identity is the reason: equality would
	 * merge two distinct values that happen to compare equal, and the
	 * whole point of the walk is to count each object once. Thread
	 * safety is not needed here either, since the walk runs on one
	 * thread against an engine that is not running.
	 */
	private final Map<Object, Object> seen = new IdentityHashMap<Object, Object>();

	private long bytes = 0L;

	/**
	 * Adds a value and everything it holds to the total. Null is
	 * ignored, and a value already counted is skipped.
	 * @param Value is the value to walk.
	 */
	public void add(AussomType Value) {
		if (Value == null) return;
		if (this.seen.put(Value, Boolean.TRUE) != null) return;

		if (Value instanceof AussomString) {
			String s = ((AussomString) Value).getValueString();
			long len = 0L;
			if (s != null) len = s.length();
			this.bytes += STRING_BASE_BYTES + (len * STRING_CHAR_BYTES);
			return;
		}

		if (Value instanceof AussomList) {
			AussomList al = (AussomList) Value;
			this.bytes += LIST_BASE_BYTES + ((long) al.size() * LIST_SLOT_BYTES);
			for (AussomType t : al.getValue()) {
				this.add(t);
			}
			this.addMembers(al);
			return;
		}

		if (Value instanceof AussomMap) {
			AussomMap am = (AussomMap) Value;
			ConcurrentHashMap<String, AussomType> m = am.getValue();
			this.bytes += MAP_BASE_BYTES + ((long) m.size() * MAP_ENTRY_BYTES);
			for (Map.Entry<String, AussomType> ent : m.entrySet()) {
				String k = ent.getKey();
				long klen = 0L;
				if (k != null) klen = k.length();
				this.bytes += STRING_BASE_BYTES + (klen * STRING_CHAR_BYTES);
				this.add(ent.getValue());
			}
			this.addMembers(am);
			return;
		}

		if (Value instanceof AussomObject) {
			AussomObject ao = (AussomObject) Value;
			this.bytes += OBJECT_BASE_BYTES;
			Object ext = ao.getExternObject();
			if (ext instanceof ABuffer) {
				byte[] buff = ((ABuffer) ext).getBuffer();
				long blen = 0L;
				if (buff != null) blen = buff.length;
				this.bytes += BUFFER_BASE_BYTES + blen;
			}
			this.addMembers(ao);
			return;
		}

		// Ints, doubles, bools, nulls, callbacks and anything else.
		this.bytes += SIMPLE_BYTES;
	}

	/**
	 * Adds an object's members, when it has any allocated. Lists and
	 * maps extend AussomObject, so they can carry members too.
	 * @param Obj is the object whose members to walk.
	 */
	private void addMembers(AussomObject Obj) {
		Members mem = Obj.getMembersOrNull();
		if (mem == null) return;
		for (Map.Entry<String, AussomType> ent : mem.getMap().entrySet()) {
			String k = ent.getKey();
			long klen = 0L;
			if (k != null) klen = k.length();
			this.bytes += STRING_BASE_BYTES + (klen * STRING_CHAR_BYTES);
			this.add(ent.getValue());
		}
	}

	/**
	 * The estimated bytes walked so far.
	 * @return A long with the estimate.
	 */
	public long getBytes() {
		return this.bytes;
	}

	/**
	 * How many distinct values have been counted. Useful to a host that
	 * would rather budget in values than in estimated bytes, since this
	 * number is exact.
	 * @return An int with the number of values counted.
	 */
	public int getValueCount() {
		return this.seen.size();
	}
}

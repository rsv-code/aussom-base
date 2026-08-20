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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;

import com.aussom.types.AussomBool;
import com.aussom.types.AussomDouble;
import com.aussom.types.AussomException;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomList;
import com.aussom.types.AussomMap;
import com.aussom.types.AussomNull;
import com.aussom.types.AussomString;
import com.aussom.types.AussomType;

/**
 * Default implementation of the security manager. This can be extended
 * to implement custom security manager functionality or properties. This 
 * object is provided in the aussom environment as secman object.
 * @author Austin Lehman
 */
public class SecurityManagerImpl implements SecurityManagerInt {
	/**
	 * Holds the properties for the security manager.
	 */
	protected ConcurrentHashMap<String, Object> props = new ConcurrentHashMap<String, Object>();

	/**
	 * Default constructor adds the standard properties.
	 */
	public SecurityManagerImpl() {
		/*
		 * Security manager itself.
		 */
		// instantiate - can new instances be created from this one? This 
		// normally applies to ones instantiated from within aussom and are blocked 
		// in ASecurityManager sub-class constructor.
		this.props.put("securitymanager.instantiate", true);
		
		// getProp
		this.props.put("securitymanager.property.get", true);
		
		// keySet/getMap
		this.props.put("securitymanager.property.list", true);
		
		// setProp
		this.props.put("securitymanager.property.set", false);
		
		/*
		 *  System information view. See com.aussom.stdlib.ASys.java.
		 */
		this.props.put("os.info.view", false);
		this.props.put("java.info.view", false);
		this.props.put("java.home.view", false);
		this.props.put("java.classpath.view", false);
		this.props.put("aussom.info.view", false);
		this.props.put("aussom.path.view", false);
		this.props.put("current.path.view", false);
		this.props.put("home.path.view", false);
		this.props.put("user.name.view", false);
		
		/*
		 *  Reflection actions. See com.aussom.stdlib.AReflect.java.
		 */
		this.props.put("reflect.eval.string", false);
		this.props.put("reflect.eval.file", false);
		this.props.put("reflect.include.module", false);
		// Discards collected parse diagnostics, so it is gated with
		// the other reflect actions that change engine state.
		// Reading them back (reflect.parseDiagnostics) is not gated,
		// matching the other read-only reflect accessors.
		this.props.put("reflect.clear.diagnostics", false);

		/*
		 * Aussomdoc actions. See com.aussom.stdlib.ALang.java.
		 */
		this.props.put("aussomdoc.file.getJson", false);
		this.props.put("aussomdoc.class.getJson", false);

		// Keep doc comment text on the AST. Unlike the two above this
		// is parse behaviour rather than an action gate. False drops
		// the text as each doc comment is parsed, which saves roughly
		// 84 KB per engine and blanks doc text for every consumer:
		// -d output, reflect, and any language tooling built on them.
		// True is the default because that loss is silent.
		this.props.put("aussomdoc.retain", true);

		/*
		 * Unit testing actions.
		 */
		this.props.put("test.aussom.runner", false);
		this.props.put("test.mock.inject", false);
		this.props.put("test.mock.spy", false);

		/*
		 * Script mode actions. See com.aussom.Engine.setScriptMode
		 * and com.aussom.Engine.evalLine.
		 */
		this.props.put("aussom.script.mode.enable", false);

		/*
		 * Debugger attach. See com.aussom.Engine.setDebugger.
		 * Gates whether an external client (DAP server, custom
		 * debug REPL, test harness) is allowed to register a
		 * DebuggerInt and turn on the engine's debug mode.
		 */
		this.props.put("aussom.debugger.enable", false);

		/*
		 * Extern class binding. See com.aussom.Engine.isExternClassAllowed.
		 * With enforce false the list is ignored and a script may name any
		 * class the system class loader can see, which is the historical
		 * behavior. With it true every 'extern class' declaration is
		 * checked, including the ones in the standard library, so the list
		 * is a complete statement of what this engine may bind.
		 *
		 * The default list is what the shipped modules need: every extern
		 * class in the base modules lives in one of these two packages.
		 * An entry is either an exact class name or a 'pkg.*' prefix that
		 * admits that package and everything under it.
		 */
		this.props.put("aussom.extern.allowlist.enforce", false);
		this.props.put("aussom.extern.allowed",
			Arrays.asList("com.aussom.stdlib.*", "com.aussom.types.*"));

		/*
		 * Runtime settings read by com.aussom.Limits. Four numbers, each
		 * bounding something the JVM will not bound on its own. See
		 * design/security-evaluation-f4-f5.md section 5.
		 *
		 * There are deliberately no per-value size caps here. A cap on the
		 * length of one string, the elements of one list, or the bytes of
		 * one buffer bounds neither memory nor anything else a host can
		 * reason about: element count is not memory, one value's cap says
		 * nothing about many values, and a single allocation larger than
		 * the heap is refused cleanly by the JVM anyway. Sustained
		 * retention is the real risk, and that is what
		 * Engine.getAllocatedBytes, Engine.measureRetainedFootprint and a
		 * host deadline are for.
		 *
		 * The call depth default of 1000 is set above what a program can
		 * reach on a default 1 MB thread stack, which measures out at
		 * roughly 550 Aussom frames, so switching it on cannot refuse a
		 * program that ran before. It becomes the operative limit as soon
		 * as the host gives the interpreter a bigger stack or runs it on a
		 * virtual thread.
		 */
		this.props.put(Limits.CALL_DEPTH_PROP, Limits.DEFAULT_CALL_DEPTH);

		// 0 means no budget, so regex behaviour is unchanged unless a host
		// asks for one. See com.aussom.stdlib.RegexSubject.
		this.props.put(Limits.REGEX_STEPS_PROP, 0L);

		/*
		 * How long a sleeping program may run before the interpreter looks
		 * at the engine's control state again. This is a control setting
		 * rather than a size cap, and it lives here for that reason: a
		 * host should be able to read and change its whole control policy
		 * in one place instead of finding part of it compiled into
		 * sys.sleep().
		 *
		 * The default reclaims a sleeping tenant within a twentieth of a
		 * second. 0 turns slicing off, which restores the older behavior
		 * of one uninterruptible wait, so a pause or cancel cannot reach
		 * the program until the sleep finishes on its own.
		 */
		this.props.put(Limits.SLEEP_SLICE_PROP, Limits.DEFAULT_SLEEP_SLICE_MS);

		/*
		 * Largest source file the engine will parse, 0 for no limit, so
		 * nothing is refused unless a host asks for it. This is the one
		 * parse cost worth bounding: a file is read whole into memory
		 * before the parser sees a token, and what it becomes measures out
		 * at roughly 37 times the source. It is checked against the file
		 * length before the read, and it applies to files only, never to
		 * source handed in as a string, so it cannot refuse the standard
		 * library. See design/security-evaluation-g1-g3.md.
		 */
		this.props.put(Limits.SOURCE_BYTES_PROP, 0L);

		/*
		 * Whether an include may be reached through a symbolic link.
		 * Default true, which is what the engine has always done and is
		 * frequently deliberate: a shared module directory or a versioned
		 * library linked into a root. A host running untrusted tenants,
		 * whose include root is a directory those tenants can write to,
		 * sets it false to say that modules come from inside the root and
		 * nothing there may point elsewhere. See Engine.addInclude.
		 */
		this.props.put("aussom.include.symlink.follow", true);
	}
	
	/**
	 * Java get property.
	 */
	@Override
	public Object getProperty(String PropName) {
		return this.props.get(PropName);
	}

	/*
	 * ============================================================
	 * Typed property reads
	 *
	 * These are what gates use. Reading getProperty and casting the
	 * result is how a missing property becomes a
	 * NullPointerException instead of an answer, and a policy
	 * question must always have an answer. See F7 in
	 * design/security-evaluation-f6-f9.md.
	 *
	 * Two shapes. The forms that take a default value answer with it
	 * when the property is missing or is not of the type asked for.
	 * The forms that do not take one answer null on no match, except
	 * where the return type is a primitive and cannot be null; there
	 * the zero value is the answer, which for a permission read means
	 * denied.
	 *
	 * No value is ever converted. A property of the wrong type is not a
	 * match, full stop: the string "5" is not an integer here, and a
	 * number is not a string. Guessing what a loosely stored value
	 * meant is how policy ends up deciding something nobody wrote down.
	 *
	 * All of them read through getProperty rather than the props map,
	 * so a subclass that overrides getProperty to compute a value is
	 * honored here too.
	 * ============================================================
	 */

	/**
	 * Boolean property with a caller-supplied default.
	 *
	 * Only a real Boolean answers the question. The string "true" does
	 * not grant a permission: a value stored loosely should not decide
	 * what a script is allowed to do.
	 *
	 * @param PropName is the property name to read.
	 * @param DefaultValue is the value to use on no match.
	 * @return A boolean with the property value, or DefaultValue.
	 */
	@Override
	public boolean getPropertyBoolean(String PropName, boolean DefaultValue) {
		Object o = this.getProperty(PropName);
		if (o instanceof Boolean) {
			return ((Boolean) o).booleanValue();
		}
		return DefaultValue;
	}

	/**
	 * Integer property with a caller-supplied default.
	 *
	 * An integer value is one stored as an integer: Long, Integer, Short
	 * or Byte. A host that writes props.put("k", 5) stores an Integer
	 * while a script setting the same key stores a Long, so both read. A
	 * Double, a String or anything else is not a match and answers
	 * DefaultValue. Nothing is parsed or truncated.
	 *
	 * @param PropName is the property name to read.
	 * @param DefaultValue is the value to use on no match.
	 * @return A long with the property value, or DefaultValue.
	 */
	@Override
	public long getPropertyInt(String PropName, int DefaultValue) {
		Object o = this.getProperty(PropName);
		if (o instanceof Long || o instanceof Integer
				|| o instanceof Short || o instanceof Byte) {
			return ((Number) o).longValue();
		}
		return DefaultValue;
	}

	/**
	 * Double property with a caller-supplied default.
	 *
	 * A Double or a Float reads. An integer value is not a match, and
	 * neither is a string: nothing is widened or parsed.
	 * @param PropName is the property name to read.
	 * @param DefaultValue is the value to use on no match.
	 * @return A double with the property value, or DefaultValue.
	 */
	@Override
	public double getPropertyDouble(String PropName, double DefaultValue) {
		Object o = this.getProperty(PropName);
		if (o instanceof Double || o instanceof Float) {
			return ((Number) o).doubleValue();
		}
		return DefaultValue;
	}

	/**
	 * String property with a caller-supplied default.
	 * @param PropName is the property name to read.
	 * @param DefaultValue is the value to use on no match.
	 * @return A String with the property value, or DefaultValue.
	 */
	@Override
	public String getPropertyString(String PropName, String DefaultValue) {
		Object o = this.getProperty(PropName);
		if (o instanceof String) {
			return (String) o;
		}
		return DefaultValue;
	}

	/**
	 * List property, null when the property is missing or is not a
	 * collection.
	 *
	 * The returned list is a copy, so a caller cannot reach back into
	 * policy through a value it was handed. That is the same rule
	 * toAussom follows for the values it hands to a script.
	 *
	 * @param PropName is the property name to read.
	 * @return A List with the property values, or null.
	 */
	@Override
	public List<Object> getPropertyList(String PropName) {
		Object o = this.getProperty(PropName);
		if (!(o instanceof Collection)) {
			return null;
		}
		List<Object> out = new ArrayList<Object>();
		for (Object entry : (Collection<?>) o) {
			out.add(entry);
		}
		return out;
	}

	/**
	 * Map property, null when the property is missing or is not a map.
	 *
	 * The returned map is a copy, for the same reason getPropertyList
	 * returns one. An entry whose key is not a String is dropped rather
	 * than renamed: a key is not converted any more than a value is.
	 *
	 * @param PropName is the property name to read.
	 * @return A Map with the property values, or null.
	 */
	@Override
	public Map<String, Object> getPropertyMap(String PropName) {
		Object o = this.getProperty(PropName);
		if (!(o instanceof Map)) {
			return null;
		}
		Map<String, Object> out = new ConcurrentHashMap<String, Object>();
		for (Map.Entry<?, ?> ent : ((Map<?, ?>) o).entrySet()) {
			if (ent.getKey() instanceof String) {
				out.put((String) ent.getKey(), ent.getValue());
			}
		}
		return out;
	}

	/**
	 * Aussom getProperty. This method will get the property, match it to a 
	 * standard AussomType and return it. If property 
	 * securitymanager.property.get is set to false this method will 
	 * throw a security exception. 
	 */
	@Override
	public AussomType getProp(Environment env, ArrayList<AussomType> args) {
		if (this.getPropertyBoolean("securitymanager.property.get", false)) {
			String PropName = ((AussomString)args.get(0)).getValueString();
			return this.toAussom(this.props.get(PropName));
		} else {
			return new AussomException("securitymanager.getProp(): Security exception, action 'securitymanager.property.get' not permitted.");
		}
	}
	
	/**
	 * Gets the key set of the properties as a list of strings.
	 */
	@Override
	public AussomType keySet(Environment env, ArrayList<AussomType> args) {
		if (this.getPropertyBoolean("securitymanager.property.list", false)) {
			AussomList cl = new AussomList();
			for (String key : this.props.keySet()) {
				cl.add(new AussomString(key));
			}
			return cl;
		} else {
			return new AussomException("securitymanager.keySet(): Security exception, action 'securitymanager.property.list' not permitted.");
		}
	}
	
	/**
	 * Gets a aussom map of the security manager properties and their values.
	 */
	@Override
	public AussomType getMap(Environment env, ArrayList<AussomType> args) {
		if (this.getPropertyBoolean("securitymanager.property.list", false)) {
			AussomMap cm = new AussomMap();
			for (String key : this.props.keySet()) {
				cm.put(key, this.toAussom(this.props.get(key)));
			}
			return cm;
		} else {
			return new AussomException("securitymanager.getMap(): Security exception, action 'securitymanager.property.list' not permitted.");
		}
	}
	
	/**
	 * Aussom setProp. This method provides the ability 
	 * to set the property of a security manager property pair. If property 
	 * securitymanager.property.set is set to false this method will 
	 * throw a security exception. 
	 */
	@Override
	public AussomType setProp(Environment env, ArrayList<AussomType> args) {
		if (this.getPropertyBoolean("securitymanager.property.set", false)) {
			String key = ((AussomString)args.get(0)).getValueString();
			AussomType ct = args.get(1);
			if (!this.storable(ct)) {
				return new AussomException("securitymanager.setProp(): Expecting simple type, list or map but found '" + ct.getClass().getName() + "' instead.");
			}
			this.putOrRemove(key, this.toJava(ct));
			return env.getClassInstance();
		} else {
			return new AussomException("securitymanager.getProp(): Security exception, action 'securitymanager.property.set' not permitted.");
		}
	}
	
	/**
	 * Aussom setMap. This method provides the ability 
	 * to set a whole map of key-val pairs. If property 
	 * securitymanager.property.set is set to false this method will 
	 * throw a security exception. 
	 */
	@Override
	public AussomType setMap(Environment env, ArrayList<AussomType> args) {
		if (this.getPropertyBoolean("securitymanager.property.set", false)) {
			AussomMap mp = (AussomMap)args.get(0);
			for (String key : mp.getValue().keySet()) {
				AussomType ct = mp.getValue().get(key);
				if (!this.storable(ct)) {
					return new AussomException("securitymanager.setMap(): Expecting simple type, list or map but found '" + ct.getClass().getName() + "' instead.");
				}
			}
			for (String key : mp.getValue().keySet()) {
				this.putOrRemove(key, this.toJava(mp.getValue().get(key)));
			}
			return env.getClassInstance();
		} else {
			return new AussomException("securitymanager.getProp(): Security exception, action 'securitymanager.property.set' not permitted.");
		}
	}

	/**
	 * Converts a stored property value into its Aussom form. Shared by
	 * getProp and getMap, which previously carried this chain twice and
	 * disagreed on values that are not simple types.
	 *
	 * <p>Builds new collections rather than wrapping the stored ones, so
	 * a script cannot reach back into policy through a value it was
	 * handed.
	 *
	 * @param Value is the stored value to convert.
	 * @return A AussomType with the converted value.
	 */
	protected AussomType toAussom(Object Value) {
		if (Value == null) {
			return new AussomNull();
		} else if (Value instanceof Boolean) {
			return new AussomBool((Boolean)Value);
		} else if (Value instanceof String) {
			return new AussomString((String)Value);
		} else if (Value instanceof Float || Value instanceof Double) {
			return new AussomDouble(((Number)Value).doubleValue());
		} else if (Value instanceof Number) {
			// Covers Integer and Short as well as Long. An embedder
			// writing props.put("k", 5) stores an Integer, which used
			// to read back as the string "5".
			return new AussomInt(((Number)Value).longValue());
		} else if (Value instanceof Collection) {
			AussomList cl = new AussomList();
			for (Object o : (Collection<?>)Value) {
				cl.add(this.toAussom(o));
			}
			return cl;
		} else if (Value instanceof Map) {
			AussomMap cm = new AussomMap();
			for (Map.Entry<?, ?> ent : ((Map<?, ?>)Value).entrySet()) {
				cm.put(String.valueOf(ent.getKey()), this.toAussom(ent.getValue()));
			}
			return cm;
		}
		return new AussomString(Value.toString());
	}

	/**
	 * Checks whether an Aussom value can be stored as a property.
	 * Anything outside the supported set is refused rather than stored,
	 * so an object or callback cannot be smuggled into the policy map.
	 * @param Value is the Aussom value to check.
	 * @return A boolean with true for storable and false for not.
	 */
	protected boolean storable(AussomType Value) {
		if (Value == null || Value instanceof AussomNull) {
			return true;
		} else if (Value instanceof AussomBool || Value instanceof AussomInt
				|| Value instanceof AussomDouble || Value instanceof AussomString) {
			return true;
		} else if (Value instanceof AussomList) {
			for (AussomType t : ((AussomList)Value).getValue()) {
				if (!this.storable(t)) {
					return false;
				}
			}
			return true;
		} else if (Value instanceof AussomMap) {
			for (AussomType t : ((AussomMap)Value).getValue().values()) {
				if (!this.storable(t)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Converts an Aussom value into its stored form. Copies into fresh
	 * Java collections so a script cannot hold a reference and edit
	 * policy after the write was permitted. Call storable first; this
	 * returns null for anything it does not recognize.
	 * @param Value is the Aussom value to convert.
	 * @return An Object with the value to store.
	 */
	protected Object toJava(AussomType Value) {
		if (Value == null || Value instanceof AussomNull) {
			return null;
		} else if (Value instanceof AussomBool) {
			return ((AussomBool)Value).getValue();
		} else if (Value instanceof AussomInt) {
			return ((AussomInt)Value).getValue();
		} else if (Value instanceof AussomDouble) {
			return ((AussomDouble)Value).getValue();
		} else if (Value instanceof AussomString) {
			return ((AussomString)Value).getValueString();
		} else if (Value instanceof AussomList) {
			List<Object> out = new ArrayList<Object>();
			for (AussomType t : ((AussomList)Value).getValue()) {
				out.add(this.toJava(t));
			}
			return out;
		} else if (Value instanceof AussomMap) {
			Map<String, Object> out = new ConcurrentHashMap<String, Object>();
			for (Map.Entry<String, AussomType> ent : ((AussomMap)Value).getValue().entrySet()) {
				out.put(ent.getKey(), this.toJava(ent.getValue()));
			}
			return out;
		}
		return null;
	}

	/**
	 * Stores a converted value. The backing map is a ConcurrentHashMap,
	 * which rejects null values, so a null is recorded by removing the
	 * key. getProperty then returns null either way.
	 * @param Key is the property name.
	 * @param Value is the converted value, possibly null.
	 */
	private void putOrRemove(String Key, Object Value) {
		if (Value == null) {
			this.props.remove(Key);
		} else {
			this.props.put(Key, Value);
		}
	}

}

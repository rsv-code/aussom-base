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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
	}
	
	/**
	 * Java get property.
	 */
	@Override
	public Object getProperty(String PropName) {
		return this.props.get(PropName);
	}

	/**
	 * Aussom getProperty. This method will get the property, match it to a 
	 * standard AussomType and return it. If property 
	 * securitymanager.property.get is set to false this method will 
	 * throw a security exception. 
	 */
	@Override
	public AussomType getProp(Environment env, ArrayList<AussomType> args) {
		if ((Boolean)this.getProperty("securitymanager.property.get")) {
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
		if ((Boolean)this.getProperty("securitymanager.property.list")) {
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
		if ((Boolean)this.getProperty("securitymanager.property.list")) {
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
		if ((Boolean)this.getProperty("securitymanager.property.set")) {
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
		if ((Boolean)this.getProperty("securitymanager.property.set")) {
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
			Map<String, Object> out = new LinkedHashMap<String, Object>();
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

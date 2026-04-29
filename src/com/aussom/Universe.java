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

package com.aussom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.aussom.ast.astClass;
import com.aussom.ast.aussomException;
import com.aussom.stdlib.Lang;

/**
 * Univers singleton object manages program global objects for Aussom Engines. Engines 
 * are setup in a manner that allows for multiple Engine instances per program. The Univers 
 * provides a global mechanism for all of the Engines.
 * @author austin
 */
public class Universe {
	/**
	 * The single Universe instance.
	 */
	static Universe instance = null;
	
	/**
	 * Flag defining if the universe has completed initialization yet or not.
	 */
	private boolean initialized = false;
	
	/**
	 * Defines the Aussom version.
	 */
	private static final String version = "1.2.0";
	
	/**
	 * Map of class definitions. This is used to hold the base lang clases. It allows
	 * them to be parsed once and coppied to any subsequent Engine object when needed.
	 */
	private Map<String, astClass> classes = new ConcurrentHashMap<String, astClass>();

	/*
	 * Hot-path cache for the primitive type class defs. Every
	 * primitive-type constructor (AussomNull, AussomInt, ...) needs
	 * its class def on construction. Reading these direct field
	 * references is much cheaper than the ConcurrentHashMap.get()
	 * the older code path required, and primitive constructors are
	 * by far the most-called bit of the runtime. Populated by init()
	 * after lang.aus has been parsed; null before then.
	 */
	public astClass NULL_CLASS_DEF = null;
	public astClass BOOL_CLASS_DEF = null;
	public astClass INT_CLASS_DEF = null;
	public astClass DOUBLE_CLASS_DEF = null;
	public astClass STRING_CLASS_DEF = null;
	public astClass LIST_CLASS_DEF = null;
	public astClass MAP_CLASS_DEF = null;
	public astClass OBJECT_CLASS_DEF = null;
	public astClass CALLBACK_CLASS_DEF = null;
	public astClass EXCEPTION_CLASS_DEF = null;

	/**
	 * Default constructor set to private to defeat instantiation. See get to get an
	 * instance of the object.
	 */
	private Universe() { }

	/**
	 * Initializes the Universe object with the provided Engine. This function will use
	 * the provided engine to Parse the Lang.langSrc code if not already initialized.
	 * @param eng is an Engine object.
	 * @throws Exception on parse error.
	 */
	public void init(Engine eng) throws Exception {
		if (!this.initialized) {
			// Load native class definitions here!
			eng.parseString("lang.aus", Lang.get().getLangIncludes().get("lang.aus"));
			// Need to deep copy otherwise additions in the engine will
			// be set here as well.
			for (String cls : eng.getClasses().keySet()) {
				astClass c = eng.getClassByName(cls);
				this.classes.put(cls, c);
			}
			// Populate the primitive class-def cache. The map lookup
			// happens once here instead of once per primitive
			// construction.
			this.NULL_CLASS_DEF      = this.classes.get("cnull");
			this.BOOL_CLASS_DEF      = this.classes.get("bool");
			this.INT_CLASS_DEF       = this.classes.get("int");
			this.DOUBLE_CLASS_DEF    = this.classes.get("double");
			this.STRING_CLASS_DEF    = this.classes.get("string");
			this.LIST_CLASS_DEF      = this.classes.get("list");
			this.MAP_CLASS_DEF       = this.classes.get("map");
			this.OBJECT_CLASS_DEF    = this.classes.get("object");
			this.CALLBACK_CLASS_DEF  = this.classes.get("callback");
			this.EXCEPTION_CLASS_DEF = this.classes.get("exception");
			this.initialized = true;
		}
	}
	
	/**
	 * Gets a handle of the Universe object.
	 * @return An instance of the Universe object.
	 */
	public static Universe get() {
		if(instance == null) instance = new Universe();
		return instance;
	}
	
	/**
	 * Gets the Map of lang class definitions.
	 * @return a Map object with class definitions.
	 */
	public Map<String, astClass> getClasses() {
		return this.classes;
	}
	
	/**
	 * Gets the class definition of the provided class name.
	 * Performs a single hash lookup and treats null as "not found"
	 * rather than the older containsKey-then-get pattern, which did
	 * the same lookup work twice. This method is on the hot path —
	 * every primitive-type constructor calls it on construction.
	 * @param Name is a String with the class name to get.
	 * @return A astClass class definition.
	 * @throws aussomException if class not found with the provided name.
	 */
	public astClass getClassDef(String Name) throws aussomException {
		astClass def = this.classes.get(Name);
		if (def != null) return def;
		throw new aussomException("Aussom Universe: Freaking out, can't find requested class def '" + Name + "'.");
	}
	
	/**
	 * Gets the Aussom version.
	 * @return A String with the Aussom version.
	 */
	public static String getAussomVersion() {
		return version;
	}
}

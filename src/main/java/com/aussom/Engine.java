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

import java.io.File;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.aussom.ast.*;
import com.aussom.stdlib.LangRegistry;
import com.aussom.types.*;

/**
 * Engine object represents a Aussom interpreter instance. Then engine handles 
 * parsing files and strings, storing includes/classes and running Aussom 
 * code.
 * @author austin
 */
public class Engine implements AussomDebuggingInt {
	/**
	 * Defines the run mode of then engine. When set to DOC some
	 * errors are ignored like missing includes.
	 */
	protected EngineRunMode engineRunMode = EngineRunMode.NORMAL;
	/**
	 * The security manager instance for this engine.
	 */
	private SecurityManagerInt secman = null;

	/**
	 * Where this engine writes its output. Owned by the engine rather
	 * than by the calling thread, so two engines in one JVM never
	 * cross-route their output and an engine driven from a pool of
	 * threads logs to the same place from all of them. Volatile
	 * because an embedder may swap the logger while the engine is
	 * running (the JSR 223 wrapper does exactly this around eval).
	 * Never null; setLogger(null) restores the default.
	 * See design/multitenancy-safety.md section 7.3.
	 */
	private volatile LoggingInt logger = new DefaultLoggingImpl();

	/**
	 * This flag is used to when the Engine parses an
	 * input file if it should load the extern class. Most
	 * of the time we do but situations where we don't is when
	 * we are generating docs from source tree.
	 */
	private boolean loadExternClasses = true;
	
	/**
	 * Stores the file names of any included Aussom code files.
	 */
	private List<String> fileNames = new ArrayList<String>();
	
	/**
	 * Flag for initialization is complete. This is set to true once 
	 * the base aussom object have been parsed. Once set to true calls to 
	 * addClass will instantiate static classes right away.
	 */
	private boolean initComplete = false;
	
	/*
	 * Main class.
	 */
	private boolean mainClassFound = false;
	private astClass mainClassDef = null;
	private AussomObject mainClassInstance = null;
	
	/*
	 * Main function.
	 */
	private boolean mainFunctFound = false;
	private astFunctDef mainFunctDef = null;
	private AussomList mainFunctArgs = new AussomList(false);
	private CallStack mainCallStack = new CallStack();

	/**
	 * Estimated bytes this engine's parsed class definitions hold.
	 * Accumulated as each file is parsed rather than walked when a
	 * measurement is asked for, so measuring costs nothing extra and
	 * parsing pays once. Parsing only ever adds definitions, so the
	 * total does not need invalidating.
	 */
	private final AtomicLong classDefBytes = new AtomicLong(0L);
	
	/**
	 * This flag is set if the parser encounters errors. This is used 
	 * when parsing the initial source code prior to running the application. If 
	 * set to true the interpreter will fail to start.
	 */
	private boolean hasParseErrors = false;

	/**
	 * Structured parse diagnostics collected during parsing. These
	 * carry the source position as integers so tooling does not have
	 * to recover it with regular expressions over the console
	 * output. Populated alongside -- never instead of -- the existing
	 * console messages. See design/error-reporting-fix.md.
	 */
	private List<ParseDiagnostic> parseDiagnostics = new ArrayList<ParseDiagnostic>();

	/**
	 * Allowed resource include paths. These are includes that are
	 * located within the JAR package.
	 */
	private List<String> resourceIncludePaths = new ArrayList<String>();
	
	/**
	 * Allowed include paths.
	 */
	private List<String> includePaths = new ArrayList<String>();

	/**
	 * Require exclude paths. This allows setting directories
	 * that are off limits.
	 */
	private List<String> excludePaths = new ArrayList<String>();
	
	/**
	 * List of Aussom includes.
	 */
	private List<String> includes = new ArrayList<String>();
	
	/*
	 * Class objects storage for engine.
	 */
	private Map<String, astClass> classes = new ConcurrentHashMap<String, astClass>();
	private Map<String, AussomType> staticClasses = new ConcurrentHashMap<String, AussomType>();

	/*
	 * Script-mode state. See setScriptMode, evalLine, parseStatements,
	 * getScriptClass, and design/script-mode-design.md. Independent of
	 * the classical run path; the synthetic class is deliberately NOT
	 * registered in this.classes.
	 */
	public static final String SCRIPT_CLASS_NAME = "__script_main";
	private boolean scriptMode = false;
	private astClass scriptClass = null;
	// Direct reference to the synthetic main(args) astFunctDef. The
	// arg is an untyped wildcard so the dispatcher routes the def to
	// wildcardOverloads rather than dispatchMap; getFunct("main", "*")
	// would not find it. Keeping a direct reference avoids that
	// indirection entirely.
	private astFunctDef scriptMainFn = null;
	private AussomObject scriptInstance = null;
	private Environment scriptEnv = null;
	// Filename reported on AST nodes parsed by evalLine. Embedders set
	// this via setScriptFileName so error attribution points at the
	// original source file. Defaults to "<script>".
	private String scriptFileName = "<script>";
	// Index of the next not-yet-evaluated statement in scriptClass's
	// main body. Advanced by evalLine to body.size() at the end of each
	// call so statements from a prior call are never re-walked.
	private int scriptCursor = 0;

	/*
	 * Debugging state. See design/debugging-interface-design.md.
	 *
	 * debugMode is a plain (non-volatile) boolean because it is set
	 * once before any interpreter thread starts and never changes
	 * after. Plain boolean lets the JIT fold the gated debug block
	 * out of the production hot path. Attaching a debugger to an
	 * already-running interpreter is not supported.
	 *
	 * debugger is volatile because it is the live reference during
	 * a debug session and may be swapped (hot-swap, detach,
	 * replace) while interpreter threads are running. The volatile
	 * read costs nothing in production because the surrounding
	 * isDebugMode() block folds away when debugMode is false.
	 *
	 * lastSeenThrowable is the per-thread "last seen" used by the
	 * post-eval exception hook to fire onException(Exception, ...)
	 * exactly once per logical throw rather than once per stack
	 * frame the throwable unwinds through. Only touched inside the
	 * gated catch block; zero cost in production.
	 */
	private boolean debugMode = false;
	private volatile DebuggerInt debugger = null;
	private final ThreadLocal<Throwable> lastSeenThrowable = new ThreadLocal<Throwable>();

	/*
	 * Control state. Set by cancel(), pause() and resume() from any
	 * thread and read at every checkpoint: loop back edges, every
	 * Aussom call, every batch of regex subject reads, and every sleep
	 * slice. Volatile because the thread asking for the change is never
	 * the interpreter thread. See the "Control: cancel, pause and
	 * resume" section further down for the full contract.
	 */
	private volatile ControlState controlState = ControlState.RUNNING;

	/*
	 * Monitor for pausing. Interpreter threads park on it while the
	 * state is PAUSED, and pause/resume/cancel and awaitPaused all
	 * synchronize on it. Separate from the engine itself so an
	 * embedder that synchronizes on the Engine cannot deadlock the
	 * interpreter.
	 */
	private final Object controlLock = new Object();

	/*
	 * How many registered interpreter threads are currently parked at a
	 * checkpoint. Guarded by controlLock. isFullyPaused compares it
	 * against the number of registered threads.
	 */
	private int stoppedThreads = 0;

	/*
	 * Resource limits for this engine, read from the security manager.
	 * Volatile and replaced wholesale rather than mutated, so a reader
	 * always sees a consistent set. Refreshed in the constructor and at
	 * the start of every program. See Limits.
	 */
	private volatile Limits limits = new Limits();

	/*
	 * The call depth limit, kept as a plain int beside the Limits
	 * snapshot. The depth check runs once per Aussom call, and reading
	 * the limit off the snapshot means a second volatile read plus a
	 * long compare and a narrowing on the hottest path in the
	 * interpreter. Maintained by refreshLimits, which is the only place
	 * that changes it.
	 */
	private volatile int maxCallDepth = (int) Limits.DEFAULT_CALL_DEPTH;


	/*
	 * Accounting. Threads currently running this engine's code, keyed
	 * by thread id, each holding the CPU and allocation counters read
	 * when it entered. bankedCpuNanos and bankedAllocBytes hold the
	 * totals from threads that have already left. See ThreadMeter and
	 * design/security-evaluation-f4-f5.md section 5.5.
	 */
	private final Map<Long, ThreadScope> interpreterThreads = new ConcurrentHashMap<Long, ThreadScope>();
	private final AtomicLong bankedCpuNanos = new AtomicLong(0L);
	private final AtomicLong bankedAllocBytes = new AtomicLong(0L);

	/**
	 * The standard library modules this engine can include. Owned by
	 * the engine rather than shared, so two engines can be given
	 * different standard libraries. See design/multitenancy-safety.md
	 * section 7.4.
	 */
	private final LangRegistry langRegistry;

	/*
	 * Hot-path cache for the primitive type class defs. Every
	 * primitive-type dispatch needs its class def, and reading a
	 * direct field reference is cheaper than the ConcurrentHashMap
	 * lookup the general path requires. Populated by
	 * parseLangSource() once lang.aus has been parsed into this
	 * engine's table. These point at this engine's own definitions,
	 * never at another engine's.
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
	 * Default constructor. Parses the base language classes into this
	 * engine's own class table and instantiates its static classes.
	 * @throws Exception on failure to instantiate SecurityManagerImpl object.
	 */
	public Engine () throws Exception {
		this(new SecurityManagerImpl());
	}

	/**
	 * Builds an engine with the provided security manager and the base
	 * standard library. Parses lang.aus into this engine's own class
	 * table, instantiates its static classes, then sets the
	 * initComplete flag to true.
	 * @param SecMan is a SecurityManagerImpl object for the engine.
	 * @throws Exception on init failure or failure to instantiate static classes.
	 */
	public Engine(SecurityManagerInt SecMan) throws Exception {
		this(SecMan, new LangRegistry());
	}

	/**
	 * Builds an engine with the provided security manager and standard
	 * library registry. The engine takes its own copy of the registry,
	 * so a later change to the caller's copy does not alter what this
	 * engine can include.
	 *
	 * <p>Every engine parses lang.aus for itself. That is what makes
	 * two engines in one JVM independent: no class definition object is
	 * ever shared, so nothing one engine does to a definition can be
	 * seen by another. See design/multitenancy-safety.md section 7.1.
	 *
	 * @param SecMan is a SecurityManagerImpl object for the engine.
	 * @param Registry is the LangRegistry of standard library modules.
	 * @throws Exception on init failure or failure to instantiate static classes.
	 */
	public Engine(SecurityManagerInt SecMan, LangRegistry Registry) throws Exception {
		this.secman = SecMan;
		this.langRegistry = new LangRegistry(Registry);

		// Resource limits come from policy. Read once here and again at
		// the start of each program, so the interpreter never pays for a
		// property lookup on the hot path. See Limits.
		this.refreshLimits();

		// Parse the base language classes into this engine's table.
		this.parseLangSource();

		// Instantiate the static classes.
		this.instantiateStaticClasses();

		this.initComplete = true;
	}

	/**
	 * Parses lang.aus into this engine's class table and caches the
	 * primitive class definitions for the dispatch hot path.
	 * @throws Exception on parse failure.
	 */
	private void parseLangSource() throws Exception {
		this.parseString("lang.aus", this.langRegistry.get("lang.aus"));

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
	}

	/**
	 * The Aussom version, read once from the jar manifest.
	 */
	private static final String VERSION = lookupVersion();

	/**
	 * Reads the project version from the jar manifest's
	 * Implementation-Version attribute, which the jar and assembly
	 * plugins populate from the pom version. Returns "dev" when the
	 * attribute is missing, which is the case when running from a
	 * build directory rather than a packaged jar.
	 *
	 * <p>This replaced a hand-maintained constant that had drifted:
	 * it still read 1.2.10 while the artifact was on 1.3.6.
	 *
	 * @return The pom version when running from a packaged jar, "dev" otherwise.
	 */
	private static String lookupVersion() {
		String v = Engine.class.getPackage().getImplementationVersion();
		if (v == null) {
			return "dev";
		}
		return v;
	}

	/**
	 * Gets the Aussom version.
	 * @return A String with the Aussom version.
	 */
	public static String getAussomVersion() {
		return VERSION;
	}

	/**
	 * Gets this engine's class definition for a primitive type.
	 *
	 * <p>Primitives do not carry a class definition, so dispatch
	 * resolves it here. Each engine answers with its own definition,
	 * which is what keeps two engines in one JVM from sharing one.
	 * Returns null for a type that has no primitive definition, which
	 * lets callers fall through to their existing not-found handling.
	 *
	 * @param Type is the cType to resolve.
	 * @return The astClass for that type, or null when there is none.
	 */
	public astClass getPrimitiveClassDef(cType Type) {
		if (Type == null) return null;
		switch (Type) {
			case cBool:      return this.BOOL_CLASS_DEF;
			case cInt:       return this.INT_CLASS_DEF;
			case cDouble:    return this.DOUBLE_CLASS_DEF;
			case cString:    return this.STRING_CLASS_DEF;
			case cList:      return this.LIST_CLASS_DEF;
			case cMap:       return this.MAP_CLASS_DEF;
			case cNull:      return this.NULL_CLASS_DEF;
			case cCallback:  return this.CALLBACK_CLASS_DEF;
			case cException: return this.EXCEPTION_CLASS_DEF;
			case cObject:    return this.OBJECT_CLASS_DEF;
			default:         return null;
		}
	}

	/**
	 * Gets the standard library registry for this engine.
	 * @return The LangRegistry this engine includes from.
	 */
	public LangRegistry getLangRegistry() {
		return this.langRegistry;
	}

	/**
	 * Registers a standard library module on this engine only.
	 * @param Name is the include name, for example {@code "http.aus"}.
	 * @param Source is the Aussom source for the module.
	 */
	public void addModule(String Name, String Source) {
		this.langRegistry.put(Name, Source);
	}

	/**
	 * Gets the class definition for the provided lang class name.
	 * @param Name is a String with the class name to get.
	 * @return A astClass class definition.
	 * @throws aussomException if no class is defined with that name.
	 */
	public astClass getClassDef(String Name) throws aussomException {
		astClass def = this.classes.get(Name);
		if (def != null) return def;
		throw new aussomException("Aussom Engine: can't find requested class def '" + Name + "'.");
	}

	/**
	 * Adds a string to the main(args) function
	 * args list.
	 * @param MainArg is a String with the arg to add.
	 */
	public void addMainArg(String MainArg) {
		this.mainFunctArgs.getValue().add(new AussomString(MainArg));
	}

	/**
	 * Adds a list of strings to the main(args) function
	 * args list.
	 * @param MainArgs is a List of Strings with the args to add.
	 */
	public void addMainArgs(List<String> MainArgs) {
		for (String arg : MainArgs) {
			this.mainFunctArgs.getValue().add(new AussomString(arg));
		}
	}
	
	/**
	 * Gets the instance of the security manager for this Engine.
	 * @return A SecurityManagerInt object of the security manager.
	 */
	public SecurityManagerInt getSecurityManager() {
		return this.secman;
	}

	/**
	 * Decides whether a script may bind the named Java class with an
	 * 'extern class' declaration. Consulted by the parser as each
	 * declaration is reduced, which is the single point where a class
	 * name enters the engine: a class that cannot be declared cannot be
	 * constructed or called either.
	 *
	 * <p>Returns true when aussom.extern.allowlist.enforce is false,
	 * which is the default and the historical behavior. When it is true
	 * the name must match an entry of aussom.extern.allowed, either
	 * exactly or through a 'pkg.*' prefix that admits that package and
	 * everything under it. An absent or unusable list denies, so
	 * switching enforcement on never permits more than intended.
	 *
	 * @param ClassName is the fully qualified name from the declaration.
	 * @return A boolean with true for permitted and false for denied.
	 */
	public boolean isExternClassAllowed(String ClassName) {
		if (!this.secman.getPropertyBoolean("aussom.extern.allowlist.enforce", false)) {
			return true;
		}
		if (ClassName == null) {
			return false;
		}

		List<Object> allowed = this.secman.getPropertyList("aussom.extern.allowed");
		if (allowed == null) {
			return false;
		}

		for (Object entry : allowed) {
			if (entry == null) {
				continue;
			}
			String ent = entry.toString().trim();
			if (ent.isEmpty()) {
				continue;
			}
			if (ent.endsWith(".*")) {
				// Drop only the star and keep the trailing dot, so an
				// entry of 'com.a.std.*' does not admit 'com.a.stdlib.Foo'.
				if (ClassName.startsWith(ent.substring(0, ent.length() - 1))) {
					return true;
				}
			} else if (ent.equals(ClassName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets the logger this engine writes to. Never returns null, so
	 * callers do not have to guard. Interpreter internals and the
	 * standard library {@code c} class both route here.
	 * @return The LoggingInt for this engine.
	 */
	public LoggingInt getLogger() {
		return this.logger;
	}

	/**
	 * Sets the logger this engine writes to. Passing null restores the
	 * default implementation rather than disabling output, so the
	 * logger reference is always safe to dereference.
	 * @param Logger is the LoggingInt to use, or null for the default.
	 */
	public void setLogger(LoggingInt Logger) {
		if (Logger == null) {
			this.logger = new DefaultLoggingImpl();
		} else {
			this.logger = Logger;
		}
	}
	
	/**
	 * Adds a Aussom include to the interpreter. The include can be a standard library 
	 * language include. It can also be a file that exists in one f the includePaths 
	 * if any are set.
	 * @param Include is a String with the include to add.
	 * @throws Exception on parse failure.
	 */
	public synchronized void addInclude(String Include) throws Exception {
		boolean found = false;
		this.getLogger().trc("Engine.addInclude(): Include: " + Include);
		if (this.langRegistry.contains(Include)) {
			found = true;
			if (!this.includes.contains(Include)) {
				this.getLogger().trc("Engine.addInclude(): Adding langInclude: " + Include);
				this.includes.add(Include);
				this.parseString("/com/aussom/stdlib/aus/" + Include, this.langRegistry.get(Include));
			}
		} else {
			this.getLogger().trc("Engine.addInclude(): Attempting to find in resourceIncludePaths ...");
			for (String pth : this.resourceIncludePaths) {
				List<String> resDir = this.langRegistry.listResourceDirectory(pth);
				String tinc = pth + Include;
				for (String fname : resDir) {
					if (fname.contains(tinc)) {
						found = true;
						if (!this.includes.contains(tinc)) {
							this.getLogger().trc("Engine.addInclude(): Include " + Include + " found in '" + fname + "'");
							this.includes.add(tinc);
							this.parseString(tinc, Util.loadResource(tinc));
							return;
						}
					}
				}
			}

			if (!found) {
				this.getLogger().trc("Engine.addInclude(): Attempting to find in includePaths ...");
				for (String pth : this.includePaths) {
					String tinc = pth + Include;
					// This could be different than tinc because of Windoz ...
					String localIncPath = tinc.replace("/", System.getProperty("file.separator"));
					if (!this.isPathExcludePath(tinc)) {
						File f = new File(localIncPath);
						if (f.exists()) {
							if (!this.getSecurityManager().getPropertyBoolean("aussom.include.symlink.follow", true)
									&& this.includeHasSymlink(pth, Include)) {
								throw new aussomException("Attempting to add include '" + tinc
									+ "' through a symbolic link, which policy does not allow.");
							}
							found = true;
							if (!this.includes.contains(tinc)) {
								this.getLogger().trc("Engine.addInclude(): Include " + Include + " found in '" + pth + "'");
								this.includes.add(tinc);
								this.parseFile(localIncPath);
								break;
							}
						}
					} else {
						throw new aussomException("Attempting to add include '" + tinc + "' from excluded path.");
					}
				}

				if (!found) {
					this.getLogger().trc("Engine.addInclude(): Include '" + Include + "' not found at all.");
					if (this.engineRunMode != EngineRunMode.DOC) {
						throw new aussomException("Engine.addInclude(): Couldn't find requested include module '" + Include + "'.");
					}
				}
			}
		}
	}

	/**
	 * Gets the load extern classes flag. If set to
	 * true the parser will load external Java classes
	 * as it parses, if set to false it won't. This is
	 * set to false for doc generation.
	 * @return A boolean with the flag.
	 */
	public boolean isLoadExternClasses() {
		return loadExternClasses;
	}

	/**
	 * Sets the load extern classes flag. If set to
	 * 	 * true the parser will load external Java classes
	 * 	 * as it parses, if set to false it won't. This is
	 * 	 * set to false for doc generation.
	 * @param loadExternClasses is a boolean with the flag.
	 */
	public void setLoadExternClasses(boolean loadExternClasses) {
		this.loadExternClasses = loadExternClasses;
	}

	/**
	 * Sets the Engine run mode.
	 * @param engineRunMode is an EngineRunMode enum value.
	 */
	public void setEngineRunMode(EngineRunMode engineRunMode) {
		this.engineRunMode = engineRunMode;
	}

	/**
	 * Gets the Engine run mode.
	 * @return An EngineRunMode enum value.
	 */
	public EngineRunMode getEngineRunMode() {
		return this.engineRunMode;
	}

	/**
	 * Adds an include path to the list of search paths for Aussom includes.
	 * @param Path is a String with the search path to add.
	 */
	public void addIncludePath(String Path) {
		String tinc = Path;
		if (!tinc.endsWith("/")) {
			tinc += "/";
		}
		this.includePaths.add(tinc);
	}
	
	/**
	 * Gets a list of the search include paths.
	 * @return A List of Strings with the include paths.
	 */
	public List<String> getIncludePaths() {
		return this.includePaths;
	}

	/**
	 * Adds an exclude path to the list of search paths for Aussom includes.
	 * @param Path is a String with the exclude search path to add.
	 */
	public void addExcludePath(String Path) {
		String tinc = Path;
		if (!tinc.endsWith("/")) {
			tinc += "/";
		}
		this.excludePaths.add(tinc);
	}

	/**
	 * Gets a list of the search exclude paths.
	 * @return A List of Strings with the include paths.
	 */
	public List<String> getExcludePaths() {
		return this.excludePaths;
	}
	
	/**
	 * Adds an include path for a resource directory with a JAR file 
	 * to the list of resource include paths.
	 * @param Path is a String with the search resource path to add.
	 */
	public void addResourceIncludePath(String Path) {
		String tinc = Path;
		if (!tinc.endsWith("/")) {
			tinc += "/";
		}
		this.resourceIncludePaths.add(tinc);
	}
	
	/**
	 * Gets a list of the resource search include paths.
	 * @return A List of Strings with the resource include paths.
	 */
	public List<String> getResourceIncludePath() {
		return this.resourceIncludePaths;
	}
	
	/**
	 * Gets a list of current includes.
	 * @return A List of Strings with the current includes.
	 */
	public List<String> getIncludes() {
		return this.includes;
	}

	/**
	 * Resets the main callstack.
	 */
	public void newMainCallstack() {
		this.mainCallStack = new CallStack();
	}

	/**
	 * Gets the current main callstack.
	 * @return A CallStack object.
	 */
	public CallStack getMainCallStack() {
		return this.mainCallStack;
	}

	/**
	 * Gets the hasParseErrors flag.
	 * @return A boolean with true for has parse errors and false for not.
	 */
	public boolean hasParseErrors() {
		return this.hasParseErrors;
	}

	public void addClass(astNode TCls) throws aussomException {
		astClass Cls = (astClass)TCls;
		this.classes.put(Cls.getName(),  Cls);
		this.setClassConstructor(Cls);
		if (Cls.getStatic() && this.initComplete) {
			// Instantiate static class now.
			this.instantiateStaticClass(Cls);
		}
	}
	
	/**
	 * Gets a class instance (astClass) object from 
	 * the list of class definitions with the provided name.
	 * @param Name is a String with the class to get.
	 * @return An astClass object with the class definition or null if not found.
	 */
	public astClass getClassByName(String Name) {
		return this.classes.get(Name);
	}

	/**
	 * Gets the astClass object with the provided filename and path
	 * and returns null if not found.
	 * @param FileNameAndPath is a string with the full filename and path.
	 * @return An astClass object or null if not found.
	 */
	public List<astClass> getClassByFileNameAndPath(String FileNameAndPath) {
		List<astClass> ret = new ArrayList<astClass>();
		for (astClass cls : this.classes.values()) {
			if (cls.getFileName().equals(FileNameAndPath)) {
				ret.add(cls);
			}
		}
		return ret;
	}

	/**
	 * Checks to see if a class definition with the provided name exists in the engine.
	 * Note that this doesn't include static classes.
	 * @param Name is a String with the class definition to search for.
	 * @return A boolean with true for exists and false for not.
	 */
	public boolean containsClass(String Name) {
		if (this.classes.containsKey(Name)) {
			return true;
		}
		return false;
	}
	
	/**
	 * Checks to see if a static class definition with the provided name exists
	 * in the engine. 
	 * @param Name is a String with the class definition to search for.
	 * @return A boolean with true for exists and false for not.
	 */
	public boolean containsStaticClass(String Name) {
		if (this.staticClasses.containsKey(Name)) {
			return true;
		}
		return false;
	}
	
	/**
	 * Gets the static class object instance with the provided name.
	 * @param Name is a string with the class definition to get.
	 * @return An astClass definition for the class or null if not found.
	 */
	public AussomType getStaticClass(String Name) {
		return this.staticClasses.get(Name);
	}
	
	/**
	 * Gets a Map with the current class names and their astClass 
	 * definition objects as values.
	 * @return A Map of (String, astClass) with the current classes.
	 */
	public Map<String, astClass> getClasses() {
		return this.classes;
	}

	/**
	 * The interpreter will parse the Aussom code file with the provided file name.
	 * @param FileName is a String with the Aussom code file to parse.
	 * @throws Exception on parse failure.
	 */
	public void parseFile(String FileName) throws Exception {
		// Checked against the file length before the read, so an
		// oversized source is refused rather than pulled into memory and
		// then rejected. Util.read builds the whole file into a
		// StringBuffer and copies it, so the read costs several times the
		// file before the parser sees a token. Files only: source handed
		// in as a string has already been measured by whoever built it,
		// which is also why this cannot refuse the standard library.
		long max = this.limits.getSourceBytes();
		if (max > 0L) {
			long len = new File(FileName).length();
			if (len > max) {
				throw new aussomException("Engine.parseFile(): Source file '" + FileName
					+ "' is " + len + " bytes, over the limit of " + max + " bytes.");
			}
		}
		this.parseString(FileName, Util.read(FileName));
	}
	
	/**
	 * The interpreter will parse the provided Aussom code string. It also 
	 * ties that code to the provided file name internally.
	 * @param FileName is a String with the file name to assign to the provided code.
	 * @param Contents is a String with the Aussom code to parse.
	 * @throws Exception on parse failure.
	 */
	public void parseString(String FileName, String Contents) throws Exception {
		// Registered for the length of the parse, so compile CPU and
		// allocation land in the same totals a host meters execution
		// with. Includes are resolved during the parse and re-enter this
		// method, and a nested entry banks to the outer scope rather than
		// counting twice. See design/security-evaluation-g1-g3.md.
		try (ThreadScope scope = this.enterInterpreterThread()) {
			// A cancelled engine stops loading here. Every include comes
			// back through this method, so a cancel reaches a multi-file
			// load at the next include; it does not interrupt the parse of
			// one file, which is bounded by that file's length.
			if (this.getControlState() == ControlState.CANCELLED) {
				throw new aussomException("Engine.parseString(): Parse of '" + FileName
					+ "' cancelled.");
			}
			this.parseSource(FileName, Contents);
		}
	}

	/**
	 * Parses source into this engine's class table. The body of
	 * parseString, split out so the accounting scope and the control
	 * check wrap one call rather than the whole method.
	 * @param FileName is a String with the file name to assign to the code.
	 * @param Contents is a String with the Aussom code to parse.
	 * @throws Exception on parse failure.
	 */
	private void parseSource(String FileName, String Contents) throws Exception {
		List<String> classesBefore = new ArrayList<String>(this.classes.keySet());
		Lexer scanner = new Lexer(new StringReader(Contents), FileName);
		// Diagnostics accumulate here; a file and its includes are
		// separate parseString calls but one logical load.
		scanner.setDiagnosticSink(this);
		parser p = new parser(scanner, this, FileName, this.loadExternClasses);
		try {
			p.parse();
		} catch (StackOverflowError soe) {
			// Defensive: no input is known to overflow the parser, since
			// CUP uses an explicit stack rather than recursive descent.
			// The catch is here so a future grammar change cannot reopen
			// that quietly. See design/security-evaluation-f4-f5.md 3.2.
			this.getLogger().err("Parse of '" + FileName + "' ran out of stack space. "
				+ "Source is nested too deeply.");
			this.setParseError();
			return;
		}
		// P2: lexer errors (e.g. illegal characters) are reported via
		// console.err but historically did not halt parsing. Promote
		// them to parse errors so the engine refuses to run code that
		// the lexer could not fully tokenize.
		if (scanner.hasErrors()) {
			this.setParseError();
		}
		this.fileNames.add(FileName);
		this.chargeClassDefinitions(classesBefore);
	}
	
	/**
	 * Runs the Aussom engine. This function goes though and identifies 
	 * the first class with a main function. If not found it will throw an exception. 
	 * If found it will call the entry point of the application (main function).
	 * @throws aussomException on failure to find main class or on parse errors.
	 * @return An integer with 0 for success and any other value for failure.
	 */
	public int run() throws aussomException {
		if (!this.hasParseErrors) {
			this.getLogger().trc("Running program now ...");

			this.mainCallStack = new CallStack();

			// Pick up any limit the host changed since the last run.
			this.refreshLimits();

			// Set the main class and function.
			if (this.setMainClassAndFunct()) {
				// Register the thread for the length of the program, so
				// accounting and pause tracking cover it. An overflow
				// is converted here rather than escaping as an Error to
				// a caller that is catching Exception.
				try (ThreadScope scope = this.enterInterpreterThread()) {
					return this.callMain();
				} catch (StackOverflowError soe) {
					AussomException ex = this.stackOverflowToException(soe, this.mainCallStack);
					this.getLogger().err(((AussomTypeInt) ex).str());
					return 1;
				}
			} else {
				throw new aussomException("Engine.run(): Failed to find main class.");
			}
		} else {
			throw new aussomException("Engine.run(): Parse errors were encountered. Not running.");
		}
	}

	/**
	 * Checks to see if the provided test path is within
	 * one of the exclude paths. If so it returns true and
	 * if not it returns false.
	 * @param testPath is a path to test.
	 * @return A boolean with true if in an exclude path
	 * and false if not.
	 */
	/**
	 * Whether any part of an include name, below its include path, is a
	 * symbolic link.
	 *
	 * Every component is checked rather than just the file, because a
	 * linked directory in the middle of the name reaches outside the root
	 * exactly as a linked file does. Links at or above the include path
	 * itself are not policed: that is the host's own choice of where the
	 * root lives, and a root that is a link, or lives under one, keeps
	 * working. What this answers is whether the name a script wrote
	 * traverses a link.
	 *
	 * Checked only when the aussom.include.symlink.follow policy is
	 * false. Following links is ordinary filesystem behaviour and often
	 * deliberate, so the default leaves it alone.
	 *
	 * @param Root is a String with the include path the name is under.
	 * @param Include is a String with the include's relative path.
	 * @return A boolean with true when a link was found.
	 */
	private boolean includeHasSymlink(String Root, String Include) {
		// Include always uses '/' separators: astInclude.getPath builds it
		// that way. Path.resolve applies the platform separator.
		Path p = new File(Root).toPath();
		for (String part : Include.split("/")) {
			if (part.isEmpty()) continue;
			p = p.resolve(part);
			if (Files.isSymbolicLink(p)) {
				return true;
			}
		}
		return false;
	}

	public boolean isPathExcludePath(String testPath) {
		for (String excludePath : this.excludePaths) {
			if (testPath.startsWith(excludePath))
				return true;
		}
		return false;
	}
	
	/**
	 * Constructors are stored in the same overload group as
	 * methods, keyed at the class name. astClass.instantiate
	 * routes through the dispatcher to pick the matching ctor
	 * overload by signature, so no separate setup is needed.
	 * Kept as a no-op for any external caller still invoking it.
	 */
	private void setClassConstructor(astClass ac) {
		// No-op: see method comment.
	}
	
	/**
	 * Function instantiates objects for all static class definitions. This is 
	 * called once all the base lang classes have been parsed.
	 * @throws aussomException
	 */
	private void instantiateStaticClasses() throws aussomException {
		for (String cname : this.classes.keySet()) {
			astClass ac = this.classes.get(cname);
			if (ac.getStatic()) {
				this.instantiateStaticClass(ac);
			}
		}
	}
	
	/**
	 * Instantiates a static class object with the provided class definition.
	 * @param ac is a astClass class definition object.
	 * @throws aussomException
	 */
	private void instantiateStaticClass(astClass ac) throws aussomException {
		if (this.loadExternClasses) {
			this.getLogger().trc("Instantiating static class: " + ac.getName());
			AussomType aci = null;
			Environment tenv = new Environment(this);
			Members locals = new Members();
			// Push a synthetic frame so debugger pauses inside the
			// static class's member inits or constructor show this
			// class as the active context, not the empty engine
			// root. See design/debugging-callstack-update.md.
			CallStack staticFrame = new CallStack(ac.getFileName(), ac.getLineNum(),
				ac.getName(), "<static-init>", "Static class initializer.");
			staticFrame.setParent(this.mainCallStack);
			tenv.setEnvironment((AussomObject) aci, locals, staticFrame);
			aci = (AussomObject) ac.instantiateStaticSingleton(tenv);
			if (!aci.isEx()) {
				this.staticClasses.put(ac.getName(), aci);
			} else {
				throw new aussomException(ac, ((AussomException) aci).getText(), ((AussomException) aci).getStackTrace());
			}
		}
	}
	
	/**
	 * Searches through list of class definitions looking for the 
	 * first one that contains a main function. Once found it sets 
	 * it's private mainClassFound and mainFunctFound variables. It 
	 * then breaks and returns true if found.
	 * @return A boolean with true if main function found and set and 
	 * false for not.
	 */
	private boolean setMainClassAndFunct() {
		boolean found = false;
		for (String cname : this.classes.keySet()) {
			if (found) break;
			astClass ac = this.classes.get(cname);
			
			if (ac.hasAnyFunctionByName("main")) {
				this.mainClassFound = true;
				this.mainClassDef = ac;
				this.mainFunctFound = true;
				// Pick the zero-arg main overload. If absent the
				// dispatcher will surface NO_MATCHING_OVERLOAD when
				// callMain runs.
				this.mainFunctDef = ac.getFunct("main", "");
				found = true;
				break;
			}
		}
		return found;
	}
	
	/**
	 * This is the program entry point. This function setups up the environment, 
	 * locals and instantiates the main class. It then compiles the main function 
	 * arguments and then calls main to kick off program execution.
	 * @throws aussomException
	 * @return An integer with 0 for success and any other value for failure.
	 */
	private int callMain() throws aussomException {
		Environment tenv = new Environment(this);
		Members locals = new Members();
		tenv.setEnvironment(null, locals, this.mainCallStack);

		// A static main class was already built once during the static
		// startup pass, so reuse that singleton rather than instantiating a
		// second, detached copy. Only a regular class is instantiated here.
		AussomType tci;
		if (this.mainClassDef.getStatic() && this.staticClasses.containsKey(this.mainClassDef.getName())) {
			tci = this.staticClasses.get(this.mainClassDef.getName());
		} else {
			tci = this.mainClassDef.instantiate(tenv, false, new AussomList());
		}
		if(!tci.isEx())
		{
			this.mainClassInstance = (AussomObject) tci;
			tenv.setClassInstance(this.mainClassInstance);

			/*
			 * Pick the entry-point shape. Pass the CLI args list when
			 * the user declared any 1-arg main overload — this covers
			 * `main(list args)` (sig "l"), `main(args)` (untyped, sig
			 * "*"), variadic `main(...)`, optional `main(args = null)`,
			 * or any other 1-arg form. Otherwise call with no args so
			 * a script that defines only `main()` doesn't trip
			 * FUNCT_NOT_FOUND.
			 */
			AussomList margs = new AussomList();
			boolean hasOneArgMain = false;
			for (astFunctDef def : this.mainClassDef.getFunctionsByName("main")) {
				if (def.getMinArity() <= 1 && def.getMaxArity() >= 1) {
					hasOneArgMain = true;
					break;
				}
			}
			if (hasOneArgMain) {
				margs.add(this.mainFunctArgs);
			}

			/*
			 * Call main.
			 */
			AussomType ret;
			ret = this.mainClassDef.call(tenv, false, "main", margs);
			if(ret.isEx()) {
				AussomException ex = (AussomException) ret;
				this.getLogger().err(((AussomTypeInt) ex).str());
				return 1;
			} else if (ret instanceof AussomInt) {
				return (int)((AussomInt)ret).getNumericInt();
			}
		} else {
			AussomException ex = (AussomException)tci;
			this.getLogger().err(ex.toString());
			return 1;
		}
		return 0;
	}
	
	/**
	 * Instantiates a new object with the provided class name and
	 * no constructor arguments. Equivalent to calling the
	 * (Name, Args) overload with an empty list.
	 * @param Name is a String with the class name to instantiate.
	 * @return A newly instantiated AussomObject.
	 * @throws aussomException if the class is not found or the
	 * constructor fails.
	 */
	public AussomObject instantiateObject(String Name) throws aussomException {
		return this.instantiateObject(Name, new AussomList());
	}

	/**
	 * Instantiates a new object with the provided class name,
	 * routing the supplied argument list through the constructor
	 * dispatcher so an overloaded constructor can be selected by
	 * signature.
	 * @param Name is a String with the class name to instantiate.
	 * @param Args is the argument list passed to the constructor.
	 * @return A newly instantiated AussomObject.
	 * @throws aussomException if the class is not found or the
	 * constructor fails or no matching overload exists.
	 */
	public AussomObject instantiateObject(String Name, AussomList Args) throws aussomException {
		if (this.classes.containsKey(Name)) {
			Environment tenv = new Environment(this);
			Members locals = new Members();
			tenv.setEnvironment(this.mainClassInstance, locals, this.mainCallStack);

			AussomList cargs = Args;
			if (cargs == null) {
				cargs = new AussomList();
			}

			AussomType result = this.classes.get(Name).instantiate(tenv, false, cargs);
			if (result.isEx()) {
				throw new aussomException("instantiateObject('" + Name + "') failed: "
					+ ((AussomException) result).stackTraceToString());
			}
			return (AussomObject) result;
		} else {
			throw new aussomException("Attempting to instantiate object of type '" + Name + "' but class not found!");
		}
	}
	
	/**
	 * Sets the parse error flag. If set prior to run being called, run will
	 * throw an exception because of the parse error. This is called by the
	 * Aussom parser generated from aussom.cup.
	 */
	public void setParseError() {
		this.hasParseErrors = true;
	}

	/**
	 * Clears the parse-error flag. Lets a long-lived embedder
	 * (e.g. the JSR 223 engine) recover from a failed parse so the
	 * next call to parseString / run starts from a clean slate.
	 *
	 * Deliberately does NOT clear the parse diagnostics. Callers
	 * clear the flag precisely when they are about to report the
	 * failure (parseScriptLine does this before throwing), so
	 * clearing diagnostics here would discard them at the moment the
	 * consumer needs them. Use clearParseDiagnostics for that.
	 */
	public void clearParseError() {
		this.hasParseErrors = false;
	}

	/**
	 * Gets the structured parse diagnostics collected so far. Each
	 * entry carries the file, line, column, severity, and message as
	 * separate fields, so consumers do not have to parse the console
	 * output to find a position.
	 *
	 * Lifetime differs by parse entry point, deliberately:
	 * parseString accumulates, so a file and everything it includes
	 * report as one batch (matching the sticky hasParseErrors flag);
	 * parseStatements clears on entry, so a script-mode caller sees
	 * only the diagnostics for the submission it just made. An
	 * embedder mixing both on one engine must read diagnostics before
	 * the next parseStatements call.
	 *
	 * @return An unmodifiable List of ParseDiagnostic.
	 */
	public List<ParseDiagnostic> getParseDiagnostics() {
		return Collections.unmodifiableList(this.parseDiagnostics);
	}

	/**
	 * Adds a structured parse diagnostic. Called by the lexer, the
	 * parser, and the engine's own parse paths at the same points
	 * they write a message to the console.
	 * @param diag is the ParseDiagnostic to add.
	 */
	public void addParseDiagnostic(ParseDiagnostic diag) {
		this.parseDiagnostics.add(diag);
	}

	/**
	 * Clears the collected parse diagnostics. parseStatements calls
	 * this on entry so each script-mode submission starts clean; a
	 * caller driving parseString can call it to start a fresh batch.
	 */
	public void clearParseDiagnostics() {
		this.parseDiagnostics.clear();
	}

	/* ============================================================
	 * Control: cancel, pause and resume
	 *
	 * A running Aussom program is stopped or held by asking it to
	 * stop or hold, not by killing or suspending the thread it runs
	 * on. The engine keeps one ControlState, and the interpreter
	 * reads it at every checkpoint:
	 *
	 *   - loop back edges                (astWhile, astFor)
	 *   - every Aussom function call     (astClass.call)
	 *   - every batch of regex reads     (RegexSubject.charAt)
	 *   - every sleep slice              (ASys.sleep)
	 *
	 * CANCELLED makes the checkpoint hand back an AussomException
	 * whose id is CANCELLED_EXCEPTION_ID. PAUSED makes it block
	 * there until the state changes.
	 *
	 * Properties that matter to callers:
	 *
	 * 1. A cancellation is a value, not a thrown Java exception.
	 *    Runtime errors already travel back to the caller as
	 *    AussomException values, so it needs no special unwinding.
	 *
	 * 2. The id is distinct. A host that runs untrusted code on a
	 *    timeout must be able to tell "this program ran too long"
	 *    apart from "this program has a bug". Match on
	 *    Engine.CANCELLED_EXCEPTION_ID, or on
	 *    AussomException.isCancellation().
	 *
	 * 3. Aussom code cannot catch a cancellation. It is a decision
	 *    made by the host, so a try/catch in the script re-raises it
	 *    instead of swallowing it. See astTryCatch.
	 *
	 * 4. pause() asks the engine to stop and returns immediately.
	 *    The engine is fully paused only once every thread running
	 *    its code has reached a checkpoint and parked. Use
	 *    isFullyPaused() to read that, or awaitPaused() to wait for
	 *    it. A thread inside an extern call that is waiting on a
	 *    socket or a latch has not reached a checkpoint, so it does
	 *    not count as stopped.
	 *
	 * 5. A paused program keeps its stack, locals and data, so
	 *    resume() continues where it stopped. Pause is a CPU
	 *    control, never a memory control: a paused engine still
	 *    holds everything it held.
	 *
	 * 6. Cancel outranks pause. cancel() on a paused engine ends the
	 *    program without the host having to resume it first.
	 *
	 * 7. Pause is invisible to a program except through the clock. A
	 *    sliced sys.sleep() is suspended by a pause, but an unsliced
	 *    blocking call such as Latch.await(5000) keeps counting wall
	 *    time, so a long pause can expire it.
	 *
	 * Thread interrupt status is deliberately not consulted, and
	 * that omission is a decision rather than an oversight.
	 * Interruption is per-thread state that any code on the stack
	 * can set or clear, while control is a property of the whole
	 * engine: a host may run many threads against one Engine (see
	 * design/aussom-concurrency.md), so treating one thread's
	 * interrupt as a reason to stop the entire program invites
	 * failures nobody asked for. A host that wants an interrupt to
	 * stop a program calls cancel() itself. Callers using an
	 * ExecutorService should therefore pair future.cancel(true) with
	 * an explicit cancel(); the interrupt alone does nothing here.
	 *
	 * See design/security-evaluation-f4-f5.md sections 4.1 and 5.6.
	 * ============================================================ */

	/**
	 * Exception id reported when a program is stopped by cancel().
	 */
	public static final String CANCELLED_EXCEPTION_ID = "EXECUTION_CANCELLED";

	/**
	 * Requests that the running program stop. Safe to call from any
	 * thread, including before run() starts, and on a paused engine.
	 * The state is sticky: it stays CANCELLED until clearCancel() is
	 * called, so an engine that is reused must be cleared first.
	 */
	public void cancel() {
		synchronized (this.controlLock) {
			this.controlState = ControlState.CANCELLED;
			this.controlLock.notifyAll();
		}
	}

	/**
	 * Returns true if cancel() has been called and not yet cleared.
	 * @return A boolean with true for cancelled and false for not.
	 */
	public boolean isCancelled() {
		return this.controlState == ControlState.CANCELLED;
	}

	/**
	 * Clears a cancellation so the engine can run again. A paused
	 * engine is left paused.
	 * @return A boolean with true if the engine had been cancelled.
	 */
	public boolean clearCancel() {
		synchronized (this.controlLock) {
			boolean prev = (this.controlState == ControlState.CANCELLED);
			if (prev) {
				this.controlState = ControlState.RUNNING;
				this.controlLock.notifyAll();
			}
			return prev;
		}
	}

	/**
	 * The engine's current control state. One volatile read, which is
	 * what every checkpoint does on the fast path.
	 * @return The current ControlState, never null.
	 */
	public ControlState getControlState() {
		return this.controlState;
	}

	/**
	 * Asks the running program to stop at its next checkpoint and wait
	 * there. Returns immediately: use isFullyPaused() or awaitPaused()
	 * to find out when the program has actually stopped.
	 *
	 * Safe to call from any thread and more than once. A cancelled
	 * engine is left cancelled, since cancel outranks pause.
	 */
	public void pause() {
		synchronized (this.controlLock) {
			if (this.controlState == ControlState.CANCELLED) return;
			this.controlState = ControlState.PAUSED;
		}
	}

	/**
	 * Lets a paused program continue. Safe to call from any thread and
	 * more than once; calling it on an engine that is not paused does
	 * nothing. A cancelled engine is left cancelled.
	 */
	public void resume() {
		synchronized (this.controlLock) {
			if (this.controlState != ControlState.PAUSED) return;
			this.controlState = ControlState.RUNNING;
			this.controlLock.notifyAll();
		}
	}

	/**
	 * True when pause() has been asked for and every thread running
	 * this engine's code has reached a checkpoint and parked. An engine
	 * that is paused with no program running counts as fully paused,
	 * since there is nothing left to stop.
	 * @return A boolean with true for fully paused.
	 */
	public boolean isFullyPaused() {
		synchronized (this.controlLock) {
			if (this.controlState != ControlState.PAUSED) return false;
			return this.stoppedThreads >= this.interpreterThreads.size();
		}
	}

	/**
	 * Waits until the engine is fully paused, or the timeout runs out.
	 * Call pause() first; this does not ask for the pause itself.
	 *
	 * A false return is information rather than a failure: it means at
	 * least one thread is still busy somewhere the engine does not
	 * control, such as a socket read or a latch wait inside an extern
	 * call. The host decides whether to keep waiting or to cancel.
	 *
	 * @param Timeout is how long to wait.
	 * @param Unit is the unit of Timeout.
	 * @return A boolean with true if the engine is fully paused.
	 * @throws InterruptedException if the calling thread is interrupted.
	 */
	public boolean awaitPaused(long Timeout, TimeUnit Unit) throws InterruptedException {
		long deadline = System.nanoTime() + Unit.toNanos(Timeout);
		synchronized (this.controlLock) {
			while (true) {
				if (this.controlState != ControlState.PAUSED) return false;
				if (this.stoppedThreads >= this.interpreterThreads.size()) return true;
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0L) return false;
				this.controlLock.wait(remaining / 1000000L, (int) (remaining % 1000000L));
			}
		}
	}

	/**
	 * The interpreter's parking spot. Called from a checkpoint that has
	 * already seen a state other than RUNNING; blocks while the engine
	 * is paused and reports whether the program must now unwind.
	 *
	 * Not for embedders. Hosts use pause(), resume() and cancel(); this
	 * is public only because the stdlib checkpoints (RegexSubject and
	 * ASys.sleep) live in another package.
	 *
	 * @return A boolean with true if the engine is cancelled and the
	 * caller must stop, false if it may keep going.
	 */
	public boolean awaitResumeOrCancel() {
		synchronized (this.controlLock) {
			if (this.controlState == ControlState.CANCELLED) return true;
			if (this.controlState != ControlState.PAUSED) return false;

			this.stoppedThreads++;
			// Tell any awaitPaused caller that one more thread stopped.
			this.controlLock.notifyAll();
			try {
				while (this.controlState == ControlState.PAUSED) {
					try {
						this.controlLock.wait();
					} catch (InterruptedException ie) {
						// Interrupt status is not a reason to stop
						// (see the contract above), but it must not be
						// swallowed either. Keep waiting for a real
						// decision and leave the flag for host code.
						Thread.currentThread().interrupt();
					}
				}
			} finally {
				this.stoppedThreads--;
			}
			return this.controlState == ControlState.CANCELLED;
		}
	}

	/**
	 * Builds the interpreter's cancellation exception. One place, so a
	 * loop back edge, the call path, and stdlib code that discovers a
	 * cancellation part-way through an operation all produce the same
	 * value: CANCELLED_EXCEPTION_ID with the cancellation flag set, so
	 * astTryCatch will not let a script swallow it.
	 *
	 * The line number is left at -1 when it is not known here. Callers
	 * inside the AST set it; for stdlib callers the extern-return
	 * enrichment in astFunctDef.callExtern fills in the call site.
	 *
	 * @param env is the current Environment, may be null.
	 * @return A new AussomException marked as a cancellation.
	 */
	public static AussomException cancelledException(Environment env) {
		return cancelledException(env, -1);
	}

	/**
	 * Cancellation exception with a known source line.
	 * @param env is the current Environment, may be null.
	 * @param LineNum is the source line to report.
	 * @return A new AussomException marked as a cancellation.
	 */
	public static AussomException cancelledException(Environment env, int LineNum) {
		String trace = "";
		if (env != null && env.getCallStack() != null) {
			trace = env.getCallStack().getStackTrace();
		}
		AussomException ex = new AussomException(AussomException.exType.exRuntime);
		ex.setException(LineNum, CANCELLED_EXCEPTION_ID,
			"Execution cancelled.",
			"Execution was cancelled by the host via Engine.cancel().",
			trace);
		ex.setCancellation(true);
		return ex;
	}

	/* ============================================================
	 * Resource limits, accounting and measurement
	 *
	 * The engine supplies mechanism here, never policy. It can say
	 * how much CPU and allocation its programs have used and how much
	 * memory its data holds, and it reads its limits from the security
	 * manager the host built. Deciding what to do about any of it belongs
	 * to the host, which owns the deadline, the tenant list and the
	 * thread pool.
	 *
	 * Note what is not here: no setter for any limit, and no per-value
	 * size caps. Limits are policy and come from the security manager,
	 * and a cap on the size of one value bounds neither memory nor
	 * anything else a host can reason about. See Limits.
	 *
	 * See design/security-evaluation-f4-f5.md section 5.
	 * ============================================================ */

	/**
	 * Exception id reported when a call would nest deeper than the
	 * engine's maximum call depth.
	 */
	public static final String CALL_DEPTH_EXCEEDED_ID = "CALL_DEPTH_EXCEEDED";

	/**
	 * Exception id reported when the interpreter ran out of Java stack
	 * and the overflow was converted at a public boundary.
	 */
	public static final String STACK_OVERFLOW_ID = "STACK_OVERFLOW";

	/**
	 * This engine's resource limits.
	 * @return The current Limits snapshot, never null.
	 */
	public Limits getLimits() {
		return this.limits;
	}

	/**
	 * Re-reads every limit from the security manager. Called in the
	 * constructor and at the start of each program, so a host that
	 * rewrites its own policy between programs is honored without the
	 * interpreter paying for a property lookup on every operation.
	 *
	 * There is no setter for any of these on Engine, and that is
	 * deliberate. Limits are policy, policy lives in the security
	 * manager, and the security manager is built by the host and handed
	 * to the constructor. A second way to set a limit would mean two
	 * places to look when one disagrees with the other.
	 */
	public void refreshLimits() {
		Limits fresh = new Limits(this.secman);
		this.limits = fresh;
		long depth = fresh.getCallDepth();
		if (depth > Integer.MAX_VALUE) depth = Integer.MAX_VALUE;
		this.maxCallDepth = (int) depth;
	}

	/**
	 * Maximum Aussom call depth, 0 for no limit. Set through the security
	 * manager as aussom.limit.call.depth.
	 *
	 * At roughly 1.9 KB of Java stack per Aussom call, a limit of N wants
	 * about N * 2 KB of thread stack plus headroom, so a host raising it
	 * should give its interpreter threads a stack size to match, or run
	 * them as virtual threads where the stack grows on the heap and this
	 * limit is the only bound.
	 *
	 * @return An int with the maximum call depth.
	 */
	public int getMaxCallDepth() {
		return this.maxCallDepth;
	}

	/**
	 * Registers the calling thread as running this engine's code, and
	 * returns a handle that unregisters it. run(), the script-mode
	 * evaluator and the JSR 223 wrapper each wrap a program body in
	 * one, so accounting and pause tracking work without the host
	 * having to track threads itself.
	 *
	 * On entry the engine records this thread's CPU and allocation
	 * counters as a baseline. On close it banks the difference. That
	 * two-step is required rather than tidy: both counters are
	 * cumulative per thread, so a pooled thread carries whatever ran on
	 * it before, and both read -1 once the thread has terminated.
	 *
	 * @return A ThreadScope to close when the program body ends.
	 */
	public ThreadScope enterInterpreterThread() {
		Long id = Long.valueOf(Thread.currentThread().getId());
		if (this.interpreterThreads.containsKey(id)) {
			// Already registered: this is a nested entry, such as a script
			// that reaches back into the engine (reflect.evalStr) or a host
			// that invokes a method from inside a program. The outer scope
			// owns the accounting and the registration, so the inner one
			// must not unregister the thread when it closes.
			return ThreadScope.nested();
		}
		ThreadScope scope = new ThreadScope(this);
		this.interpreterThreads.put(id, scope);
		return scope;
	}

	/**
	 * Thread ids currently running this engine's code. A snapshot, for
	 * a host that wants to read the JVM's own counters directly.
	 * @return An array of thread ids, empty when nothing is running.
	 */
	public long[] getInterpreterThreadIds() {
		Long[] keys = this.interpreterThreads.keySet().toArray(new Long[0]);
		long[] out = new long[keys.length];
		for (int i = 0; i < keys.length; i++) {
			out[i] = keys[i].longValue();
		}
		return out;
	}

	/**
	 * CPU nanoseconds this engine's programs have consumed, across
	 * every thread that has run them, including the ones running now.
	 * @return A long with CPU nanoseconds, or -1 when this JVM does not
	 * report per-thread CPU time. -1 means "no accounting available"
	 * rather than "no CPU used".
	 */
	public long getCpuNanos() {
		if (!ThreadMeter.isCpuAvailable()) return -1L;
		long total = this.bankedCpuNanos.get();
		for (ThreadScope scope : this.interpreterThreads.values()) {
			total += scope.liveCpuNanos();
		}
		return total;
	}

	/**
	 * Bytes this engine's programs have allocated, across every thread
	 * that has run them, including the ones running now. Cumulative
	 * allocation rather than live size: see measureRetainedFootprint
	 * for what the engine is holding.
	 * @return A long with bytes allocated, or -1 when this JVM does not
	 * report per-thread allocation.
	 */
	public long getAllocatedBytes() {
		if (!ThreadMeter.isAllocAvailable()) return -1L;
		long total = this.bankedAllocBytes.get();
		for (ThreadScope scope : this.interpreterThreads.values()) {
			total += scope.liveAllocBytes();
		}
		return total;
	}

	/**
	 * Zeroes the CPU and allocation totals. A host that meters per
	 * request calls this at the start of one. Threads running now are
	 * re-baselined, so work already done is discarded rather than
	 * arriving later when they finish.
	 */
	public void resetAccounting() {
		this.bankedCpuNanos.set(0L);
		this.bankedAllocBytes.set(0L);
		for (ThreadScope scope : this.interpreterThreads.values()) {
			scope.rebaseline();
		}
	}

	/**
	 * Banks a finished thread's usage. Called by ThreadScope.close().
	 * @param CpuNanos is the CPU time to add.
	 * @param AllocBytes is the allocation to add.
	 */
	void bankThreadUsage(long CpuNanos, long AllocBytes) {
		if (CpuNanos > 0L) this.bankedCpuNanos.addAndGet(CpuNanos);
		if (AllocBytes > 0L) this.bankedAllocBytes.addAndGet(AllocBytes);
	}

	/**
	 * Unregisters a thread. Called by ThreadScope.close().
	 * @param ThreadId is the thread id to drop.
	 */
	void leaveInterpreterThread(long ThreadId) {
		this.interpreterThreads.remove(Long.valueOf(ThreadId));
		synchronized (this.controlLock) {
			// A thread leaving can complete a pause that awaitPaused is
			// waiting on, so wake the waiters.
			this.controlLock.notifyAll();
		}
	}

	/**
	 * Adds the definitions this parse produced to the class definition
	 * total. Walks only the classes whose names are new, so a program of
	 * many files costs one pass over each definition rather than one pass
	 * over everything for every file.
	 *
	 * Aussom class definitions are not small: 405 KB of source measures
	 * out at about 15 MB of AST, and a host that cannot see that cannot
	 * budget for it. Node count is what scales predictably, at roughly
	 * 145 bytes per node across sources of different sizes; source bytes
	 * and bytes allocated during the parse were both measured and both
	 * vary by more than a factor of two. See
	 * design/security-evaluation-g1-g3.md.
	 *
	 * @param Existing is a List of the class names that were present
	 * before this parse ran.
	 */
	private void chargeClassDefinitions(List<String> Existing) {
		Map<Object, Object> seen = new IdentityHashMap<Object, Object>();
		long nodes = 0L;
		for (Map.Entry<String, astClass> ent : this.classes.entrySet()) {
			if (Existing.contains(ent.getKey())) continue;
			nodes += countNodes(ent.getValue(), seen);
		}
		if (nodes > 0L) {
			this.classDefBytes.addAndGet(nodes * AussomFootprint.AST_NODE_BYTES);
		}
	}

	/**
	 * Counts the AST nodes reachable from a value, by identity so a node
	 * reached twice counts once.
	 *
	 * The walk is reflective because astNode carries a single child field
	 * and there is no general accessor for a node's children, so a hand
	 * written visitor would mean a case for each of more than thirty node
	 * types and would go stale the next time one is added. It runs once
	 * per parsed file and never on an execution path.
	 *
	 * @param Value is the value to walk, may be null.
	 * @param Seen is the identity set of nodes already counted.
	 * @return A long with the number of nodes counted.
	 */
	private long countNodes(Object Value, Map<Object, Object> Seen) {
		if (Value == null) return 0L;

		if (Value instanceof astNode) {
			if (Seen.put(Value, Boolean.TRUE) != null) return 0L;
			long count = 1L;
			for (Class<?> c = Value.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
				for (Field f : c.getDeclaredFields()) {
					if (f.getType().isPrimitive()) continue;
					if (f.getType() == String.class) continue;
					try {
						f.setAccessible(true);
						count += this.countNodes(f.get(Value), Seen);
					} catch (RuntimeException e) {
						// A field the JVM will not open is skipped rather
						// than failing a parse over a measurement.
					} catch (IllegalAccessException e) {
						// Same.
					}
				}
			}
			return count;
		}

		if (Value instanceof Collection) {
			long count = 0L;
			for (Object o : (Collection<?>) Value) {
				count += this.countNodes(o, Seen);
			}
			return count;
		}

		if (Value instanceof Map) {
			long count = 0L;
			for (Object o : ((Map<?, ?>) Value).values()) {
				count += this.countNodes(o, Seen);
			}
			return count;
		}

		return 0L;
	}

	/**
	 * Estimated bytes this engine's parsed class definitions hold.
	 * @return A long with the estimate.
	 */
	public long getClassDefinitionBytes() {
		return this.classDefBytes.get();
	}

	/**
	 * Walks this engine's reachable Aussom values and returns an
	 * estimate of the bytes they hold.
	 *
	 * Call it on an engine that is not running: pause() then
	 * awaitPaused(), or between programs. The walk reads the value
	 * graph without locking it, so a running interpreter thread would
	 * make the result meaningless rather than merely stale. That is
	 * refused rather than left to the caller: an engine with threads
	 * running that is not fully paused answers -1, meaning no
	 * measurement is available, the same convention getCpuNanos and
	 * getAllocatedBytes use.
	 *
	 * Roots are this engine's own: its static class instances, the main
	 * class instance, the script-mode environment, and the locals of
	 * every frame a paused thread is standing in. That last one is why a
	 * paused program measures what it is actually holding: a running
	 * program keeps most of its data in locals, which nothing else
	 * reaches. The estimated size of the parsed class definitions is
	 * added on top. A value held in two places is counted once, and a
	 * structure that contains itself is counted once.
	 *
	 * The number is an estimate from a documented model, not a promise
	 * of exact bytes. See AussomFootprint for the model.
	 *
	 * @return A long with the estimated bytes retained.
	 */
	public long measureRetainedFootprint() {
		// A walk of a running engine reads a graph that is being rewritten
		// under it, which is a number nobody can use. Refuse instead. -1
		// is "no measurement available", the convention getCpuNanos and
		// getAllocatedBytes already use for a JVM that cannot report.
		// An engine with no registered threads is not running, which is
		// the ordinary measurement after a program ends.
		if (!this.interpreterThreads.isEmpty() && !this.isFullyPaused()) {
			return -1L;
		}

		AussomFootprint fp = new AussomFootprint();
		for (AussomType t : this.staticClasses.values()) {
			fp.add(t);
		}
		fp.add(this.mainClassInstance);
		fp.add(this.mainFunctArgs);
		if (this.scriptInstance != null) fp.add(this.scriptInstance);
		if (this.scriptEnv != null && this.scriptEnv.getLocals() != null) {
			for (AussomType t : this.scriptEnv.getLocals().getMap().values()) {
				fp.add(t);
			}
		}

		// The locals of every frame a paused thread is standing in. A
		// running program keeps most of what it holds here rather than in
		// a static or a member, so without these roots a paused engine
		// sitting on a large structure measures as almost nothing.
		for (ThreadScope scope : this.interpreterThreads.values()) {
			CallStack frame = scope.getParkedFrame();
			while (frame != null) {
				Members mem = frame.getLocals();
				if (mem != null) {
					for (AussomType t : mem.getMap().values()) {
						fp.add(t);
					}
				}
				frame = frame.getParent();
			}
		}

		return fp.getBytes() + this.classDefBytes.get();
	}

	/**
	 * Records the frame the calling thread is parking in, so a footprint
	 * measurement taken while the engine is paused can reach that
	 * thread's locals. Called by a checkpoint on its way into the wait
	 * and cleared on the way out, which keeps it off the running path
	 * and keeps it from ever naming a frame that has already returned.
	 *
	 * Not for embedders. It is public because the standard library
	 * checkpoints live in another package.
	 *
	 * @param Frame is the frame being parked in, or null to clear.
	 */
	public void publishParkedFrame(CallStack Frame) {
		ThreadScope scope = this.interpreterThreads.get(Long.valueOf(Thread.currentThread().getId()));
		if (scope != null) {
			scope.setParkedFrame(Frame);
		}
	}

	/**
	 * Converts a StackOverflowError into an Aussom exception, so an
	 * Error never escapes the interpreter to a caller that is catching
	 * Exception. The Java stack trace is not copied into the
	 * script-visible message: it is thousands of frames of interpreter
	 * internals. It goes to this engine's logger instead.
	 *
	 * @param e is the error that was caught.
	 * @param cs is the call stack to report, may be null.
	 * @return An AussomException with id STACK_OVERFLOW.
	 */
	public AussomException stackOverflowToException(StackOverflowError e, CallStack cs) {
		this.getLogger().err("Interpreter ran out of Java stack. "
			+ "Deepest interpreter frames: " + topFrames(e, 6));
		String trace = "";
		if (cs != null) trace = cs.getStackTrace();
		AussomException ex = new AussomException(AussomException.exType.exRuntime);
		ex.setException(-1, STACK_OVERFLOW_ID,
			"The interpreter ran out of stack space while evaluating this program. "
				+ "Deeply nested expressions or data, or recursion the call depth "
				+ "limit could not see.",
			"Raise the thread stack size, simplify the expression or data being "
				+ "evaluated, or lower '" + Limits.CALL_DEPTH_PROP + "'.",
			trace);
		return ex;
	}

	/**
	 * First few frames of a throwable, for the logger. Kept short: an
	 * overflow trace is thousands of frames of the same cycle.
	 */
	public static String topFrames(Throwable t, int count) {
		StackTraceElement[] els = t.getStackTrace();
		StringBuilder sb = new StringBuilder();
		int n = count;
		if (els.length < n) n = els.length;
		for (int i = 0; i < n; i++) {
			if (i > 0) sb.append(" <- ");
			sb.append(els[i].toString());
		}
		return sb.toString();
	}

	/* ============================================================
	 * Debugging
	 *
	 * See design/debugging-interface-design.md.
	 * ============================================================ */

	/**
	 * Registers (or clears) the debugger. Setting a non-null
	 * debugger turns debug mode on; setting null turns it off.
	 *
	 * Contract: must be called before any interpreter thread
	 * starts running. The debugMode field is a plain boolean and
	 * relies on safe publication via thread start to be visible to
	 * interpreter threads. Attaching a debugger to an
	 * already-running interpreter is not supported.
	 *
	 * The debugger reference itself is volatile, so it may be
	 * swapped during an active debug session (hot-swap, detach,
	 * replace) and the change is visible to interpreter threads on
	 * their next eval.
	 *
	 * Gated by the security property aussom.debugger.enable.
	 * Throws an aussomException when attaching a debugger if the
	 * property is false. Detach (d == null) is always allowed so a
	 * caller can clear a previously-attached debugger regardless of
	 * the current property value.
	 *
	 * @param d The DebuggerInt implementation, or null to clear.
	 * @throws aussomException on security denial.
	 */
	public void setDebugger(DebuggerInt d) throws aussomException {
		if (d != null) {
			if (!this.secman.getPropertyBoolean("aussom.debugger.enable", false)) {
				throw new aussomException(
					"Engine.setDebugger: Security exception, action "
					+ "'aussom.debugger.enable' not permitted.");
			}
		}
		this.debugger = d;
		this.debugMode = (d != null);
	}

	/**
	 * Returns the currently registered debugger, or null.
	 * @return A DebuggerInt or null.
	 */
	public DebuggerInt getDebugger() {
		return this.debugger;
	}

	/**
	 * Returns true if a debugger is currently registered.
	 * @return A boolean with true for enabled and false for not.
	 */
	public final boolean isDebugMode() {
		return this.debugMode;
	}

	/**
	 * Returns the per-thread "last seen" throwable used by the
	 * post-eval exception hook in astNode.eval to dedupe
	 * onException(Exception, ...) calls across stack frames the
	 * throwable unwinds through. Engine-internal; exposed for the
	 * eval hook to access.
	 * @return The ThreadLocal holding the last-seen throwable.
	 */
	public ThreadLocal<Throwable> getLastSeenThrowable() {
		return this.lastSeenThrowable;
	}

	/**
	 * Walks every class registered in the engine (and the
	 * synthetic script class, if script mode is on) recursively
	 * and returns every astNode whose getFileName() and
	 * getLineNum() match the supplied values. The debugger uses
	 * this to translate a user-supplied "set breakpoint at
	 * file.aus:42" into the AST node(s) to mark.
	 *
	 * Cost is O(N) in total node count. Run once per "set
	 * breakpoint" request, not on the hot path.
	 *
	 * @param fileName The file name to match (must equal getFileName()).
	 * @param lineNumber The line number to match (must equal getLineNum()).
	 * @return A list of matching nodes in source order, possibly empty.
	 */
	public List<astNode> findNodesByLine(String fileName, int lineNumber) {
		final List<astNode> matches = new ArrayList<astNode>();
		final String file = fileName;
		final int line = lineNumber;
		java.util.function.Consumer<astNode> visitor = new java.util.function.Consumer<astNode>() {
			@Override public void accept(astNode n) {
				if (n.getLineNum() == line
						&& n.getFileName() != null
						&& n.getFileName().equals(file)) {
					matches.add(n);
				}
			}
		};
		debuggerVisitAll(visitor);
		return matches;
	}

	/**
	 * Convenience method that sets a breakpoint at the given
	 * file and line. Marks the first node returned by
	 * findNodesByLine and leaves any other nodes on the same
	 * line unmarked. Returns true if a node was found and
	 * marked, false if the line has no executable code.
	 *
	 * @param fileName The file name to match.
	 * @param lineNumber The line number to match.
	 * @return true if a node was marked, false otherwise.
	 */
	public boolean setBreakpoint(String fileName, int lineNumber) {
		List<astNode> hits = this.findNodesByLine(fileName, lineNumber);
		if (hits.isEmpty()) return false;
		hits.get(0).breakpoint = true;
		return true;
	}

	/**
	 * Convenience method that clears any breakpoints set at
	 * the given file and line. Unsets the breakpoint flag on
	 * every matching node (not just the first) so that this
	 * call undoes a prior setBreakpoint as well as any nodes
	 * the caller marked manually via findNodesByLine.
	 *
	 * @param fileName The file name to match.
	 * @param lineNumber The line number to match.
	 * @return true if at least one breakpoint was cleared, false otherwise.
	 */
	public boolean clearBreakpoint(String fileName, int lineNumber) {
		List<astNode> hits = this.findNodesByLine(fileName, lineNumber);
		boolean any = false;
		for (astNode n : hits) {
			if (n.breakpoint) {
				n.breakpoint = false;
				any = true;
			}
		}
		return any;
	}

	/**
	 * Convenience method that clears every breakpoint flag in
	 * the AST — across every registered class and the
	 * synthetic script class. Useful for DAP-style "remove all
	 * breakpoints" handling.
	 *
	 * @return true if at least one breakpoint was cleared, false otherwise.
	 */
	public boolean clearAllBreakpoints() {
		final boolean[] any = new boolean[] { false };
		java.util.function.Consumer<astNode> visitor = new java.util.function.Consumer<astNode>() {
			@Override public void accept(astNode n) {
				if (n.breakpoint) {
					n.breakpoint = false;
					any[0] = true;
				}
			}
		};
		debuggerVisitAll(visitor);
		return any[0];
	}

	/**
	 * Helper: invokes the supplied visitor on every astNode in
	 * the engine — across every registered class and the
	 * synthetic script class, if one exists. Single entry
	 * point shared by findNodesByLine, clearAllBreakpoints,
	 * and any other walker-based debugger helper.
	 */
	private void debuggerVisitAll(java.util.function.Consumer<astNode> visitor) {
		for (astClass cls : this.classes.values()) {
			debuggerVisitClass(cls, visitor);
		}
		if (this.scriptClass != null) {
			debuggerVisitClass(this.scriptClass, visitor);
		}
	}

	/**
	 * Helper: walks a class definition's members and functions
	 * and invokes the visitor on every node.
	 */
	private void debuggerVisitClass(astClass cls, java.util.function.Consumer<astNode> visitor) {
		if (cls == null) return;
		visitor.accept(cls);
		for (astNode m : cls.getMembers().values()) {
			debuggerVisitNode(m, visitor);
		}
		for (astFunctDef f : cls.getAllFunctions()) {
			debuggerVisitNode(f, visitor);
		}
	}

	/**
	 * Helper: walks an arbitrary AST node by subclass-aware
	 * recursion. Knows the child shapes of every astNode
	 * subclass that owns sub-nodes.
	 */
	private void debuggerVisitNode(astNode n, java.util.function.Consumer<astNode> visitor) {
		if (n == null) return;
		visitor.accept(n);

		// Dispatch by concrete subclass to walk children.
		if (n instanceof astFunctDef) {
			astFunctDef fd = (astFunctDef) n;
			debuggerVisitNode(fd.getArgList(), visitor);
			debuggerVisitNode(fd.getInstructionList(), visitor);
		} else if (n instanceof astStatementList) {
			for (astNode s : ((astStatementList) n).getStatements()) {
				debuggerVisitNode(s, visitor);
			}
		} else if (n instanceof astFunctDefArgsList) {
			for (astNode a : ((astFunctDefArgsList) n).getArgs()) {
				debuggerVisitNode(a, visitor);
			}
		} else if (n instanceof astExpression) {
			astExpression e = (astExpression) n;
			debuggerVisitNode(e.getLeft(), visitor);
			debuggerVisitNode(e.getRight(), visitor);
		} else if (n instanceof astIfElse) {
			astIfElse ie = (astIfElse) n;
			debuggerVisitNode(ie.getIfCondition(), visitor);
			for (astNode c : ie.getIfElseConditions()) {
				debuggerVisitNode(c, visitor);
			}
			debuggerVisitNode(ie.getElseInstructionList(), visitor);
		} else if (n instanceof astConditionBlock) {
			astConditionBlock cb = (astConditionBlock) n;
			debuggerVisitNode(cb.getExpression(), visitor);
			debuggerVisitNode(cb.getInstructionList(), visitor);
		} else if (n instanceof astSwitch) {
			astSwitch sw = (astSwitch) n;
			debuggerVisitNode(sw.getExpression(), visitor);
			for (astNode c : sw.getCaseConditions()) {
				debuggerVisitNode(c, visitor);
			}
			debuggerVisitNode(sw.getDefaultList(), visitor);
		} else if (n instanceof astTryCatch) {
			astTryCatch tc = (astTryCatch) n;
			debuggerVisitNode(tc.getTryInstList(), visitor);
			debuggerVisitNode(tc.getCatchInstList(), visitor);
		} else if (n instanceof astWhile) {
			astWhile w = (astWhile) n;
			debuggerVisitNode(w.getExpr(), visitor);
			debuggerVisitNode(w.getInstructions(), visitor);
		} else if (n instanceof astFor) {
			astFor f = (astFor) n;
			debuggerVisitNode(f.getExprInit(), visitor);
			debuggerVisitNode(f.getExprCond(), visitor);
			debuggerVisitNode(f.getExprInc(), visitor);
			debuggerVisitNode(f.getEachVar(), visitor);
			debuggerVisitNode(f.getEachExpr(), visitor);
			debuggerVisitNode(f.getInstructions(), visitor);
		} else if (n instanceof astFunctCall) {
			debuggerVisitNode(((astFunctCall) n).getArgs(), visitor);
		} else if (n instanceof astNewInst) {
			debuggerVisitNode(((astNewInst) n).getArgs(), visitor);
		} else if (n instanceof astReturn) {
			debuggerVisitNode(((astReturn) n).getValue(), visitor);
		} else if (n instanceof astThrow) {
			debuggerVisitNode(((astThrow) n).getExpression(), visitor);
		} else if (n instanceof astObj) {
			debuggerVisitNode(((astObj) n).getIndex(), visitor);
		} else if (n instanceof astVar) {
			debuggerVisitNode(((astVar) n).getAssociative(), visitor);
		} else if (n instanceof astList) {
			for (astNode item : ((astList) n).getItems()) {
				debuggerVisitNode(item, visitor);
			}
		} else if (n instanceof astMap) {
			for (Map.Entry<astNode, astNode> e : ((astMap) n).getItems().entrySet()) {
				debuggerVisitNode(e.getKey(), visitor);
				debuggerVisitNode(e.getValue(), visitor);
			}
		} else if (n instanceof astClass) {
			// Nested or inherited class definition encountered through
			// some other node's children; recurse the same way as
			// top-level classes.
			debuggerVisitClass((astClass) n, visitor);
		}

		// Every astNode supports a child chain via getChild() for
		// dot-chained references (x.y.z). Walk it for every node.
		if (n.getChild() != null) {
			debuggerVisitNode(n.getChild(), visitor);
		}
	}

	/**
	 * Parses an Aussom source snippet and evaluates it against
	 * the supplied frame's environment. Used by debuggers to
	 * implement DAP "evaluate" requests (and similar tooling)
	 * that need to inspect or compute values in the context of a
	 * paused frame.
	 *
	 * The source is parsed via the existing parseStatements
	 * building block, so it accepts the same shape as
	 * Engine.evalLine: bare statements, class declarations, and
	 * include directives. Only bare top-level statements are
	 * walked against the supplied frame; class declarations and
	 * includes go through the engine's normal addClass /
	 * addInclude paths.
	 *
	 * Returns the value of the last evaluated statement, or
	 * AussomNull if the source produced no statements. A runtime
	 * error from a statement is returned as an AussomException
	 * value (caught and converted; not thrown).
	 *
	 * Parse errors throw an aussomException.
	 *
	 * Gated by the security property aussom.debugger.enable.
	 * Throws an aussomException if the property is false. The
	 * check runs on every entry so that revoking the property at
	 * runtime (via setProp on a permissive manager) immediately
	 * blocks further evaluation, even if a debugger is still
	 * attached.
	 *
	 * @param source The Aussom source snippet to evaluate.
	 * @param frame The Environment of the paused frame.
	 * @return An AussomType with the last value.
	 * @throws Exception on parse error or security denial.
	 */
	public AussomType evalInFrame(String source, Environment frame) throws Exception {
		// Security check on every entry; defends against runtime
		// property changes via setProp.
		if (!this.secman.getPropertyBoolean("aussom.debugger.enable", false)) {
			throw new aussomException(
				"Engine.evalInFrame: Security exception, action "
				+ "'aussom.debugger.enable' not permitted.");
		}

		astStatementList parsed = new astStatementList();
		this.parseStatements("<eval>", source, 0, parsed);
		if (this.hasParseErrors) {
			this.clearParseError();
			throw new aussomException("Engine.evalInFrame: parse error.");
		}

		AussomType last = new AussomNull();
		List<astNode> stmts = parsed.getStatements();
		for (int i = 0; i < stmts.size(); i++) {
			try {
				last = stmts.get(i).eval(frame, false);
			} catch (aussomException e) {
				last = new AussomException(
					"Engine.evalInFrame: uncaught exception during "
					+ "evaluation: " + e.getMessage());
				break;
			}
			if (last.isEx()) break;
			if (last.isReturn()) {
				last = ((AussomReturn) last).getValue();
				break;
			}
			if (last.isBreak()) break;
		}
		return last;
	}

	/* ============================================================
	 * Script mode
	 *
	 * Script mode is a self-contained facility on the engine that
	 * lets an embedder evaluate top-level statements (assignments,
	 * expressions, control flow) without wrapping them in a class
	 * and main. It does NOT touch the classical run pipeline:
	 * setMainClassAndFunct, callMain, instantiateStaticClasses, and
	 * the contents of this.classes are unchanged. The synthetic
	 * script class is deliberately kept out of this.classes so the
	 * classical pipeline never sees it.
	 *
	 * See design/script-mode-design.md for the full design.
	 * ============================================================ */

	/**
	 * Returns true if script mode is currently enabled.
	 * @return A boolean with true for enabled and false for not.
	 */
	public boolean isScriptMode() {
		return this.scriptMode;
	}

	/**
	 * Enables or disables script mode. Enabling builds a synthetic
	 * __script_main class with an empty main(args), instantiates it
	 * against a long-lived Environment whose Members persist across
	 * evalLine calls, and prepares the engine to accept evalLine
	 * input. The synthetic class is NOT registered in this.classes;
	 * the classical run path remains independent. Disabling does
	 * not destroy the synthetic class — it just gates further
	 * evalLine calls.
	 *
	 * Gated by the security property aussom.script.mode.enable.
	 * Throws an aussomException if the property is false when
	 * enabling.
	 *
	 * @param on is a boolean with true to enable script mode.
	 * @throws aussomException on security denial or instantiation
	 *         failure of the synthetic class.
	 */
	public void setScriptMode(boolean on) throws aussomException {
		if (this.scriptMode == on) return;
		if (on) {
			// Security check (every entry; defends against runtime
			// property changes via setProp).
			if (!this.secman.getPropertyBoolean("aussom.script.mode.enable", false)) {
				throw new aussomException(
					"Engine.setScriptMode: Security exception, action "
					+ "'aussom.script.mode.enable' not permitted.");
			}

			// Build the synthetic class with an empty main(args).
			this.scriptClass = new astClass(SCRIPT_CLASS_NAME);
			this.scriptClass.setParserInfo("<script>", 1, 1);
			this.scriptMainFn = new astFunctDef("main");
			this.scriptMainFn.setParserInfo("<script>", 1, 1);
			this.scriptMainFn.setAccessType(AccessType.aPublic);
			astFunctDefArgsList args = new astFunctDefArgsList();
			astVar argsVar = new astVar();
			argsVar.setName("args");
			args.addNode(argsVar);
			this.scriptMainFn.setArgList(args);
			this.scriptMainFn.setInstructionList(new astStatementList());
			this.scriptClass.addFunction("main", this.scriptMainFn);

			// Build the long-lived Environment with persistent
			// Members and instantiate the synthetic class against
			// it.
			this.scriptEnv = new Environment(this);
			Members locals = new Members();
			this.scriptEnv.setEnvironment(null, locals, new CallStack());
			AussomType inst = this.scriptClass.instantiate(this.scriptEnv, false, new AussomList());
			if (inst.isEx()) {
				throw new aussomException(this.scriptClass,
					((AussomException) inst).getText(), "");
			}
			this.scriptInstance = (AussomObject) inst;
			this.scriptEnv.setClassInstance(this.scriptInstance);
			// Leave curObj null. Top-level identifiers go through
			// astObj.evalObjStart which checks locals and static
			// classes; setting curObj would force evalObj which
			// only resolves members of the current object.
			this.scriptCursor = 0;
		}
		this.scriptMode = on;
	}

	/**
	 * Sets the file name reported on AST nodes parsed by evalLine.
	 * The default is {@code "<script>"}. Embedders feeding source from a
	 * real file call this once after setScriptMode(true) so error
	 * attribution points at the original source file.
	 * @param fileName is the file name to report.
	 */
	public void setScriptFileName(String fileName) {
		this.scriptFileName = fileName;
	}

	/**
	 * Returns the current script-mode file name.
	 * @return A String with the script-mode file name.
	 */
	public String getScriptFileName() {
		return this.scriptFileName;
	}

	/**
	 * Returns the synthetic __script_main class definition built
	 * by setScriptMode(true), or null if script mode has not been
	 * enabled. The synthetic class is not registered in
	 * this.classes; this accessor is the bridge for tooling that
	 * needs to walk the synthetic main's body (e.g. an LSP
	 * provider that analyzes top-level statements).
	 *
	 * Read-only; does not trigger initialization, parsing, or
	 * evaluation.
	 *
	 * @return An astClass for the synthetic class, or null.
	 */
	public astClass getScriptClass() {
		return this.scriptClass;
	}

	/**
	 * Returns the long-lived Environment that script mode evaluates
	 * top-level statements against, or null if script mode has not
	 * been enabled. Its locals hold every top-level binding the
	 * session has made, so this accessor is the bridge for tooling
	 * that needs to read or manage session scope (e.g. a REPL
	 * inspector listing what is currently defined).
	 *
	 * The returned Environment is the live one, not a copy, and its
	 * locals are the live Members. Callers that mutate it change the
	 * running session's scope.
	 *
	 * Read-only; does not trigger initialization, parsing, or
	 * evaluation.
	 *
	 * @return An Environment for the script scope, or null.
	 */
	public Environment getScriptEnvironment() {
		return this.scriptEnv;
	}

	/**
	 * Single-argument convenience wrapper that calls
	 * evalLine(source, 1).
	 * @param source is the Aussom source string to parse and run.
	 * @return An AussomType with the value of the last evaluated
	 *         statement, or AussomNull if the source was empty.
	 * @throws Exception on parse error, security denial, or other
	 *         engine failure.
	 */
	public AussomType evalLine(String source) throws Exception {
		return this.evalLine(source, 1);
	}

	/**
	 * Parses the supplied Aussom source as a script-mode fragment,
	 * appends any parsed top-level statements to the synthetic
	 * main's body, and evaluates only the newly-appended statements
	 * against the long-lived script Environment. Returns the
	 * AussomType produced by the last evaluated statement.
	 *
	 * Convenience wrapper that calls parseScriptLine followed by
	 * evalParsedScript. Splitting them lets a debugger arm
	 * breakpoints against the newly-parsed nodes between the two
	 * phases; embedders that do not need that seam can keep using
	 * this single-call form.
	 *
	 * The lineNumber argument is 1-indexed and tells the lexer
	 * which line the first source line should report as. Pass the
	 * file line where the snippet starts so error attribution
	 * remains correct (e.g. lineNumber=42 for a snippet that
	 * begins on line 42 of the original file).
	 *
	 * Parse errors throw an aussomException after rolling back any
	 * partially-appended statements. Runtime errors (from a
	 * statement returning or throwing an exception) are returned
	 * as an AussomException value, never re-thrown.
	 *
	 * @param source is the Aussom source string to parse and run.
	 * @param lineNumber is the 1-indexed line number to report
	 *        for the first source line.
	 * @return An AussomType with the value of the last evaluated
	 *         statement, or AussomNull if the source was empty.
	 * @throws Exception on parse error or security denial.
	 */
	public AussomType evalLine(String source, int lineNumber) throws Exception {
		astStatementList body = this.parseScriptLine(source, lineNumber);
		return this.evalParsedScript(body);
	}

	/**
	 * Parses the supplied Aussom source as a script-mode fragment
	 * and appends the parsed top-level statements to the synthetic
	 * main's body. Does NOT evaluate. Returns the script main's
	 * instruction-list body so the caller can pass it back to
	 * evalParsedScript.
	 *
	 * This is the parse half of evalLine. Splitting parse and eval
	 * gives a debugger (or any other consumer) a place to inspect
	 * or mutate the newly-parsed AST before it runs — for example
	 * to call setBreakpoint on lines that did not exist as nodes
	 * before this call.
	 *
	 * The lineNumber argument is 1-indexed and tells the lexer
	 * which line the first source line should report as.
	 *
	 * Parse errors throw an aussomException after rolling back any
	 * partially-appended statements so the body is left exactly as
	 * it was before this call.
	 *
	 * Gated by the security property aussom.script.mode.enable;
	 * checked on every entry so a permissive manager that revokes
	 * the property at runtime immediately blocks further parsing.
	 *
	 * @param source is the Aussom source string to parse.
	 * @param lineNumber is the 1-indexed line number to report
	 *        for the first source line.
	 * @return The script main's instruction-list body, with any
	 *         newly-parsed statements appended.
	 * @throws Exception on parse error or security denial.
	 */
	public astStatementList parseScriptLine(String source, int lineNumber) throws Exception {
		if (!this.scriptMode) {
			throw new aussomException(
				"Engine.parseScriptLine: script mode is not enabled.");
		}
		if (!this.secman.getPropertyBoolean("aussom.script.mode.enable", false)) {
			throw new aussomException(
				"Engine.parseScriptLine: Security exception, action "
				+ "'aussom.script.mode.enable' not permitted.");
		}

		astStatementList body = this.scriptMainFn.getInstructionList();
		List<astNode> stmts = body.getStatements();

		// Snapshot the body size before the parse so a parse error
		// can roll back any statements the parser already appended.
		int sliceStart = stmts.size();

		// lineNumber is 1-indexed; the lexer adds (lineNumber - 1)
		// to its yyline+1 so the first source line reports as
		// lineNumber.
		this.parseStatements(this.scriptFileName, source, lineNumber - 1, body);
		if (this.hasParseErrors) {
			while (stmts.size() > sliceStart) {
				stmts.remove(stmts.size() - 1);
			}
			this.clearParseError();
			throw new aussomException(
				"Engine.parseScriptLine: parse error.");
		}

		return body;
	}

	/**
	 * Evaluates any statements in the supplied body that have not
	 * yet been evaluated -- that is, statements at indices >=
	 * scriptCursor. Advances scriptCursor past the slice before
	 * the eval loop runs, so a runtime failure mid-slice does not
	 * leave unwalked statements for the next call to pick up.
	 *
	 * The body argument is expected to be the same instruction
	 * list returned by parseScriptLine (the synthetic script
	 * main's body). Passing a different list is not supported --
	 * the scriptCursor watermark is engine state and only relates
	 * to that one body.
	 *
	 * Returns the AussomType produced by the last evaluated
	 * statement, or AussomNull if no statements were pending.
	 * Runtime errors (a statement returning or throwing) are
	 * returned as an AussomException value, never re-thrown.
	 *
	 * Gated by the security property aussom.script.mode.enable;
	 * checked on every entry so a permissive manager that revokes
	 * the property between parse and eval immediately blocks the
	 * eval.
	 *
	 * @param body the script main's instruction-list body from
	 *        parseScriptLine.
	 * @return An AussomType with the value of the last evaluated
	 *         statement, or AussomNull if the slice was empty.
	 * @throws Exception on security denial.
	 */
	public AussomType evalParsedScript(astStatementList body) throws Exception {
		if (!this.scriptMode) {
			throw new aussomException(
				"Engine.evalParsedScript: script mode is not enabled.");
		}
		if (!this.secman.getPropertyBoolean("aussom.script.mode.enable", false)) {
			throw new aussomException(
				"Engine.evalParsedScript: Security exception, action "
				+ "'aussom.script.mode.enable' not permitted.");
		}

		List<astNode> stmts = body.getStatements();
		int sliceStart = this.scriptCursor;
		int sliceEnd = stmts.size();

		// Commit the cursor advance for the whole slice up front so
		// an exception or return mid-slice does not leave unwalked
		// statements for the next call to pick up.
		this.scriptCursor = sliceEnd;

		// Pick up any limit the host changed since the last submission.
		this.refreshLimits();

		AussomType last = new AussomNull();
		try (ThreadScope scope = this.enterInterpreterThread()) {
			for (int i = sliceStart; i < sliceEnd; i++) {
				try {
					last = stmts.get(i).eval(this.scriptEnv, false);
				} catch (aussomException e) {
					// Convert any thrown evaluation exception into a
					// returnable AussomException so the caller always
					// gets an AussomType back.
					last = new AussomException(
						"Engine.evalParsedScript: uncaught exception during "
						+ "evaluation: " + e.getMessage());
					break;
				} catch (StackOverflowError soe) {
					// Same contract as a runtime error here: the caller
					// gets a value back, never an Error.
					last = this.stackOverflowToException(soe, this.mainCallStack);
					break;
				}
				if (last.isEx()) break;
				if (last.isReturn()) {
					last = ((AussomReturn) last).getValue();
					break;
				}
				if (last.isBreak()) break;
			}
		}
		return last;
	}

	/**
	 * Protected building block that parses an Aussom source string
	 * and populates a caller-supplied astStatementList with the
	 * parsed top-level statements. Class declarations and includes
	 * encountered during the parse still flow through the existing
	 * addClass / addInclude paths on the engine; only bare
	 * top-level statements go to the supplied target.
	 *
	 * Wraps p.parse() in a try/catch and converts any thrown
	 * exception to the engine's parse-error flag so callers handle
	 * parse failure uniformly via hasParseErrors() — no try/catch
	 * needed at the call site.
	 *
	 * Does NOT mutate this.fileNames; each call is a transient
	 * parse, not a "loaded file."
	 *
	 * Visibility is protected; subclasses (e.g. a future debugger-
	 * aware Engine) can call it directly.
	 *
	 * @param fileName is the file name to attach to AST nodes.
	 * @param source is the Aussom source string to parse.
	 * @param lineOffset is added to lexer-reported line numbers
	 *        (so the first source line reports as
	 *        lineOffset + 1).
	 * @param target is the caller-supplied list to receive parsed
	 *        top-level statements.
	 */
	protected void parseStatements(String fileName, String source, int lineOffset, astStatementList target) {
		// One submission is one parse. Drop diagnostics from any
		// previous submission so the caller reads only its own.
		this.clearParseDiagnostics();

		Lexer scanner = new Lexer(new StringReader(source), fileName);
		scanner.setLineOffset(lineOffset);
		scanner.setDiagnosticSink(this);
		parser p = new parser(scanner, this, fileName, this.loadExternClasses, target);
		try {
			p.parse();
		} catch (Exception e) {
			// Convert any thrown parser-level exception (semantic
			// action raise, lexer fatal, etc.) to the parse-error
			// flag. The exception carries no position we can rely on,
			// so the diagnostic is file-level.
			this.getLogger().err(e.getMessage());
			this.addParseDiagnostic(new ParseDiagnostic(fileName,
				ParseDiagnostic.NO_POSITION, ParseDiagnostic.NO_POSITION,
				e.getMessage()));
			this.setParseError();
		}
		if (scanner.hasErrors()) {
			this.setParseError();
		}

		// Closures defined in top-level statements never pass a
		// classSection, so they are still pending when the parse
		// finishes. Hoist them onto the synthetic script class. With
		// no script class to receive them there is nowhere for the
		// definition to live, so that is a parse error.
		List<astFunctDef> leftovers = p.drainPendingClosures();
		if (!leftovers.isEmpty()) {
			if (this.scriptClass != null) {
				// Tracks the closure being added so a failure can be
				// attributed to it rather than to the file at large.
				astFunctDef adding = null;
				try {
					for (astFunctDef c : leftovers) {
						adding = c;
						this.scriptClass.addFunction(c.getName(), c);
					}
				} catch (aussomException e) {
					// Duplicate closure name/signature on the script
					// class.
					this.getLogger().err(e.getMessage());
					this.addParseDiagnostic(new ParseDiagnostic(fileName,
						adding.getLineNum(), adding.getColNum(),
						e.getMessage()));
					this.setParseError();
				}
			} else {
				astFunctDef orphan = leftovers.get(0);
				String msg = "PARSE_ERROR: Closure defined outside a class with no "
					+ "script class to receive it.";
				this.getLogger().err(fileName + " [" + orphan.getLineNum() + "]: " + msg);
				this.addParseDiagnostic(new ParseDiagnostic(fileName,
					orphan.getLineNum(), orphan.getColNum(), msg));
				this.setParseError();
			}
		}
	}

	/**
	 * Obligatory toString method.
	 * @return A String representing the engine includes and classes.
	 */
	@Override
	public String toString() {
		String rstr = "";

		rstr += "Parser loaded the following aussom files ...\n";
		for(int i = 0; i < this.includes.size(); i++) {
			rstr += "INCLUDE={'" + this.includes.get(i) + "'}\n";
		}
		rstr += "\n";

		rstr += "loadClassList found the following classes ...\n";
		for(String className : this.classes.keySet()) {
			rstr += "CLASS={" + className + "}\n";
			rstr += this.classes.get(className).toString();
		}
		rstr += "\n";

		return rstr;
	}
}

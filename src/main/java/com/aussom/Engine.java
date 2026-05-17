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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aussom.ast.*;
import com.aussom.stdlib.Lang;
import com.aussom.stdlib.console;
import com.aussom.types.*;

/**
 * Engine object represents a Aussom interpreter instance. Then engine handles 
 * parsing files and strings, storing includes/classes and running Aussom 
 * code.
 * @author austin
 */
public class Engine {
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
	 * This flag is set if the parser encounters errors. This is used 
	 * when parsing the initial source code prior to running the application. If 
	 * set to true the interpreter will fail to start.
	 */
	private boolean hasParseErrors = false;
	
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

	/**
	 * Default constructor. When called this gets an instance of the Universe object 
	 * and initializes it if not already done. It loads universe classes and instantiates 
	 * static classes. Finally it sets the initComplete flag to true.
	 * @throws Exception on failure to instantiate SecurityManagerImpl object.
	 */
	public Engine () throws Exception {
		this(new SecurityManagerImpl());
	}
	
	/**
	 * Default constructor. When called this gets an instance of the Universe object 
	 * and initializes it if not already done. It loads universe classes and instantiates 
	 * static classes. Finally it sets the initComplete flag to true.
	 * @param SecMan is a SecurityManagerImpl object for the engine.
	 * @throws Exception on init failure or failure to instantiate static classes.
	 */
	public Engine(SecurityManagerInt SecMan) throws Exception {
		this.secman = SecMan;
		
		Universe u = Universe.get();
		u.init(this);
		
		// If needed, load base classes.
		this.loadUniverseClasses();
		
		// Instantiate the static classes.
		this.instantiateStaticClasses();
		
		this.initComplete = true;
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
	 * Adds a Aussom include to the interpreter. The include can be a standard library 
	 * language include. It can also be a file that exists in one f the includePaths 
	 * if any are set.
	 * @param Include is a String with the include to add.
	 * @throws Exception on parse failure.
	 */
	public void addInclude(String Include) throws Exception {
		boolean found = false;
		console.get().trc("Engine.addInclude(): Include: " + Include);
		if (Lang.get().getLangIncludes().containsKey(Include)) {
			found = true;
			if (!this.includes.contains(Include)) {
				console.get().trc("Engine.addInclude(): Adding langInclude: " + Include);
				this.includes.add(Include);
				this.parseString("/com/aussom/stdlib/aus/" + Include, Lang.get().getLangIncludes().get(Include));
			}
		} else {
			console.get().trc("Engine.addInclude(): Attempting to find in resourceIncludePaths ...");
			for (String pth : this.resourceIncludePaths) {
				List<String> resDir = Lang.get().listResourceDirectory(pth);
				String tinc = pth + Include;
				for (String fname : resDir) {
					if (fname.contains(tinc)) {
						found = true;
						if (!this.includes.contains(Include)) {
							console.get().trc("Engine.addInclude(): Include " + Include + " found in '" + fname + "'");
							this.includes.add(tinc);
							this.parseString(tinc, Util.loadResource(tinc));
							return;
						}
					}
				}
			}

			if (!found) {
				console.get().trc("Engine.addInclude(): Attempting to find in includePaths ...");
				for (String pth : this.includePaths) {
					String tinc = pth + Include;
					// This could be different than tinc because of Windoz ...
					String localIncPath = tinc.replace("/", System.getProperty("file.separator"));
					if (!this.isPathExcludePath(tinc)) {
						File f = new File(localIncPath);
						if (f.exists()) {
							found = true;
							if (!this.includes.contains(tinc)) {
								console.get().trc("Engine.addInclude(): Include " + Include + " found in '" + pth + "'");
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
					console.get().trc("Engine.addInclude(): Include '" + Include + "' not found at all.");
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
	 * This function loads the native type class definitions 
	 * in the Engine class set. This is need if you want to 
	 * say create a new native type using the new operator.
	 */
	public void loadUniverseClasses() {
		// Add universe classes.
		Map<String, astClass> clses = Universe.get().getClasses();
		for (String key : clses.keySet()) {
			if (!this.classes.containsKey(key)) {
				this.classes.put(key, clses.get(key));
			}
		}
	}
	
	/**
	 * The interpreter will parse the Aussom code file with the provided file name.
	 * @param FileName is a String with the Aussom code file to parse.
	 * @throws Exception on parse failure.
	 */
	public void parseFile(String FileName) throws Exception {
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
		Lexer scanner = new Lexer(new StringReader(Contents), FileName);
		parser p = new parser(scanner, this, FileName, this.loadExternClasses);
		p.parse();
		// P2: lexer errors (e.g. illegal characters) are reported via
		// console.err but historically did not halt parsing. Promote
		// them to parse errors so the engine refuses to run code that
		// the lexer could not fully tokenize.
		if (scanner.hasErrors()) {
			this.setParseError();
		}
		this.fileNames.add(FileName);
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
			console.get().trc("Running program now ...");
	
			this.mainCallStack = new CallStack();
			
			// Set the main class and function.
			if (this.setMainClassAndFunct()) {
				return this.callMain();
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
			console.get().trc("Instantiating static class: " + ac.getName());
			AussomType aci = null;
			Environment tenv = new Environment(this);
			Members locals = new Members();
			tenv.setEnvironment((AussomObject) aci, locals, this.mainCallStack);
			aci = (AussomObject) ac.instantiate(tenv, false, new AussomList());
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

		AussomType tci = this.mainClassDef.instantiate(tenv, false, new AussomList());
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
				console.get().err(((AussomTypeInt) ex).str());
				return 1;
			} else if (ret instanceof AussomInt) {
				return (int)((AussomInt)ret).getNumericInt();
			}
		} else {
			AussomException ex = (AussomException)tci;
			console.get().err(ex.toString());
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

			AussomType result = this.classes.get(Name).instantiate(tenv, false, Args == null ? new AussomList() : Args);
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
	 */
	public void clearParseError() {
		this.hasParseErrors = false;
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
	 * @param d The DebuggerInt implementation, or null to clear.
	 */
	public void setDebugger(DebuggerInt d) {
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
		List<astNode> matches = new ArrayList<astNode>();
		for (astClass cls : this.classes.values()) {
			debuggerCollectFromClass(cls, fileName, lineNumber, matches);
		}
		if (this.scriptClass != null) {
			debuggerCollectFromClass(this.scriptClass, fileName, lineNumber, matches);
		}
		return matches;
	}

	/**
	 * Helper for findNodesByLine: walks a class definition's
	 * members and functions.
	 */
	private void debuggerCollectFromClass(astClass cls, String file, int line, List<astNode> matches) {
		if (cls == null) return;
		debuggerMatchAndAdd(cls, file, line, matches);
		for (astNode m : cls.getMembers().values()) {
			debuggerCollectFromNode(m, file, line, matches);
		}
		for (astFunctDef f : cls.getAllFunctions()) {
			debuggerCollectFromNode(f, file, line, matches);
		}
	}

	/**
	 * Helper for findNodesByLine: walks an arbitrary AST node by
	 * subclass-aware recursion. Knows the child shapes of every
	 * astNode subclass that owns sub-nodes.
	 */
	private void debuggerCollectFromNode(astNode n, String file, int line, List<astNode> matches) {
		if (n == null) return;
		debuggerMatchAndAdd(n, file, line, matches);

		// Dispatch by concrete subclass to walk children.
		if (n instanceof astFunctDef) {
			astFunctDef fd = (astFunctDef) n;
			debuggerCollectFromNode(fd.getArgList(), file, line, matches);
			debuggerCollectFromNode(fd.getInstructionList(), file, line, matches);
		} else if (n instanceof astStatementList) {
			for (astNode s : ((astStatementList) n).getStatements()) {
				debuggerCollectFromNode(s, file, line, matches);
			}
		} else if (n instanceof astFunctDefArgsList) {
			for (astNode a : ((astFunctDefArgsList) n).getArgs()) {
				debuggerCollectFromNode(a, file, line, matches);
			}
		} else if (n instanceof astExpression) {
			astExpression e = (astExpression) n;
			debuggerCollectFromNode(e.getLeft(), file, line, matches);
			debuggerCollectFromNode(e.getRight(), file, line, matches);
		} else if (n instanceof astIfElse) {
			astIfElse ie = (astIfElse) n;
			debuggerCollectFromNode(ie.getIfCondition(), file, line, matches);
			for (astNode c : ie.getIfElseConditions()) {
				debuggerCollectFromNode(c, file, line, matches);
			}
			debuggerCollectFromNode(ie.getElseInstructionList(), file, line, matches);
		} else if (n instanceof astConditionBlock) {
			astConditionBlock cb = (astConditionBlock) n;
			debuggerCollectFromNode(cb.getExpression(), file, line, matches);
			debuggerCollectFromNode(cb.getInstructionList(), file, line, matches);
		} else if (n instanceof astSwitch) {
			astSwitch sw = (astSwitch) n;
			debuggerCollectFromNode(sw.getExpression(), file, line, matches);
			for (astNode c : sw.getCaseConditions()) {
				debuggerCollectFromNode(c, file, line, matches);
			}
			debuggerCollectFromNode(sw.getDefaultList(), file, line, matches);
		} else if (n instanceof astTryCatch) {
			astTryCatch tc = (astTryCatch) n;
			debuggerCollectFromNode(tc.getTryInstList(), file, line, matches);
			debuggerCollectFromNode(tc.getCatchInstList(), file, line, matches);
		} else if (n instanceof astWhile) {
			astWhile w = (astWhile) n;
			debuggerCollectFromNode(w.getExpr(), file, line, matches);
			debuggerCollectFromNode(w.getInstructions(), file, line, matches);
		} else if (n instanceof astFor) {
			astFor f = (astFor) n;
			debuggerCollectFromNode(f.getExprInit(), file, line, matches);
			debuggerCollectFromNode(f.getExprCond(), file, line, matches);
			debuggerCollectFromNode(f.getExprInc(), file, line, matches);
			debuggerCollectFromNode(f.getEachVar(), file, line, matches);
			debuggerCollectFromNode(f.getEachExpr(), file, line, matches);
			debuggerCollectFromNode(f.getInstructions(), file, line, matches);
		} else if (n instanceof astFunctCall) {
			debuggerCollectFromNode(((astFunctCall) n).getArgs(), file, line, matches);
		} else if (n instanceof astNewInst) {
			debuggerCollectFromNode(((astNewInst) n).getArgs(), file, line, matches);
		} else if (n instanceof astReturn) {
			debuggerCollectFromNode(((astReturn) n).getValue(), file, line, matches);
		} else if (n instanceof astThrow) {
			debuggerCollectFromNode(((astThrow) n).getExpression(), file, line, matches);
		} else if (n instanceof astObj) {
			debuggerCollectFromNode(((astObj) n).getIndex(), file, line, matches);
		} else if (n instanceof astVar) {
			debuggerCollectFromNode(((astVar) n).getAssociative(), file, line, matches);
		} else if (n instanceof astList) {
			for (astNode item : ((astList) n).getItems()) {
				debuggerCollectFromNode(item, file, line, matches);
			}
		} else if (n instanceof astMap) {
			for (Map.Entry<astNode, astNode> e : ((astMap) n).getItems().entrySet()) {
				debuggerCollectFromNode(e.getKey(), file, line, matches);
				debuggerCollectFromNode(e.getValue(), file, line, matches);
			}
		} else if (n instanceof astClass) {
			// Nested or inherited class definition encountered through
			// some other node's children; recurse the same way as
			// top-level classes.
			debuggerCollectFromClass((astClass) n, file, line, matches);
		}

		// Every astNode supports a child chain via getChild() for
		// dot-chained references (x.y.z). Walk it for every node.
		if (n.getChild() != null) {
			debuggerCollectFromNode(n.getChild(), file, line, matches);
		}
	}

	/**
	 * Helper for findNodesByLine: tests a single node and adds it
	 * to the matches list when the file name and line number
	 * match.
	 */
	private void debuggerMatchAndAdd(astNode n, String file, int line, List<astNode> matches) {
		if (n.getLineNum() == line
				&& n.getFileName() != null
				&& n.getFileName().equals(file)) {
			matches.add(n);
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
	 * @param source The Aussom source snippet to evaluate.
	 * @param frame The Environment of the paused frame.
	 * @return An AussomType with the last value.
	 * @throws Exception on parse error.
	 */
	public AussomType evalInFrame(String source, Environment frame) throws Exception {
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
			if (!(Boolean) this.secman.getProperty("aussom.script.mode.enable")) {
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
		if (!this.scriptMode) {
			throw new aussomException(
				"Engine.evalLine: script mode is not enabled.");
		}
		// Security check on every entry; defends against runtime
		// property changes via setProp.
		if (!(Boolean) this.secman.getProperty("aussom.script.mode.enable")) {
			throw new aussomException(
				"Engine.evalLine: Security exception, action "
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
				"Engine.evalLine: parse error.");
		}

		int sliceEnd = stmts.size();

		// Commit the cursor advance for the whole slice up front so
		// an exception or return mid-slice does not leave unwalked
		// statements for the next call to pick up.
		this.scriptCursor = sliceEnd;

		AussomType last = new AussomNull();
		for (int i = sliceStart; i < sliceEnd; i++) {
			try {
				last = stmts.get(i).eval(this.scriptEnv, false);
			} catch (aussomException e) {
				// Convert any thrown evaluation exception into a
				// returnable AussomException so the caller always
				// gets an AussomType back.
				last = new AussomException(
					"Engine.evalLine: uncaught exception during "
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
		Lexer scanner = new Lexer(new StringReader(source), fileName);
		scanner.setLineOffset(lineOffset);
		parser p = new parser(scanner, this, fileName, this.loadExternClasses, target);
		try {
			p.parse();
		} catch (Exception e) {
			// Convert any thrown parser-level exception (semantic
			// action raise, lexer fatal, etc.) to the parse-error
			// flag.
			this.setParseError();
		}
		if (scanner.hasErrors()) {
			this.setParseError();
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

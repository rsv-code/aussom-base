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

package com.aussom.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aussom.CallStack;
import com.aussom.Environment;
import com.aussom.stdlib.UnitTest;
import com.aussom.stdlib.UnitTestClass;
import com.aussom.types.*;
import com.aussom.types.AussomException.exType;

public class astClass extends astNode implements astNodeInt {
	// Has the class init ran yet? Init does things like link to
	// extended classes ...
	private boolean initRan = false;

	// Is the class static.
	private boolean isStatic = false;

	// Is the class external.
	private boolean isExtern = false;

	// External class name.
	private String externClassName = "";

	// List of extended class names.
	private ArrayList<String> extendedClasses = new ArrayList<String>();

	// Includes the direct extended classes along with classes
	// that those extend and so forth.
	private List<String> allExtendedClasses = new ArrayList<>();

	// External class reverence.
	@SuppressWarnings("rawtypes")
	private Class externClass = null;

	// Class members.
	private List<String> membList = new ArrayList<String>();
	private Map<String, astNode> membDefs = new ConcurrentHashMap<String, astNode>();

	// Class functions. The dispatch hot path is a single flat
	// HashMap keyed on "name#sig" (e.g. "setFirstName#s",
	// "draw#i,i"). Each exact-typed overload is pre-registered
	// under every (arity, tag-variant) combination it can match,
	// so a typical call resolves in one HashMap.get + one
	// composite-key build. Wildcard and variadic overloads can't
	// be enumerated as flat keys (they accept arbitrary tags), so
	// they live in separate small lists walked only on the slow
	// path. functList preserves declaration order for iterators
	// that walk every defined method. declaredNames lets
	// hasAnyFunctionByName answer in O(1).
	private List<astFunctDef> functList = new ArrayList<>();
	private Map<String, astFunctDef> dispatchMap = new ConcurrentHashMap<>();
	private List<astFunctDef> wildcardOverloads = new ArrayList<>();
	private List<astFunctDef> variadicOverloads = new ArrayList<>();
	private java.util.Set<String> declaredNames = ConcurrentHashMap.newKeySet();

	// Inherited members and functions are kept track of so we can
	// reproduce the AST later.
	private List<String> inheritedMembers = new ArrayList<String>();
	private List<String> inheritedFuncts = new ArrayList<String>();

	/**
	 * Counts non-wildcard, non-ETCETERA positions in the declared
	 * signature. Higher value = more specific. Used by the
	 * wildcard slow-path matcher to break ties.
	 */
	private static int specificity(astFunctDef d) {
		int spec = 0;
		List<astNode> as = d.getArgList().getArgs();
		for (astNode arg : as) {
			if (arg.getType() == astNodeType.ETCETERA) continue;
			cType t = astFunctDef.effectiveArgType(arg);
			if (t != cType.cUndef) spec++;
		}
		return spec;
	}

	/**
	 * Default constructor.
	 */
	public astClass() {
		this.setType(astNodeType.CLASS);
	}

	/**
	 * Creates a new class.
	 * @param Name A string with the class name.
	 */
	public astClass(String Name) {
		this.setType(astNodeType.CLASS);
		this.setName(Name);
	}

	public void addMember(String Name, astNode Value) {
		this.membList.add(Name);
		this.membDefs.put(Name, Value);
	}

	public boolean containsMember(String Name) {
		return this.membDefs.containsKey(Name);
	}

	public astNode getMember(String Name) {
		return this.membDefs.get(Name);
	}

	public Map<String, astNode> getMembers() {
		return this.membDefs;
	}

	/**
	 * Adds a function definition to this class. The def is
	 * classified by signature shape (exact, wildcard, variadic)
	 * and rejects same-mangle duplicates with a class-init error.
	 */
	public void addFunction(String Name, astNode Value) throws aussomException {
		if (!(Value instanceof astFunctDef)) {
			throw new aussomException(this, "astClass.addFunction: expected astFunctDef, got " + Value.getClass().getName(), "");
		}
		astFunctDef def = (astFunctDef) Value;
		this.functList.add(def);
		this.declaredNames.add(Name);
		this.classifyAndAdd(Name, def);
	}

	private void classifyAndAdd(String name, astFunctDef def) throws aussomException {
		String sig = def.getSignature();
		if (def.isVariadic()) {
			String head = headSignature(sig);
			for (astFunctDef existing : this.variadicOverloads) {
				if (existing.getName().equals(name)
					&& headSignature(existing.getSignature()).equals(head)) {
					throw duplicateSignatureError(name, head.isEmpty() ? "..." : head + ",...", existing, def);
				}
			}
			this.variadicOverloads.add(def);
		} else if (def.hasWildcard()) {
			for (astFunctDef existing : this.wildcardOverloads) {
				if (existing.getName().equals(name) && existing.getSignature().equals(sig)) {
					throw duplicateSignatureError(name, sig, existing, def);
				}
			}
			this.wildcardOverloads.add(def);
		} else {
			// Register the def at every valid (arity, tag-variant)
			// combination in the flat dispatchMap with "name#sig"
			// keys. Each slot can accept multiple tags: ref-shape
			// slots accept 'n' (null) in addition to their type,
			// and slots with a null default accept 'n' regardless
			// of declared type. Pre-registering every variant
			// keeps the dispatch hot path to a single HashMap.get.
			List<astNode> declared = def.getArgList().getArgs();
			int min = def.getMinArity();
			int max = def.getMaxArity();
			char[][] tagsPerPos = new char[max][];
			for (int i = 0; i < max; i++) {
				tagsPerPos[i] = computeSlotTags(declared.get(i));
			}
			for (int arity = min; arity <= max; arity++) {
				registerSigVariants(name, def, tagsPerPos, arity);
			}
		}
	}

	/**
	 * Per-slot allowed tag set used at registration time. A typed
	 * ref-shape slot accepts the type tag plus 'n' (null). A slot
	 * whose default value is null accepts 'n' in addition to its
	 * declared type tag (or just 'n' if no type was declared).
	 */
	private static char[] computeSlotTags(astNode arg) {
		astNodeType nt = arg.getType();
		cType pt = arg.getPrimType();
		if (nt == astNodeType.VAR) {
			if (pt != null && pt != cType.cUndef) {
				char tag = astFunctDef.mangleChar(pt);
				if (isRefShape(pt)) {
					return new char[]{tag, 'n'};
				}
				return new char[]{tag};
			}
			return new char[]{'*'};
		}
		if (nt == astNodeType.NULL) {
			if (pt != null && pt != cType.cUndef) {
				char tag = astFunctDef.mangleChar(pt);
				return new char[]{tag, 'n'};
			}
			return new char[]{'n'};
		}
		// Literal default — runtime enforces the literal type.
		cType eff = astFunctDef.effectiveArgType(arg);
		char tag = astFunctDef.mangleChar(eff);
		if (isRefShape(eff)) {
			return new char[]{tag, 'n'};
		}
		return new char[]{tag};
	}

	private static boolean isRefShape(cType t) {
		if (t == null) return false;
		switch (t) {
			case cString:
			case cList:
			case cMap:
			case cObject:
			case cCallback:
				return true;
			default:
				return false;
		}
	}

	/**
	 * Registers the def in dispatchMap at every "name#sig"
	 * variant for the given arity, computed as the cartesian
	 * product of per-slot tag sets for the first `arity`
	 * positions.
	 */
	private void registerSigVariants(String name, astFunctDef def, char[][] tagsPerPos, int arity) throws aussomException {
		int variantCount = 1;
		for (int i = 0; i < arity; i++) variantCount *= tagsPerPos[i].length;
		for (int v = 0; v < variantCount; v++) {
			StringBuilder sb = new StringBuilder(name.length() + 1 + arity * 2);
			sb.append(name).append('#');
			int rem = v;
			for (int i = 0; i < arity; i++) {
				int idx = rem % tagsPerPos[i].length;
				rem /= tagsPerPos[i].length;
				if (i > 0) sb.append(',');
				sb.append(tagsPerPos[i][idx]);
			}
			String key = sb.toString();
			astFunctDef prior = this.dispatchMap.get(key);
			if (prior != null && prior != def) {
				String displaySig = key.substring(name.length() + 1);
				throw duplicateSignatureError(name, displaySig, prior, def);
			}
			this.dispatchMap.put(key, def);
		}
	}

	private aussomException duplicateSignatureError(String name, String sig, astFunctDef prior, astFunctDef next) {
		String priorLoc = prior.getFileName() + ":" + prior.getLineNum();
		String nextLoc = next.getFileName() + ":" + next.getLineNum();
		String displaySig = sig.isEmpty() ? "()" : "(" + sig + ")";
		return new aussomException(this, "Duplicate method overload '" + name + displaySig
			+ "' declared at " + nextLoc + "; first declared at " + priorLoc + ".", "");
	}

	private static String headSignature(String fullSig) {
		// fullSig ends with "..." or ",..." for variadic. Strip both.
		if (fullSig.endsWith(",...")) return fullSig.substring(0, fullSig.length() - 4);
		if (fullSig.endsWith("...")) return fullSig.substring(0, fullSig.length() - 3);
		return fullSig;
	}

	/**
	 * Exact existence check for a specific overload signature.
	 * @param Name Method name.
	 * @param Signature Mangled positional signature; "" for zero-arg.
	 */
	public boolean containsFunction(String Name, String Signature) {
		if (Signature == null) Signature = "";
		return this.dispatchMap.containsKey(Name + "#" + Signature);
	}

	/**
	 * Exact lookup for a specific overload by name and mangled
	 * signature. Returns null when no exact-match overload exists
	 * with that signature.
	 */
	public astFunctDef getFunct(String Name, String Signature) {
		if (Signature == null) Signature = "";
		return this.dispatchMap.get(Name + "#" + Signature);
	}

	/**
	 * Returns every declared overload of Name as a flat list in
	 * declaration order. Empty list when no overload exists.
	 * Public enumeration API for embedders that need to walk
	 * every overload of a name.
	 */
	public List<astFunctDef> getFunctionsByName(String Name) {
		List<astFunctDef> out = new ArrayList<>();
		for (astFunctDef d : this.functList) {
			if (d.getName().equals(Name)) out.add(d);
		}
		return out;
	}

	/**
	 * True if any overload exists for Name. Used internally for
	 * "is this name declared at all" checks (e.g. constructor
	 * existence).
	 */
	public boolean hasAnyFunctionByName(String Name) {
		return this.declaredNames.contains(Name);
	}

	/**
	 * Returns all declared method definitions on this class in
	 * declaration order, including each overload separately.
	 */
	public List<astFunctDef> getAllFunctions() {
		return this.functList;
	}

	public boolean containsTests() {
		for (astFunctDef funct : this.functList) {
			for (astAnnotation ann : funct.getAnnotations()) {
				if (ann.getAnnotationName().equals("Test")) {
					return true;
				}
			}
		}
		return false;
	}

	public UnitTestClass getTestClass() {
		astAnnotation classAnnotation = this.getAnnotation("Test");
		String classUnitTestName = "";
		if (classAnnotation != null) {
			List<astAnnotationArg> classArgs = classAnnotation.getAnnotationArgsByName("name");
			if (classArgs.size() > 0) {
				classUnitTestName = classArgs.get(0).getValue();
			}
		}
		UnitTestClass testClass = new UnitTestClass(this.getName(), classUnitTestName);

		for (astFunctDef funct : this.functList) {
			for (astAnnotation ann : funct.getAnnotations()) {
				if (ann.getAnnotationName().equals("Test")) {
					String functUnitTestName = "";
					List<astAnnotationArg> functArgs = ann.getAnnotationArgsByName("name");
					if (functArgs.size() > 0) {
						functUnitTestName = functArgs.get(0).getValue();
					}
					UnitTest test = new UnitTest(functUnitTestName, funct.getName());
					String skipVal = ann.getAnnotationArgValueByName("skip");
					if (skipVal != null && skipVal.equals("true")) {
						test.setSkip(true);
					}
					String tagsVal = ann.getAnnotationArgValueByName("tags");
					if (tagsVal != null && !tagsVal.equals("")) {
						for (String t : tagsVal.split(",")) {
							test.addTag(t.trim());
						}
					}
					String timeoutVal = ann.getAnnotationArgValueByName("timeoutMs");
					if (timeoutVal != null && !timeoutVal.trim().equals("")) {
						try {
							test.setTimeoutMs(Long.parseLong(timeoutVal.trim()));
						} catch (NumberFormatException nfe) {
							// Leave timeoutMs at 0 (no timeout) for unparseable values.
						}
					}
					testClass.addTest(test);
				} else if (ann.getAnnotationName().equals("Before")) {
					testClass.setBeforeFunctionName(funct.getName());
				} else if (ann.getAnnotationName().equals("After")) {
					testClass.setAfterFunctionName(funct.getName());
				} else if (ann.getAnnotationName().equals("BeforeEach")) {
					testClass.setBeforeEachFunctionName(funct.getName());
				} else if (ann.getAnnotationName().equals("AfterEach")) {
					testClass.setAfterEachFunctionName(funct.getName());
				} else if (ann.getAnnotationName().equals("OnTestFail")) {
					testClass.setOnTestFailFunctionName(funct.getName());
				}
			}
		}

		return testClass;
	}

	public void init(Environment env) throws aussomException {
		if (!this.initRan) {
			this.initRan = true;

			boolean foundExtern = false;
			if (this.isExtern) { foundExtern = true; }

			for(String className : this.extendedClasses) {
				astClass ac = env.getClassByName(className);

				if(ac != null) {
					if(ac.getExtern()) {
						if (ac.getName().equals("object")) {
							// Set object extern stuff to begin with if not set already.
							if (this.externClass == null) {
								this.isExtern = true;
								this.externClassName = ac.getExternClassName();
								this.externClass = ac.getExternClass();
							}
						} else {
							if(foundExtern) {
								throw new aussomException(this, "Cannot inherit from two external classes. First is '" + this.externClassName + "' and second is '" + className + "'.", env.stackTraceToString());
							}

							foundExtern = true;
							this.isExtern = true;
							this.externClassName = ac.getExternClassName();
							this.externClass = ac.getExternClass();
						}
					}
				} else {
					throw new aussomException(this, "Extended class '" + className + "' not found.", env.stackTraceToString());
				}
			}
		}
	}

	/*
	 * Run functions
	 */
	public AussomType instantiate(Environment env) throws aussomException {
		return this.instantiate(env, false, new AussomList());
	}

	public AussomType instantiate(Environment env, boolean getRef, AussomList args) throws aussomException {
		// First run init if it hasn't been ran for the class.
		this.init(env);

		AussomObject ci;
		if (this.isExtern) {
			ci = (AussomObject) instantiateExtern(env);
		} else {
			ci = new AussomObject();
		}
		ci.setClassDef(this);

		// Instantiated inherited classes.
		this.instantiateInheritedClasses(env, ci, this.extendedClasses);

		// Instantiate members.
		this.instantiateMembers(env, ci);

		/*
		 * Call constructor: route through the dispatcher if any
		 * overload exists at the class name. The dispatcher picks
		 * the matching constructor overload by signature.
		 *
		 * Build the constructor call stack on top of the caller's
		 * stack so error traces (including dispatcher errors like
		 * FUNCT_NOT_FOUND raised before the body runs) point at
		 * the `new` site rather than only at the class def.
		 */
		if (this.hasAnyFunctionByName(this.getName())) {
			CallStack constStack;
			synchronized (env.getCallStack()) {
				constStack = new CallStack(this.getFileName(), this.getLineNum(), this.getName(), this.getName(), "Constructor called.");
				constStack.setParent(env.getCallStack());
			}
			Environment tenv = new Environment(env.getEngine());
			tenv.setEnvironment(ci, env.getLocals(), constStack);
			AussomType ret = this.call(tenv, getRef, getName(), args);
			if (ret.isEx()) {
				return ret;
			}
		}

		return ci;
	}

	public void instantiateMembers(Environment env, AussomObject ci) throws aussomException {
		for (int i = 0; i < this.membList.size(); i++) {
			astNode cur = this.membDefs.get(this.membList.get(i));
			switch(cur.getType()) {
			case VAR:
				ci.addMember(cur.getName(), cur.eval(env));
				break;
			case BOOL:
				ci.addMember(cur.getName(), cur.eval(env));
				break;
			case INT:
				ci.addMember(cur.getName(), cur.eval(env));
				break;
			case DOUBLE:
				ci.addMember(cur.getName(), cur.eval(env));
				break;
			case STRING:
				ci.addMember(cur.getName(), cur.eval(env));
				break;
			case OBJ:
				ci.addMember(cur.getName(), cur.eval(env));
				break;
			case LIST:
				ci.addMember(cur.getName(), cur.eval(env));
				break;
			case MAP:
				ci.addMember(cur.getName(), cur.eval(env));
				break;
			case NULL:
				ci.addMember(cur.getName(), cur.eval(env));
				break;
			default:
				throw new aussomException(this, "astClass.instantiateMembers(): Unexpected node type '" + cur.getType().name() + "' found.", env.stackTraceToString());
			}
		}
	}

	/**
	 * Dispatches a call to one of this class's overloaded methods.
	 * Resolution order: exact signature match -> numeric promotion
	 * (int->double) -> wildcard candidates -> variadic. Falls
	 * through to a stored callback member if no overload matches.
	 */
	public AussomType call (Environment env, boolean getRef, String functName, AussomList args) throws aussomException {
		AussomType ret = null;
		AussomObject cobj = null;
		if (env.getCurObj() != null && env.getCurObj() instanceof AussomObject) {
			cobj = (AussomObject) env.getCurObj();
		}

		// Mock framework keys on bare name; an overload group is
		// mocked or spied as a unit. The isSpySet / isMockSet /
		// hasFunctionMock predicates short-circuit when the object
		// has no mock allocated, so primitives (the common case)
		// don't pay for a Mock allocation just to check.
		boolean spySet = (cobj != null) && cobj.isSpySet(functName);
		if (spySet && !(Boolean)env.getEngine().getSecurityManager().getProperty("test.mock.spy")) {
			return new AussomException("astClass.call(): Security exception, action 'test.mock.spy' not permitted.");
		}

		if (cobj != null && cobj.isMockSet() && cobj.hasFunctionMock(functName)) {
			if (!(Boolean)env.getEngine().getSecurityManager().getProperty("test.mock.inject")) {
				return new AussomException("astClass.call(): Security exception, action 'test.mock.inject' not permitted.");
			}
			ret = this.processMock(env, cobj, functName, args);
		}

		if (ret == null) {
			astFunctDef fdef = null;
			AussomList useArgs = args;

			// Hot path: build "name#sig" composite key, single
			// HashMap.get on the flat dispatch map. Slow paths
			// (numeric promotion, wildcards, variadic) only run
			// when this misses.
			String key = makeDispatchKey(functName, args);
			fdef = this.dispatchMap.get(key);
			if (fdef == null && this.declaredNames.contains(functName)) {
				ResolveResult rr = resolveSlowPaths(functName, args, key, env);
				if (rr.error != null) return rr.error;
				fdef = rr.def;
				useArgs = rr.args;
			}

			if (fdef == null) {
				// Fallback: stored callback member with this name.
				if (cobj != null && cobj.containsMember(functName)) {
					AussomType member = cobj.getMember(functName);
					if (member instanceof AussomCallback) {
						return ((AussomCallback) member).call(env, args);
					}
				}
				AussomException ce = new AussomException(exType.exRuntime);
				ce.setException(this.getLineNum(), "FUNCT_NOT_FOUND", "Object '" + this.getName() + "' has no overload of '" + functName + "' matching call signature '" + mangleCallSig(args) + "'.", env.getCallStack().getStackTrace());
				return ce;
			}

			// Access check on the resolved overload (was previously
			// in astFunctCall.functionHasAccess).
			AussomObject ci = env.getClassInstance();
			if (cobj != null && ci != cobj && fdef.getAccessType() == AccessType.aPrivate) {
				AussomException e = new AussomException(exType.exRuntime);
				e.setException(this.getLineNum(), "NO_ACCESS", "No access to function '" + functName + "' (private).", env.getCallStack().getStackTrace());
				return e;
			}

			// Set up call environment.
			Members locals = new Members();
			Environment tenv = new Environment(env.getEngine());
			if (cobj != null) ci = cobj;
			tenv.setEnvironment(ci, locals, env.getCallStack());

			if (fdef.getExtern()) {
				AussomType tmp = fdef.initArgs(tenv, useArgs);
				if (!tmp.isEx()) {
					ret = fdef.callExtern(tenv, useArgs);
				} else {
					ret = tmp;
				}
			} else {
				ret = fdef.call(tenv, getRef, useArgs, this.getFileName());
			}
		}

		if (spySet) {
			MockFunctionSpyRecord spyRecord = new MockFunctionSpyRecord(args, (AussomObject) ret);
			cobj.getMock().addSpyRecord(functName, spyRecord);
		}

		if (ret == null) ret = new AussomNull();
		return ret;
	}

	/**
	 * Holder for overload-resolution. def is the chosen overload
	 * (null when no match). args is the (possibly numeric-promoted)
	 * arg list to pass to the body. error is non-null when
	 * resolution itself failed (AMBIGUOUS_OVERLOAD).
	 */
	private static final class ResolveResult {
		astFunctDef def;
		AussomList args;
		AussomException error;
	}

	/**
	 * Builds the composite dispatch key "name#tagsig" used by the
	 * flat dispatchMap. Allocation: one StringBuilder + one
	 * String per call.
	 */
	private static String makeDispatchKey(String name, AussomList args) {
		int n = (args == null) ? 0 : args.size();
		StringBuilder sb = new StringBuilder(name.length() + 1 + n * 2);
		sb.append(name).append('#');
		if (n > 0) {
			List<AussomType> vals = args.getValue();
			sb.append(astFunctDef.mangleChar(vals.get(0).getType()));
			for (int i = 1; i < n; i++) {
				sb.append(',').append(astFunctDef.mangleChar(vals.get(i).getType()));
			}
		}
		return sb.toString();
	}

	/**
	 * Slow-path resolution. The caller has already missed in the
	 * exact dispatchMap; this routine walks numeric-promotion,
	 * wildcard-candidate, and variadic matchers. Allocating a
	 * ResolveResult here is amortized by being only on the cold
	 * path.
	 */
	private ResolveResult resolveSlowPaths(String functName, AussomList args, String missedKey, Environment env) throws aussomException {
		String callSig = mangleCallSig(args);

		ResolveResult promoted = matchPromoted(functName, args, callSig, env);
		if (promoted.error != null) return promoted;
		if (promoted.def != null) return promoted;

		ResolveResult wildcard = matchWildcards(functName, args, env);
		if (wildcard.error != null) return wildcard;
		if (wildcard.def != null) return wildcard;

		ResolveResult variadic = matchVariadic(functName, args, env);
		if (variadic.error != null) return variadic;
		if (variadic.def != null) return variadic;

		ResolveResult rr = new ResolveResult();
		rr.args = args;
		return rr;
	}

	/**
	 * Mangles a call's runtime arg types into a comma-joined
	 * single-character signature ("i,d,s" for an int+double+string
	 * call).
	 */
	private static String mangleCallSig(AussomList args) {
		if (args == null || args.size() == 0) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < args.size(); i++) {
			if (i > 0) sb.append(',');
			sb.append(astFunctDef.mangleChar(args.getValue().get(i).getType()));
		}
		return sb.toString();
	}

	/**
	 * Tries every int->double substitution variant of callSig in
	 * ascending promotion-count order. Picks the unique
	 * lowest-count match. Multiple matches at the same count are
	 * AMBIGUOUS_OVERLOAD.
	 */
	private ResolveResult matchPromoted(String functName, AussomList args, String callSig, Environment env) {
		ResolveResult rr = new ResolveResult();
		rr.args = args;
		if (callSig.isEmpty()) return rr;

		String[] callTags = callSig.split(",");
		int n = callTags.length;
		int[] intPositions = new int[n];
		int intCount = 0;
		for (int i = 0; i < n; i++) {
			if (callTags[i].equals("i")) intPositions[intCount++] = i;
		}
		if (intCount == 0) return rr;

		int totalSubsets = 1 << intCount;
		Integer[] subsets = new Integer[totalSubsets - 1];
		for (int i = 1; i < totalSubsets; i++) subsets[i - 1] = i;
		Arrays.sort(subsets, Comparator.comparingInt(Integer::bitCount));

		int bestCount = -1;
		List<astFunctDef> bestMatches = new ArrayList<>();
		Integer bestSubset = null;
		for (Integer subsetI : subsets) {
			int subset = subsetI;
			int popcount = Integer.bitCount(subset);
			if (bestCount != -1 && popcount > bestCount) break;
			String[] promoted = callTags.clone();
			for (int j = 0; j < intCount; j++) {
				if ((subset & (1 << j)) != 0) {
					promoted[intPositions[j]] = "d";
				}
			}
			String promotedKey = functName + "#" + String.join(",", promoted);
			astFunctDef hit = this.dispatchMap.get(promotedKey);
			if (hit != null) {
				if (bestCount == -1) {
					bestCount = popcount;
					bestMatches.add(hit);
					bestSubset = subset;
				} else if (popcount == bestCount) {
					bestMatches.add(hit);
				}
			}
		}

		if (bestMatches.size() == 1) {
			rr.def = bestMatches.get(0);
			rr.args = buildPromotedArgs(args, intPositions, intCount, bestSubset);
			return rr;
		} else if (bestMatches.size() > 1) {
			rr.error = ambiguityError("AMBIGUOUS_OVERLOAD", bestMatches, env);
		}
		return rr;
	}

	private static AussomList buildPromotedArgs(AussomList args, int[] intPositions, int intCount, int subset) {
		AussomList out = new AussomList();
		List<AussomType> orig = args.getValue();
		for (int i = 0; i < orig.size(); i++) out.getValue().add(orig.get(i));
		for (int j = 0; j < intCount; j++) {
			if ((subset & (1 << j)) != 0) {
				int pos = intPositions[j];
				AussomType v = out.getValue().get(pos);
				if (v.getType() == cType.cInt) {
					out.getValue().set(pos, new AussomDouble((double) ((AussomInt) v).getValue()));
				}
			}
		}
		return out;
	}

	private ResolveResult matchWildcards(String functName, AussomList args, Environment env) {
		ResolveResult rr = new ResolveResult();
		rr.args = args;
		if (this.wildcardOverloads.isEmpty()) return rr;

		// Walk the per-class wildcard list filtered by name.
		// Pick the most-specific match; AMBIGUOUS_OVERLOAD on ties.
		int bestSpec = -1;
		List<astFunctDef> bestMatches = null;
		for (astFunctDef cand : this.wildcardOverloads) {
			if (!cand.getName().equals(functName)) continue;
			if (!overloadMatchesCall(cand, args)) continue;
			int spec = specificity(cand);
			if (spec > bestSpec) {
				bestSpec = spec;
				bestMatches = new ArrayList<>();
				bestMatches.add(cand);
			} else if (spec == bestSpec) {
				bestMatches.add(cand);
			}
		}

		if (bestMatches == null) return rr;
		if (bestMatches.size() == 1) {
			rr.def = bestMatches.get(0);
			return rr;
		}
		rr.error = ambiguityError("AMBIGUOUS_OVERLOAD", bestMatches, env);
		return rr;
	}

	private static boolean overloadMatchesCall(astFunctDef def, AussomList args) {
		// Allow defaults: call may be shorter than declared as
		// long as it covers all required positions.
		if (args.size() < def.getMinArity()) return false;
		if (args.size() > def.getMaxArity()) return false;
		List<astNode> declared = def.getArgList().getArgs();
		for (int i = 0; i < args.size(); i++) {
			cType decl = astFunctDef.effectiveArgType(declared.get(i));
			cType actual = args.getValue().get(i).getType();
			if (!tagMatches(decl, actual)) return false;
		}
		return true;
	}

	/**
	 * Per-position tag-match rule used by wildcard and variadic
	 * matchers. cUndef accepts anything. Numeric widening allows
	 * int -> double. Null matches any reference-shape declared
	 * tag.
	 */
	private static boolean tagMatches(cType decl, cType actual) {
		if (decl == cType.cUndef) return true;
		if (decl == actual) return true;
		if (decl == cType.cDouble && actual == cType.cInt) return true;
		if (actual == cType.cNull) {
			switch (decl) {
				case cString:
				case cList:
				case cMap:
				case cObject:
				case cCallback:
					return true;
				default:
					break;
			}
		}
		return false;
	}

	private ResolveResult matchVariadic(String functName, AussomList args, Environment env) {
		ResolveResult rr = new ResolveResult();
		rr.args = args;
		if (this.variadicOverloads.isEmpty()) return rr;

		List<astFunctDef> matches = null;
		for (astFunctDef def : this.variadicOverloads) {
			if (!def.getName().equals(functName)) continue;
			if (!variadicMatches(def, args)) continue;
			if (matches == null) matches = new ArrayList<>();
			matches.add(def);
		}

		if (matches == null) return rr;
		if (matches.size() == 1) {
			rr.def = matches.get(0);
			return rr;
		}
		// Pick the most-specific variadic head (longest head sig
		// with fewest wildcards). If still tied, raise ambiguity.
		matches.sort(Comparator.comparingInt((astFunctDef d) -> -specificity(d)));
		int topSpec = specificity(matches.get(0));
		int topHeadLen = matches.get(0).getArgList().getArgs().size();
		List<astFunctDef> top = new ArrayList<>();
		for (astFunctDef d : matches) {
			if (specificity(d) == topSpec
				&& d.getArgList().getArgs().size() == topHeadLen) {
				top.add(d);
			}
		}
		if (top.size() == 1) {
			rr.def = top.get(0);
			return rr;
		}
		rr.error = ambiguityError("AMBIGUOUS_OVERLOAD", top, env);
		return rr;
	}

	private static boolean variadicMatches(astFunctDef def, AussomList args) {
		List<astNode> declared = def.getArgList().getArgs();
		int headLen = declared.size() - 1;
		if (args.size() < headLen) return false;
		for (int i = 0; i < headLen; i++) {
			cType decl = astFunctDef.effectiveArgType(declared.get(i));
			cType actual = args.getValue().get(i).getType();
			if (!tagMatches(decl, actual)) return false;
		}
		astNode etcArg = declared.get(declared.size() - 1);
		cType etcType = (etcArg instanceof astEtcetera) ? ((astEtcetera) etcArg).getPrimType() : cType.cUndef;
		if (etcType != cType.cUndef) {
			for (int i = headLen; i < args.size(); i++) {
				cType actual = args.getValue().get(i).getType();
				if (!tagMatches(etcType, actual)) return false;
			}
		}
		return true;
	}

	private AussomException ambiguityError(String code, List<astFunctDef> candidates, Environment env) {
		StringBuilder sb = new StringBuilder("Call to '" + this.getName() + "' is ambiguous between: ");
		for (int i = 0; i < candidates.size(); i++) {
			if (i > 0) sb.append(", ");
			astFunctDef c = candidates.get(i);
			sb.append(c.getName()).append("(").append(c.getSignature()).append(")");
		}
		AussomException e = new AussomException(exType.exRuntime);
		e.setException(this.getLineNum(), code, sb.toString(), env.getCallStack().getStackTrace());
		return e;
	}

	private AussomType processMock(Environment env, AussomObject cobj, String functName, AussomList args) throws aussomException {
		AussomType ret = null;

		MockFunction simpleMock = cobj.getMock().getSimpleMock(functName);
		if (simpleMock != null) {
			ret = simpleMock.getReturnValue();
		} else {
			boolean found = false;

			List<MockFunction> mocks = cobj.getMock().getMockFunctions(functName);
			for (MockFunction mock : mocks) {
				AussomList fargs = new AussomList();
				fargs.getValue().add(cobj);
				fargs.getValue().add(args);
				AussomType at = mock.getCondition().call(env, fargs);
				if (at.isEx()) {
					ret = at;
					break;
				} else {
					if (at.getNumericBool() == true) {
						found = true;
						ret = mock.getReturnValue();
						break;
					}
				}
			}
		}

		return ret;
	}

	private void instantiateInheritedClasses(Environment env, AussomObject cobj, ArrayList<String> extClasses) throws aussomException {
		for(String className : extClasses) {
			astClass ac = env.getClassByName(className);

			if (!this.allExtendedClasses.contains(ac.getName()))
				this.allExtendedClasses.add(ac.getName());

			if (ac.getExtendedClasses().size() > 0)
				this.instantiateInheritedClasses(env, cobj, ac.getExtendedClasses());

			if(ac != null) {
				if(ac.getExtern()) {
					if (!ac.getName().equals("object")) {
						AussomObject ao = (AussomObject) ac.instantiate(env);
						if(ao != null) {
							cobj.setExternObject(ao.getExternObject());
						} else {
							throw new aussomException(this, "Failed to instantiate class '" + className + "', object is null.", env.stackTraceToString());
						}
					}
				}

				ac.instantiateMembers(env, cobj);

				// Per-overload merge across the flat dispatchMap and
				// the wildcard / variadic lists. Child entries
				// shadow parent entries at the same key (or same
				// name + signature for wildcards / variadic).
				for (Map.Entry<String, astFunctDef> ee : ac.dispatchMap.entrySet()) {
					if (!this.dispatchMap.containsKey(ee.getKey())) {
						this.dispatchMap.put(ee.getKey(), ee.getValue());
						this.inheritedFuncts.add(ee.getKey());
						if (!this.functList.contains(ee.getValue())) {
							this.functList.add(ee.getValue());
						}
						this.declaredNames.add(ee.getValue().getName());
					}
				}
				for (astFunctDef pdef : ac.wildcardOverloads) {
					boolean shadowed = false;
					for (astFunctDef cdef : this.wildcardOverloads) {
						if (cdef.getName().equals(pdef.getName())
							&& cdef.getSignature().equals(pdef.getSignature())) {
							shadowed = true;
							break;
						}
					}
					if (!shadowed) {
						this.wildcardOverloads.add(pdef);
						this.inheritedFuncts.add(pdef.getName() + "(" + pdef.getSignature() + ")");
						if (!this.functList.contains(pdef)) {
							this.functList.add(pdef);
						}
						this.declaredNames.add(pdef.getName());
					}
				}
				for (astFunctDef pdef : ac.variadicOverloads) {
					String pHead = headSignature(pdef.getSignature());
					boolean shadowed = false;
					for (astFunctDef cdef : this.variadicOverloads) {
						if (cdef.getName().equals(pdef.getName())
							&& headSignature(cdef.getSignature()).equals(pHead)) {
							shadowed = true;
							break;
						}
					}
					if (!shadowed) {
						this.variadicOverloads.add(pdef);
						this.inheritedFuncts.add(pdef.getName() + "(" + pHead + ",...)");
						if (!this.functList.contains(pdef)) {
							this.functList.add(pdef);
						}
						this.declaredNames.add(pdef.getName());
					}
				}

			} else {
				throw new aussomException(this, "Extended class '" + className + "' not found.", env.stackTraceToString());
			}
		}
	}

	private AussomType instantiateExtern(Environment env) throws aussomException {
		boolean primType = true;
		AussomObject obj;

		// Instantiate native type, or generic object.
		if (this.getName().equals("bool")) {
			obj = new AussomBool();
		} else if (this.getName().equals("int")) {
			obj = new AussomInt();
		} else if (this.getName().equals("double")) {
			obj = new AussomDouble();
		} else if (this.getName().equals("string")) {
			obj = new AussomString();
		} else if (this.getName().equals("list")) {
			obj = new AussomList();
		} else if (this.getName().equals("map")) {
			obj = new AussomMap();
		} else {
			primType = false;
			obj = new AussomObject();
		}

		obj.setClassDef(this);
		if (primType || this.externClass.getName().equals("com.aussom.types.AussomObject")) {
			obj.setExternObject(obj);
		} else {
			try {
		        obj.setExternObject(this.externClass.newInstance());
		    } catch (SecurityException e) {
				throw new aussomException(this, "Instantiate extern security exception: " + e.getMessage(), env.stackTraceToString());
			} catch (InstantiationException e) {
				throw new aussomException(this, "Instantiate extern instantiation exception: " + e.getMessage(), env.stackTraceToString());
			} catch (IllegalAccessException e) {
				throw new aussomException(this, "Instantiate extern illegal access exception: " + e.getMessage(), env.stackTraceToString());
			} catch (IllegalArgumentException e) {
				throw new aussomException(this, "Instantiate extern illegal argument exception: " + e.getMessage(), env.stackTraceToString());
			}
		}

		return obj;
	}

	private void loadExternClass() throws aussomException {
		ClassLoader cl = ClassLoader.getSystemClassLoader();
	    try {
	        this.externClass = cl.loadClass(this.externClassName);
	    } catch (ClassNotFoundException e) {
	    	throw new aussomException("Extern class '" + this.externClassName + "' not found.");
	    } catch (SecurityException e) {
			throw new aussomException("Extern class security exception: " + e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new aussomException("Extern class illegal argument excetpion: " + e.getMessage());
		}
	}


	@Override
	public String toString() {
		return this.toString(0);
	}

	public String toString(int Level) {
		String rstr = "";
		rstr += getTabs(Level) + "{\n";
		rstr += this.getNodeStr(Level + 1) + ",\n";
		rstr += getTabs(Level + 1) + "\"fileName\": \"" + this.getFileName() + "\",\n";
		rstr += getTabs(Level + 1) + "\"modRef\": \"" + this.externClassName + "\",\n";
		rstr += getTabs(Level + 1) + "\"static\": \"" + this.isStatic + "\",\n";
		rstr += getTabs(Level + 1) + "\"extern\": \"" + this.isExtern + "\",\n";

		rstr += getTabs(Level + 1) + "\"definitions\": [\n";
		for(int i = 0; i < this.membList.size(); i++)
			rstr += ((astNodeInt)this.membDefs.get(this.membList.get(i))).toString(Level + 2) + ",\n";
		rstr += getTabs(Level + 1) + "],\n";

		rstr += getTabs(Level + 1) + "\"functionDefinitions\": [\n";
		for (astFunctDef fun : this.functList) {
			rstr += ((astNodeInt) fun).toString(Level + 2) + ",\n";
		}
		rstr += getTabs(Level + 1) + "],\n";

		rstr += getTabs(Level) + "}";
		return rstr;
	}

	public boolean hasMain() {
		return this.hasAnyFunctionByName("main");
	}

	public void setInheritedMembers(ArrayList<String> InheritedMembers) { this.inheritedMembers = InheritedMembers; }
	public List<String> getInheritedMembers() { return this.inheritedMembers; }

	public void setInheritedFuncts(ArrayList<String> InheritedFuncts) { this.inheritedFuncts = InheritedFuncts; }
	public List<String> getInheritedFuncts() { return this.inheritedFuncts; }

	public boolean getStatic() {
		return isStatic;
	}

	public void setStatic(boolean isStatic) {
		this.isStatic = isStatic;
	}

	public boolean getExtern() {
		return isExtern;
	}

	public void setExtern(boolean isExtern) {
		this.isExtern = isExtern;
	}

	public String getExternClassName() {
		return externClassName;
	}

	public void setExternClassName(String externClass, boolean LoadExtern) throws aussomException {
		this.externClassName = externClass;
		if (LoadExtern)
			this.loadExternClass();
	}

	@SuppressWarnings("rawtypes")
	public void setExternClass(Class C) { this.externClass = C; }

	@SuppressWarnings("rawtypes")
	public Class getExternClass() { return this.externClass; }

	@Override
	public AussomType evalImpl(Environment env, boolean getref) {
		return new AussomNull();
	}

	public ArrayList<String> getExtendedClasses() {
		return extendedClasses;
	}

	public void setExtendedClasses(ArrayList<String> extendedClasses) {
		this.extendedClasses = extendedClasses;
		for (String cls : this.extendedClasses) {
			if (!this.allExtendedClasses.contains(cls))
				this.allExtendedClasses.add(cls);
		}
	}

	public boolean instanceOf(String Name) {
		if (this.getName().equals(Name)) {
			return true;
		} else if (this.allExtendedClasses.contains(Name)) {
			return true;
		} else if (this.getName().equals("cnull") && Name.equals("null")) {
			return true;
		}
		return false;
	}

	@Override
	public void setName(String Name) {
		// Don't add object as extend class to object.
		if (!Name.equals("object") && !this.extendedClasses.contains("object")) {
			this.extendedClasses.add("object");
		}
		// Actually set the name.
		super.setName(Name);
	}

	public AussomType getAussomdoc() {
		// Class object
		AussomMap ret = new AussomMap();
		ret.put("type", new AussomString("class"));
		ret.put("className", new AussomString(this.getName()));
		ret.put("fileName", new AussomString(this.getFileName()));
		ret.put("lineNumber", new AussomInt(this.getLineNum()));
		ret.put("colNumber", new AussomInt(this.getColNum()));
		ret.put("isStatic", new AussomBool(this.isStatic));
		ret.put("isExtern", new AussomBool(this.isExtern));
		ret.put("externClassName", new AussomString(this.externClassName));
		AussomList extClasses = new AussomList();
		for (String str : this.extendedClasses) {
			extClasses.add(new AussomString(str));
		}
		ret.put("extendedClasses", extClasses);

		if (this.docNode != null) {
			ret.put("aussomDoc", this.docNode.getAussomdoc());
		}

		// Members
		AussomList mlist = new AussomList();
		for (String memberName : this.membList) {
			astNode mbr = this.membDefs.get(memberName);
			AussomMap mm = new AussomMap();
			mm.put("name", new AussomString(memberName));
			if (mbr.docNode == null) {
				mm.put("value", new AussomNull());
			} else {
				mm.put("value", mbr.docNode.getAussomdoc());
			}
			mlist.add(mm);
		}
		ret.put("members", mlist);

		// Functions: emit one entry per declared overload.
		AussomList flist = new AussomList();
		for (astFunctDef fun : this.functList) {
			flist.add(fun.getAussomdoc());
		}
		ret.put("methods", flist);

		return ret;
	}
}

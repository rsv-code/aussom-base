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

package com.aussom.ast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.aussom.CallStack;
import com.aussom.Environment;
import com.aussom.types.*;
import com.aussom.types.AussomException.exType;

public class astFunctDef extends astNode implements astNodeInt {
	private astFunctDefArgsList argList = new astFunctDefArgsList();
	private astStatementList instructionList = new astStatementList();
	private boolean isExtern = false;
	
	public astFunctDef() {
		this.setType(astNodeType.FUNCTDEF);
	}
	
	public astFunctDef(String Name) {
		this.setType(astNodeType.FUNCTDEF);
		this.setName(Name);
	}

	public astFunctDefArgsList getArgList() {
		return argList;
	}

	public void setArgList(astFunctDefArgsList argList) {
		this.argList = argList;
	}

	public astStatementList getInstructionList() {
		return instructionList;
	}

	public void setInstructionList(astStatementList InstructionList) {
		this.instructionList = InstructionList;
	}

	public void setExtern(boolean extern) {
		this.isExtern = extern;
	}
	
	public boolean getExtern() {
		return this.isExtern;
	}

	/**
	 * Maps a cType to its single-character mangle tag used for
	 * overload signatures. cUndef becomes '*' (wildcard).
	 */
	public static char mangleChar(cType t) {
		if (t == null) return '*';
		switch (t) {
			case cBool: return 'b';
			case cInt: return 'i';
			case cDouble: return 'd';
			case cString: return 's';
			case cList: return 'l';
			case cMap: return 'm';
			case cObject: return 'o';
			case cCallback: return 'c';
			case cNull: return 'n';
			case cUndef:
			default: return '*';
		}
	}

	/**
	 * Maps an arg-list AST node to the cType it accepts for
	 * dispatch matching. Untyped `var` slots return cUndef
	 * (wildcard). Literal-default args (`x = 5`) report the
	 * literal's type. A slot with a null default but no declared
	 * type (`x = null`) returns cUndef — the runtime does not
	 * enforce a type at that slot, so it behaves as a wildcard.
	 * ETCETERA returns cUndef here; callers check
	 * astNodeType.ETCETERA separately.
	 */
	public static cType effectiveArgType(astNode arg) {
		astNodeType nt = arg.getType();
		if (nt == astNodeType.ETCETERA) return cType.cUndef;
		cType pt = arg.getPrimType();
		if (pt != null && pt != cType.cUndef) return pt;
		switch (nt) {
			case BOOL: return cType.cBool;
			case INT: return cType.cInt;
			case DOUBLE: return cType.cDouble;
			case STRING: return cType.cString;
			case LIST: return cType.cList;
			case MAP: return cType.cMap;
			case NULL: return cType.cUndef; // permissive: null-default + no type = wildcard
			case OBJ: return cType.cObject;
			case VAR:
			default: return cType.cUndef;
		}
	}

	/**
	 * Returns the mangled signature for this definition. Examples:
	 * "" for f(), "i,i" for f(int,int), "s,..." for f(string,...),
	 * "*" for f(x), "i,*" for f(int x, var y).
	 */
	public String getSignature() {
		StringBuilder sb = new StringBuilder();
		List<astNode> as = (this.argList != null) ? this.argList.getArgs() : null;
		if (as == null || as.isEmpty()) return "";
		for (int i = 0; i < as.size(); i++) {
			astNode arg = as.get(i);
			if (i > 0) sb.append(',');
			if (arg.getType() == astNodeType.ETCETERA) {
				sb.append("...");
			} else {
				sb.append(mangleChar(effectiveArgType(arg)));
			}
		}
		return sb.toString();
	}

	/**
	 * True when the last declared arg is ETCETERA.
	 */
	public boolean isVariadic() {
		List<astNode> as = (this.argList != null) ? this.argList.getArgs() : null;
		if (as == null || as.isEmpty()) return false;
		return as.get(as.size() - 1).getType() == astNodeType.ETCETERA;
	}

	/**
	 * True when any non-variadic arg slot is a wildcard — a slot
	 * whose effective type is cUndef. This includes both `var x`
	 * (untyped VAR) and `x = null` (null default, no declared
	 * type), since the runtime treats both as accepting any tag.
	 */
	public boolean hasWildcard() {
		List<astNode> as = (this.argList != null) ? this.argList.getArgs() : null;
		if (as == null) return false;
		for (astNode arg : as) {
			if (arg.getType() == astNodeType.ETCETERA) continue;
			if (effectiveArgType(arg) == cType.cUndef) return true;
		}
		return false;
	}

	/**
	 * Number of leading required args: VAR slots with no default
	 * value, before the first default-bearing arg or ETCETERA.
	 */
	public int getMinArity() {
		List<astNode> as = (this.argList != null) ? this.argList.getArgs() : null;
		if (as == null) return 0;
		int min = 0;
		for (astNode arg : as) {
			if (arg.getType() == astNodeType.ETCETERA) break;
			if (arg.getType() == astNodeType.VAR) {
				min++;
			} else {
				break;
			}
		}
		return min;
	}

	/**
	 * Maximum arity: Integer.MAX_VALUE if variadic, else total
	 * declared args.
	 */
	public int getMaxArity() {
		List<astNode> as = (this.argList != null) ? this.argList.getArgs() : null;
		if (as == null) return 0;
		if (this.isVariadic()) return Integer.MAX_VALUE;
		return as.size();
	}

	@Override
	public AussomType evalImpl(Environment env, boolean getref) throws aussomException {
		throw new aussomException(this, "INTERNAL [astFunctDef.evalImpl] Not implemented.", env.stackTraceToString());
	}
	
	@Override
	public String toString(int Level) {
		String rstr = "";
		rstr += getTabs(Level) + "{\n";
		rstr += this.getNodeStr(Level + 1) + ",\n";
		if(this.argList != null) {
			rstr += getTabs(Level + 1) + "\"argumentList\":\n";
			rstr += this.argList.toString(Level + 1) + ",\n";
		}
		if(this.instructionList != null) {
		  rstr += getTabs(Level + 1) + "\"statementList\":\n";
			rstr += this.instructionList.toString(Level + 1) + ",\n";
		}
		rstr += getTabs(Level) + "}";
		return rstr;
	}
	
	public AussomType initArgs(Environment env, AussomList args) throws aussomException {
		AussomType ret = new AussomNull();

		// Push a synthetic frame so debugger pauses inside default
		// argument expressions show this function as the active
		// context. The frame names the function and is parented to
		// the caller's stack. See design/debugging-callstack-update.md.
		String className = (env.getClassInstance() != null)
			? env.getClassInstance().getClassDef().getName() : "";
		CallStack defaultsFrame = new CallStack(this.getFileName(), this.getLineNum(),
			className, this.getName() + " <arg-defaults>",
			"Default argument values.");
		defaultsFrame.setParent(env.getCallStack());
		Environment tenv = new Environment(env.getEngine());
		tenv.setEnvironment(env.getClassInstance(), env.getLocals(), defaultsFrame);

		if (this.argList != null) {
			int i = 0;
			for (astNode adef : this.argList.getArgs()) {
				// Data type in funct def specified, check that passed data 
				// is valid.
				if (i < args.getValue().size()) {
					cType actualT = args.getValue().get(i).getType();
					if (adef.getType() != astNodeType.UNDEF) {
						boolean typeMismatch =
							(adef.getType() == astNodeType.BOOL && actualT != cType.cBool)
							|| (adef.getType() == astNodeType.INT && actualT != cType.cInt)
							|| (adef.getType() == astNodeType.DOUBLE && actualT != cType.cDouble)
							|| (adef.getType() == astNodeType.STRING && actualT != cType.cString)
							|| (adef.getType() == astNodeType.LIST && actualT != cType.cList)
							|| (adef.getType() == astNodeType.MAP && actualT != cType.cMap)
							|| (adef.getType() == astNodeType.OBJ && actualT != cType.cObject);
						// Allow null at any ref-shape slot, matching the
						// dispatcher's null-matches-refs rule.
						if (typeMismatch && actualT == cType.cNull
							&& (adef.getType() == astNodeType.STRING
								|| adef.getType() == astNodeType.LIST
								|| adef.getType() == astNodeType.MAP
								|| adef.getType() == astNodeType.OBJ)) {
							typeMismatch = false;
						}
						if (typeMismatch) {
								AussomException e = new AussomException(exType.exRuntime);
								e.setException(this.getLineNum(), "INVALID_DATA_TYPE", "Function '" + this.getName() + "' definition at position " + (i + 1) + " is expected to be of type '" + adef.getType().name() + "' but found '" + actualT.name() + "' instead.", env.getCallStack().getStackTrace());
								return e;
						} else if (adef.getType() == astNodeType.VAR && adef.getPrimType() != cType.cUndef && adef.getPrimType() != actualT) {
							// Same null-ref allowance for typed VAR slots.
							boolean nullOk = (actualT == cType.cNull) && (
								adef.getPrimType() == cType.cString
								|| adef.getPrimType() == cType.cList
								|| adef.getPrimType() == cType.cMap
								|| adef.getPrimType() == cType.cObject
								|| adef.getPrimType() == cType.cCallback);
							if (!nullOk) {
								AussomException e = new AussomException(exType.exRuntime);
								e.setException(this.getLineNum(), "INVALID_DATA_TYPE", "Function '" + this.getName() + "' definition at position " + (i + 1) + " is expected to be of type '" + adef.getPrimType().name() + "' but found '" + actualT.name() + "' instead.", env.getCallStack().getStackTrace());
								return e;
							}
						}
					}
					
					if (adef.getType() == astNodeType.ETCETERA) {
						AussomList etcList = new AussomList();
						for (int j = i; j < args.getValue().size(); j++) {
							etcList.add(args.getValue().get(j));
						}
						env.getLocals().add("etc", etcList);
						break;
					} else {
						env.getLocals().add(adef.getName(), args.getValue().get(i));
					}
				} else if (adef.getType() == astNodeType.ETCETERA) {
					AussomList etcList = new AussomList();
					env.getLocals().add("etc", etcList);
					break;
				} else if (adef.getType() != astNodeType.VAR) {
					// Not a var, so it is a defalut value. Evaluate
					// against tenv so the synthetic <arg-defaults>
					// frame is visible to the debugger.
					env.getLocals().add(adef.getName(), adef.eval(tenv, false));
				} else {
					AussomException e = new AussomException(exType.exRuntime);
					e.setException(this.getLineNum(), "ARGUMENT_NUMBER", "Number of arguments provided does not match the number in definition.", "Number of arguments provided does not match the number in definition.", env.getCallStack().getStackTrace());
					ret = e;
				}
				i++;
			}
		} else if (this.argList.getArgs().size() > 0) {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(this.getLineNum(), "ARGUMENT_NUMBER", "Number of arguments provided does not match the number in definition.", "Number of arguments provided does not match the number in definition.", env.getCallStack().getStackTrace());
			ret = e;
		}
		
		if(ret.isNull()) {
			ret = new AussomBool(true);
		}

		return ret;
	}
	
	public AussomType call (Environment env, boolean getRef, AussomList args, String FileName) throws aussomException {
		AussomType ret = new AussomNull();
		boolean retFound = false;
		
		AussomType tnode = this.initArgs(env, args);
		
		CallStack cst = new CallStack(FileName, this.getLineNum(), env.getClassInstance().getClassDef().getName(), this.getName(), "Defined.");
		cst.setParent(env.getCallStack());
		
		// Itterate statement list of function
		if(!tnode.isEx()) {
			if(this.instructionList != null) {
				for(int i = 0; (i < this.instructionList.getStatements().size())&&(!ret.isEx()); i++) {
					astNode statement = this.instructionList.getStatements().get(i);
					AussomType tmp = null;
					if(statement != null) {
						Environment tenv = new Environment(env.getEngine());
						tenv.setEnvironment(env.getClassInstance(), env.getLocals(), cst);
						tmp = statement.eval(tenv, getRef);
						if(tmp.isReturn()) {
							ret = ((AussomReturn)tmp).getValue();
							retFound = true;
							break;
						} else if(tmp.isEx()) {
							ret = tmp;
							break;
						}
					} else {
						AussomException e = new AussomException(exType.exInternal);
						e.setException(this.getLineNum(), "NULL_PTR", "Null pointer for statement. (aFunctDef::call)", "Null pointer for statement.", env.getCallStack().getStackTrace());
						ret = e;
						break;
					}
				}
			} else {
				AussomException e = new AussomException(exType.exInternal);
				e.setException(this.getLineNum(), "NULL_PTR", "Null pointer for slist. (aFunctDef::call)", "Null pointer for slist.", env.getCallStack().getStackTrace());
				ret = e;
			}
		}
		else
			ret = tnode;
		
		if(ret == null) {
			if(retFound) {
				AussomException e = new AussomException(exType.exInternal);
				e.setException(this.getLineNum(), "NULL_PTR", "Null pointer returned. (aFunctDef::call)", "Null pointer returned.", env.getCallStack().getStackTrace());
				ret = e;
			} else {
				// No return statement, create new astNode of aUndef and return.
				ret = new AussomNull();
			}
		}

		
		return ret;
	}
	
	public AussomType callExtern(Environment env, AussomList args) throws aussomException {
		AussomType ret = new AussomNull();
		
		AussomObject callingObj = env.getClassInstance();
		Object o = callingObj.getExternObject();
		
		if(o != null) {
			ArrayList<AussomType> fargs = null;
			try {
				fargs = this.getExternArgs(env, args);
			} catch (aussomException e) {
				AussomException ex = new AussomException(exType.exRuntime);
				ex.setException(this.getLineNum(), "EXTERN_ARGS_EXCEPTION", "Exception while getting args: '" + this.getName() + "'.", env.getCallStack().getStackTrace());
				return ex;
			}

			try {
				Class<?> aclass = callingObj.getClassDef().getExternClass();
				if (aclass == null) {
					System.out.println("aclass null");
				}
				Method meth = aclass.getMethod(this.getName(), Environment.class, ArrayList.class);
				AussomType tmp = (AussomType)meth.invoke(o, env, fargs);
				if((tmp != null)&&(tmp instanceof AussomType)) ret = tmp;
				else
					throw new aussomException(this, "Return value found from calling '" + this.getName() + "' is null or not of type AussomType.", env.stackTraceToString());
			} catch (NoSuchMethodException e) {
				AussomException ex = new AussomException(exType.exRuntime);
				ex.setException(this.getLineNum(), "EXTERN_NO_SUCH_METHOD", "External call, no such method '" + this.getName() + "'.", env.getCallStack().getStackTrace());
				return ex;
			} catch (SecurityException e) {
				AussomException ex = new AussomException(exType.exRuntime);
				ex.setException(this.getLineNum(), "EXTERN_SECURITY_EXCEPTION", "External call, security exception for method '" + this.getName() + "'.", env.getCallStack().getStackTrace());
				return ex;
			} catch (IllegalAccessException e) {
				AussomException ex = new AussomException(exType.exRuntime);
				ex.setException(this.getLineNum(), "EXTERN_ILLEGAL_ACCESS", "External call, illegal access exception for method '" + this.getName() + "'.", env.getCallStack().getStackTrace());
				return ex;
			} catch (IllegalArgumentException e) {
				AussomException ex = new AussomException(exType.exRuntime);
				ex.setException(this.getLineNum(), "EXTERN_ILLEGAL_ARGUMENT", "External call, illegal argument exception for method '" + this.getName() + "'.", env.getCallStack().getStackTrace());
				return ex;
			} catch(StackOverflowError e) {
				AussomException ex = new AussomException(exType.exRuntime);
				ex.setException(this.getLineNum(), "EXTERN_STACK_OVERFLOW", "External call, stack overflow exception for method '" + this.getName() + "'. Infinite recursion perhaps?", env.getCallStack().getStackTrace());
				return ex;
			} catch (InvocationTargetException e) {
				AussomException ex = new AussomException(exType.exRuntime);
				ex.setException(this.getLineNum(), "EXTERN_INVOCATION_TARGET_EXCEPTION", "External call, invocation target exception for method '" + this.getName() + "', the external method threw an uncaught exception: " + e.getTargetException().toString(), env.getCallStack().getStackTrace());
				return ex;
			} catch (aussomException e) {
				AussomException ex = new AussomException(exType.exRuntime);
				ex.setException(this.getLineNum(), "EXTERN_EXCEPTION", e.getMessage(), env.getCallStack().getStackTrace());
				return ex;
			} catch (Exception e) {
				// P5: previously this catch silently swallowed Java exceptions
				// (printStackTrace + fall through to return null). Surface as a
				// proper Aussom exception with the call-site line and stack so
				// the user can see what went wrong.
				AussomException ex = new AussomException(exType.exRuntime);
				ex.setException(this.getLineNum(), "EXTERN_UNCAUGHT_JAVA",
					"External call, uncaught Java exception in method '" + this.getName()
						+ "': " + e.getClass().getSimpleName()
						+ (e.getMessage() != null ? ": " + e.getMessage() : ""),
					env.getCallStack().getStackTrace());
				return ex;
			}
		} else {
			AussomException ex = new AussomException(exType.exRuntime);
			ex.setException(this.getLineNum(), "EXTERN_OBJECT_NOT_FOUND", "External object not found when calling '" + this.getName() + "'.", env.getCallStack().getStackTrace());
			return ex;
		}

		// P1: enrich any AussomException returned by stdlib code with
		// line, id, and stack trace from the call site. Stdlib methods
		// commonly construct exceptions via the single-arg
		// AussomException(text) constructor which leaves lineNumber=-1,
		// id="", and stackTrace="". This wrap is gated on lineNumber==-1
		// so any stdlib site that already populated those fields is
		// left alone.
		if (ret instanceof AussomException) {
			AussomException ex = (AussomException) ret;
			if (ex.getLineNumber() == -1) {
				ex.setLineNumber(this.getLineNum());
			}
			if (ex.getStackTrace() == null || ex.getStackTrace().isEmpty()) {
				ex.setStackTrace(env.getCallStack().getStackTrace());
			}
			if (ex.getId() == null || ex.getId().isEmpty()) {
				ex.setId("EXTERN_RUNTIME");
			}
		}

		return ret;
	}
	
	private ArrayList<AussomType> getExternArgs(Environment env, AussomList eargs) throws aussomException
	{
		ArrayList<AussomType> args = new ArrayList<AussomType>();

		// Push a synthetic frame so debugger pauses inside default
		// argument expressions for extern dispatch show this function
		// as the active context. See design/debugging-callstack-update.md.
		String className = (env.getClassInstance() != null)
			? env.getClassInstance().getClassDef().getName() : "";
		CallStack defaultsFrame = new CallStack(this.getFileName(), this.getLineNum(),
			className, this.getName() + " <extern-arg-defaults>",
			"Default argument values.");
		defaultsFrame.setParent(env.getCallStack());
		Environment tenv = new Environment(env.getEngine());
		tenv.setEnvironment(env.getClassInstance(), env.getLocals(), defaultsFrame);

		boolean etcFound = false;
		AussomList etcList = new AussomList();
		cType etype = cType.cUndef;
		
		for(int i = 0; (i < this.argList.getArgs().size())||(i < eargs.size()); i++) {
			if((i < this.argList.getArgs().size())&&(this.argList.getArgs().get(i).getType() == astNodeType.ETCETERA)) {
				if(!etcFound) {
					etype = ((astEtcetera)this.argList.getArgs().get(i)).getPrimType();
					etcFound = true;
				}
				else
					throw new aussomException(this, "Already found a etcetera (...) definition in this function definition.", env.stackTraceToString());
			}
			
			if(etcFound) {
				if(i < eargs.size()) {
					if(etype != cType.cUndef) {
						if(eargs.getValue().get(i).getType() == etype) {
							etcList.add(eargs.getValue().get(i));
						}
						else
							throw new aussomException(this, "Etcetera (...) list is expecting type '" + etype.name() + " but found type '" + eargs.getValue().get(i).getType().name() + "'.", env.stackTraceToString());
					}
					else
						etcList.add(eargs.getValue().get(i));
				}
			} else {
				if(etcFound) {
					throw new aussomException(this, "Cannot have arguments in function definition after etcetera (...).", env.stackTraceToString());
				} else {
					if(this.argList.getArgs().size() > i) {
						astNode v = this.argList.getArgs().get(i);

						if(i < eargs.size()) {
							if((v.getPrimType() == cType.cUndef)||(v.getPrimType() == eargs.getValue().get(i).getType())||(eargs.getValue().get(i).isNull())) {
								args.add(eargs.getValue().get(i));
							} else if((v.getPrimType() == cType.cInt)&&(eargs.getValue().get(i).getType() == cType.cDouble)){
								AussomInt ai = new AussomInt((int)((AussomDouble)eargs.getValue().get(i)).getValue());
								args.add(ai);
							} else if((v.getPrimType() == cType.cDouble)&&(eargs.getValue().get(i).getType() == cType.cInt)) {
								AussomDouble ad = new AussomDouble((double)((AussomInt)eargs.getValue().get(i)).getValue());
								args.add(ad);
							} else {
								throw new aussomException(this, "Expecting type '" + v.getPrimType().name() + "' but found type '" + eargs.getValue().get(i).getType().name() + "'.", env.stackTraceToString());
							}
						} else {
							args.add(this.argList.getArgs().get(i).eval(tenv));
						}
					} else {
						throw new aussomException(this, "Incorrect number of arguments provided to function '" + this.getName() + "'. Provided " + String.valueOf(eargs.size()) + " but expecting " + String.valueOf(this.argList.getArgs().size()) + ".", env.stackTraceToString());
					}
				}
			}
		}
		
		if(etcFound) {
			args.add(etcList);
		}
		
		return args;
	}

	public AussomType getAussomdoc() {
		AussomMap ret = new AussomMap();

		ret.put("name", new AussomString(this.getName()));

		List<astNode> args = this.argList.getArgs();
		AussomList cargs = new AussomList();
		for (astNode arg : args) {
			astNodeInt targ = (astNodeInt)arg;
			AussomMap am = new AussomMap();
			am.put("type", new AussomString(targ.getType().name().toLowerCase()));
			if (targ.getType() != astNodeType.ETCETERA) {
				am.put("name", new AussomString(targ.getName()));
				am.put("specifiedType", new AussomString(arg.getPrimType().name().substring(1).toLowerCase()));
				if (targ.getType() != astNodeType.VAR) {
					am.put("valueType", new AussomString(arg.getPrimType().name().substring(1).toLowerCase()));
					if (targ.getType() == astNodeType.STRING) {
						am.put("value", new AussomString(((astString) arg).getValueString()));
					} else if (targ.getType() == astNodeType.BOOL) {
						am.put("value", new AussomBool(((astBool) arg).getValueBool()));
					} else if (targ.getType() == astNodeType.INT) {
						am.put("value", new AussomInt(((astInt) arg).getValueInt()));
					} else if (targ.getType() == astNodeType.DOUBLE) {
						am.put("value", new AussomDouble(((astDouble) arg).getValueDouble()));
					} else if (targ.getType() == astNodeType.NULL) {
						am.put("value", new AussomNull());
					}
				}
			}
			cargs.add(am);
		}
		ret.put("args", cargs);

		if (this.docNode != null) {
			ret.put("aussomDoc", this.docNode.getAussomdoc());
		}

		return ret;
	}
}

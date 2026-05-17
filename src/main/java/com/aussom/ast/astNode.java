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

import com.aussom.DebuggerInt;
import com.aussom.Engine;
import com.aussom.Environment;
import com.aussom.PauseReason;
import com.aussom.types.AussomException;
import com.aussom.types.AussomType;
import com.aussom.types.cType;

import java.util.ArrayList;
import java.util.List;

public class astNode {
	private String name = "";
	private astNodeType type = astNodeType.UNDEF;
	private String fileName = "";
	private int lineNum = 0;
	private int colNum = 0;
	
	private AccessType accessType = AccessType.aPrivate;
	
	// Primative type allows for strong typing.
	protected cType primativeType = cType.cUndef;
	
	private astNode child = null;

	protected astAussomDoc docNode = null;

	protected List<astAnnotation> annotations = new ArrayList<astAnnotation>();

	/**
	 * Debugger breakpoint flag. Volatile because the debugger
	 * control thread sets this while interpreter threads read it.
	 * Read inside the gated debug block of astNode.eval, so the
	 * cost is paid only when debug mode is on. Defaults to false.
	 * See design/debugging-interface-design.md section 4.
	 */
	public volatile boolean breakpoint = false;

	public void setPrimType(cType PrimType) {
		this.primativeType = PrimType;
	}
	
	public cType getPrimType() {
		return this.primativeType;
	}
	
	public String getNodeStr(int Level) {
		String rstr = "";
		rstr += getTabs(Level) + "\"type\": \"" + this.type.name() + "\",\n";
		rstr += getTabs(Level) + "\"name\": \"" + this.name + "\",\n";
		rstr += getTabs(Level) + "\"line\": " + this.getLineNum();
		if (this.type != astNodeType.UNDEF) {
			rstr += ",\n";
			rstr += getTabs(Level) + "\"dataType\": \"" + this.type.name() + "\"";
		}
		if (this.primativeType != cType.cUndef) {
			rstr += ",\n";
			rstr += getTabs(Level) + "\"primativeType\": \"" + this.primativeType.name() + "\"";
		}
		return rstr;
	}
	
	public static String getTabs(int level) {
		String s = "";
		for(int i = 0; i < level; i++) s += "\t";
		return s;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public astNodeType getType() {
		return type;
	}
	public void setType(astNodeType type) {
		this.type = type;
	}

	public void setFileName(String FileName){
		this.fileName = FileName;
	}
	
	public String getFileName() {
		return this.fileName;
	}
	
	public void setLineNum(int LineNum) {
		this.lineNum = LineNum;
	}
	
	public int getLineNum() {
		return this.lineNum;
	}
	
	public void setColNum(int ColNum) {
		this.colNum = ColNum;
	}
	
	public int getColNum() {
		return this.colNum;
	}
	
	public void setChild(astNode Child) {
		this.child = Child;
	}

	public astNode getChild() {
		return this.child;
	}

	public void appendChild(astNode Child) {
		astNode cur = this;
		while (cur.getChild() != null) {
			cur = cur.getChild();
		}
		cur.setChild(Child);
	}
	
	public void setParserInfo(String FileName, int LineNum, int ColNum) {
		this.fileName = FileName;
		this.lineNum = LineNum;
		this.colNum = ColNum;
	}

	public astAussomDoc getDocNode() {
		return docNode;
	}

	public void setDocNode(astAussomDoc docNode) {
		this.docNode = docNode;
	}

	public void addAnnotations(List<astAnnotation> Annotations) {
		this.annotations.addAll(Annotations);
	}

	public List<astAnnotation> getAnnotations() {
		return this.annotations;
	}

	public void setAnnotations(List<astAnnotation> Annotations) {
		this.annotations = Annotations;
	}

	public astAnnotation getAnnotation(String annotationName) {
		for (astAnnotation annotation : this.annotations) {
			if (annotation.getAnnotationName().equals(annotationName)) {
				return annotation;
			}
		}
		return null;
	}

	public AussomType eval(Environment env) throws aussomException {
		return this.eval(env, false);
	}
	
	public AussomType eval(Environment env, boolean getref) throws aussomException {
		AussomType ret = null;
		Engine eng = env.getEngine();

		if (!eng.isDebugMode()) {
			// FAST PATH: debug mode off (production). Just the
			// dispatch, no try/catch, no debug hooks, no
			// post-eval check. This is byte-for-byte the
			// pre-debugger eval body so the JIT has every chance
			// to inline the evalImpl calls aggressively.
			switch(type) {
			case NULL:
				ret = ((astNull)this).evalImpl(env, getref);
				break;
			case BOOL:
				ret = ((astBool)this).evalImpl(env, getref);
				break;
			case INT:
				ret = ((astInt)this).evalImpl(env, getref);
				break;
			case DOUBLE:
				ret = ((astDouble)this).evalImpl(env, getref);
				break;
			case STRING:
				ret = ((astString)this).evalImpl(env, getref);
				break;
			case LIST:
				ret = ((astList)this).evalImpl(env, getref);
				break;
			case MAP:
				ret = ((astMap)this).evalImpl(env, getref);
				break;
			case OBJ:
				ret = ((astObj)this).evalImpl(env, getref);
				break;
			case VAR:
				ret = ((astVar)this).evalImpl(env, getref);
				break;
			case EXP:
				ret = ((astExpression)this).evalImpl(env, getref);
				break;
			case FUNCTCALL:
				ret = ((astFunctCall)this).evalImpl(env, getref);
				break;
			case FUNCTDEFARGSLIST:
				ret = ((astFunctDefArgsList)this).evalImpl(env, getref);
				break;
			case RETURN:
				ret = ((astReturn)this).evalImpl(env, getref);
				break;
			case TRYCATCH:
				ret = ((astTryCatch)this).evalImpl(env, getref);
				break;
			case NEWINST:
				ret = ((astNewInst)this).evalImpl(env, getref);
				break;
			case IFELSE:
				ret = ((astIfElse)this).evalImpl(env, getref);
				break;
			case CONDITION:
				ret = ((astConditionBlock)this).evalImpl(env, getref);
				break;
			case SWITCH:
				ret = ((astSwitch)this).evalImpl(env, getref);
				break;
			case WHILE:
				ret= ((astWhile)this).evalImpl(env, getref);
				break;
			case BREAK:
				ret = ((astBreak)this).evalImpl(env, getref);
				break;
			case FOR:
				ret = ((astFor)this).evalImpl(env, getref);
				break;
			case CALLBACK:
				ret = ((astCallback)this).evalImpl(env, getref);
				break;
			case THROW:
				ret = ((astThrow)this).evalImpl(env, getref);
				break;
			case INCLUDE:
				ret = ((astInclude)this).evalImpl(env, getref);
				break;
			case AUSSOM_DOC:
				ret = ((astAussomDoc)this).evalImpl(env, getref);
				break;
			default:
				throw new aussomException(this, "INTERNAL [astNode.eval] Not implemented, attempting to eval type '" + type.name() + "'.", env.stackTraceToString());
			}
		} else {
			// SLOW PATH: debug mode on. All hooks live here.
			// --- pre-eval hook: breakpoints and step requests ---
			DebuggerInt dbg = eng.getDebugger();
			if (dbg != null) {
				if (this.breakpoint) {
					dbg.onPause(this, env, PauseReason.BREAKPOINT);
				} else if (dbg.shouldPauseForStep(this, env)) {
					dbg.onPause(this, env, PauseReason.STEP);
				}
			}

			try {
				switch(type) {
				case NULL:
					ret = ((astNull)this).evalImpl(env, getref);
					break;
				case BOOL:
					ret = ((astBool)this).evalImpl(env, getref);
					break;
				case INT:
					ret = ((astInt)this).evalImpl(env, getref);
					break;
				case DOUBLE:
					ret = ((astDouble)this).evalImpl(env, getref);
					break;
				case STRING:
					ret = ((astString)this).evalImpl(env, getref);
					break;
				case LIST:
					ret = ((astList)this).evalImpl(env, getref);
					break;
				case MAP:
					ret = ((astMap)this).evalImpl(env, getref);
					break;
				case OBJ:
					ret = ((astObj)this).evalImpl(env, getref);
					break;
				case VAR:
					ret = ((astVar)this).evalImpl(env, getref);
					break;
				case EXP:
					ret = ((astExpression)this).evalImpl(env, getref);
					break;
				case FUNCTCALL:
					ret = ((astFunctCall)this).evalImpl(env, getref);
					break;
				case FUNCTDEFARGSLIST:
					ret = ((astFunctDefArgsList)this).evalImpl(env, getref);
					break;
				case RETURN:
					ret = ((astReturn)this).evalImpl(env, getref);
					break;
				case TRYCATCH:
					ret = ((astTryCatch)this).evalImpl(env, getref);
					break;
				case NEWINST:
					ret = ((astNewInst)this).evalImpl(env, getref);
					break;
				case IFELSE:
					ret = ((astIfElse)this).evalImpl(env, getref);
					break;
				case CONDITION:
					ret = ((astConditionBlock)this).evalImpl(env, getref);
					break;
				case SWITCH:
					ret = ((astSwitch)this).evalImpl(env, getref);
					break;
				case WHILE:
					ret= ((astWhile)this).evalImpl(env, getref);
					break;
				case BREAK:
					ret = ((astBreak)this).evalImpl(env, getref);
					break;
				case FOR:
					ret = ((astFor)this).evalImpl(env, getref);
					break;
				case CALLBACK:
					ret = ((astCallback)this).evalImpl(env, getref);
					break;
				case THROW:
					ret = ((astThrow)this).evalImpl(env, getref);
					break;
				case INCLUDE:
					ret = ((astInclude)this).evalImpl(env, getref);
					break;
				case AUSSOM_DOC:
					ret = ((astAussomDoc)this).evalImpl(env, getref);
					break;
				default:
					throw new aussomException(this, "INTERNAL [astNode.eval] Not implemented, attempting to eval type '" + type.name() + "'.", env.stackTraceToString());
				}
			} catch (Exception jex) {
				// --- post-eval hook (throw form) ---
				// Notify on first sighting of this throwable, dedupe
				// via per-thread ThreadLocal so we fire exactly once
				// per logical throw rather than once per unwinding
				// frame.
				if (eng.getLastSeenThrowable().get() != jex) {
					eng.getLastSeenThrowable().set(jex);
					DebuggerInt dbgThrow = eng.getDebugger();
					if (dbgThrow != null) dbgThrow.onException(jex, env);
				}
				throw jex;
			}

			// --- post-eval hook (value form) ---
			// Notify on first sighting of an AussomException value
			// flowing out of eval. Dedup via the value's own
			// debuggerSeen flag.
			if (ret != null && ret.isEx()) {
				AussomException ex = (AussomException) ret;
				if (!ex.isDebuggerSeen()) {
					ex.setDebuggerSeen(true);
					DebuggerInt dbgValue = eng.getDebugger();
					if (dbgValue != null) dbgValue.onException(ex, env);
				}
			}
		}

		return ret;
	}

	public static boolean isBreakReturnEvent(AussomType ret) {
		if((ret != null)&&((ret.getType() == cType.cReturn)||(ret.getType() == cType.cBreak))) return true;
		return false;
	}

	public static boolean isBreakEvent(AussomType ret) {
		if(ret.getType() == cType.cBreak) return true;
		return false;
	}
	
	public static boolean isBreakReturnExcept(AussomType ret) {
		if (astNode.isBreakReturnEvent(ret) || ret.getType() == cType.cException) return true;
		return false;
	}

	public AccessType getAccessType() {
		return accessType;
	}

	public void setAccessType(AccessType accessType) {
		this.accessType = accessType;
	}
}

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

import com.aussom.Environment;
import com.aussom.types.*;
import com.aussom.types.AussomException.exType;

public class astObj  extends astNode implements astNodeInt {
	private astNode index = null;
	
	//private Map<String, objContainer> members = new ConcurrentHashMap<String, objContainer>();
	
	public astObj() {
		this.setType(astNodeType.OBJ);
	}
	
	public void setIndex(astNode Index) {
		this.index = Index;
	}
	
	public astNode getIndex() {
		return this.index;
	}
	
	@Override
	public AussomType evalImpl(Environment env, boolean getRef) throws aussomException {
		AussomType ret;
		
		if (env.getCurObj() == null) {
			ret = this.evalObjStart(env, getRef);
		} else {
			ret = this.evalObj(env, getRef);
		}

		return ret;
	}
	
	/**
	 * This method is called if the envorinment current object isn't set. This 
	 * is the start of the object path.
	 * @param env The current Environment object.
	 * @param getRef a boolean with true to get reference or false for not.
	 * @return A AussomType object.
	 * @throws aussomException
	 */
	private AussomType evalObjStart(Environment env, boolean getRef) throws aussomException {
		AussomType ret;
		
		// this object
		if (this.getName().equals("this")) {
		  // We need to get the object pointer from the 
		  // current object.
		  if (this.getChild() != null) {
			// Set the current object to this class instance.
			Environment tenv = env.clone(env.getClassInstance());
			ret = this.getChild().eval(tenv, getRef);
		  } else {
			return env.getClassInstance();
		  }
		}
		
		// Found in locals.
		else if (!getRef && env.getLocals().contains(this.getName())) {
		  if (this.getChild() != null) {
			Environment tenv = env.clone(env.getLocals().get(this.getName()));
			ret = this.getChild().eval(tenv, getRef);
		  } else {
			return env.getLocals().get(this.getName());
		  }
		}
		
		// Static object found.
		else if (env.getEngine().containsStaticClass(this.getName())) {
			if (this.getChild() != null) {
				// We need to get the extern static class.
				if (this.getChild().getType() == astNodeType.FUNCTCALL || this.getChild().getType() == astNodeType.OBJ) {
					Environment tenv = env.clone(env.getEngine().getStaticClass(this.getName()));
					ret = this.getChild().eval(tenv, getRef);
				} else {
					AussomException e = new AussomException(exType.exInternal);
					e.setException(getLineNum(), "NOT_IMPLEMENTED", "astObj.evalObjStart(): Static class with child of '" + this.getChild().getType().name() + "' found.", env.getCallStack().getStackTrace());
					return e;
				}
			} else {
				AussomException e = new AussomException(exType.exRuntime);
				e.setException(getLineNum(), "NO_OPERATION", "astObj.evalObjStart(): Static class object found but no child function or property provided.", env.getCallStack().getStackTrace());
				return e;
			}
		}
		
		// If getRef is true and we have no children, then just add to locals.
		else if (getRef && this.getChild() == null) {
		  if (!env.getLocals().contains(this.getName())) {
			env.getLocals().add(this.getName(), new AussomNull());
		  }
		  AussomRef ref = new AussomRef();
		  ref.setMap(this.getName(), env.getLocals().getMap());
		  ret = ref;
		}
		
		else if (getRef && this.getChild() != null && env.getLocals().contains(this.getName())) {
		  Environment tenv = env.clone(env.getLocals().get(this.getName()));
		  ret = this.getChild().eval(tenv, getRef);
		}
		
		// Else, the name didn't match a class, static class, or local
		// variable in scope. Treat as undefined.
		else {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "UNDEFINED_NAME", "astObj.evalObjStart(): Undefined name '" + this.getName() + "'.", env.getCallStack().getStackTrace());
		  return e;
		}
		
		return ret;
	}
	
	private AussomType evalObj (Environment env, boolean getRef) throws aussomException {
		AussomType ret;
		
		// Found that it exists in the current object. Short-circuits via
		// containsMember without forcing Members allocation on
		// primitives that have no members.
		if (env.getCurObj() instanceof AussomObject && ((AussomObject)env.getCurObj()).containsMember(this.getName())) {
		  AussomObject curObj = (AussomObject) env.getCurObj();
		  // Check member access.
		  if (this.memberHasAccess(env, this.getName())) {
			if (this.getChild() != null) {
			  Environment tenv = env.clone(curObj.getMember(this.getName()));
			  ret = this.getChild().eval(tenv, getRef);
			} else {
			  if (getRef) {
				AussomRef ref = new AussomRef();
				// Members must exist here because containsMember was
				// true above; getMembers().getMap() returns the same
				// underlying map without re-allocation.
				ref.setMap(this.getName(), curObj.getMembers().getMap());
				ret = ref;
			  } else {
				ret = curObj.getMember(this.getName());
			  }
			}
		  } else {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "NO_ACCESS", "astObj.evalObj(): No access to member '" + this.getName() + "'.", env.getCallStack().getStackTrace());
			return e;
		  }
		}
		
		// Found in current map object.
		else if (env.getCurObj().getType() == cType.cMap) {
			String key = this.getName();
			if (this.getIndex() != null) {
				Environment tenv = env.clone(((AussomMap)env.getCurObj()).getValue().get(key));
				AussomType indRet = this.getIndex().eval(tenv, false);
				if (indRet.isEx()) {
					return indRet;
				}
				key = ((AussomTypeInt)indRet).str();
			}
			AussomMap mp = (AussomMap)env.getCurObj();

			// Descend through child first, regardless of getRef. The
			// leaf node decides whether to return a ref or a value.
			// This is what makes chained writes like
			// mp['a']['b'] = v reach the inner key instead of silently
			// overwriting the outer slot.
			if (this.getChild() != null) {
				if (!mp.contains(key)) {
					AussomException e = new AussomException(exType.exRuntime);
					e.setException(this.getLineNum(), "MAP_MISSING_KEY", "astObj.evalObj(): Map doesn't have key '" + key + "'.", env.getCallStack().getStackTrace());
					return e;
				}
				Environment tenv = env.clone(mp.getValue().get(key));
				ret = this.getChild().eval(tenv, getRef);
			} else if (getRef) {
				// Terminal map-slot write. Slot may or may not exist;
				// assignment.assign() will create or overwrite it.
				AussomRef ref = new AussomRef();
				ref.setMap(key, mp.getValue());
				ret = ref;
			} else if (mp.contains(key)) {
				ret = mp.getValue().get(key);
			} else {
				AussomException e = new AussomException(exType.exRuntime);
				e.setException(this.getLineNum(), "MAP_MISSING_KEY", "astObj.evalObj(): Map doesn't have key '" + key + "'.", env.getCallStack().getStackTrace());
				return e;
			}
		}
		
		// Found in current list object.
		else if (env.getCurObj().getType() == cType.cList) {
		  if (this.index != null) {
			Environment ienv = env.clone(null);
			AussomType ctindex = this.index.eval(ienv, false);
			if (ctindex.isEx()) {
				return ctindex;
			}
			if (ctindex.getType() == cType.cInt) {
			  long ind = ((AussomInt)ctindex).getValue();
			  AussomList lst = (AussomList)env.getCurObj();
			  if (ind >= 0 && ((long)ind) < lst.getValue().size()) {
				if (this.getChild() != null) {
				  Environment tenv = env.clone(lst.getValue().get((int)ind));
				  ret = this.getChild().eval(tenv, getRef);
				} else if (getRef) {
				  // Terminal list-slot write. Return a ref so the
				  // assignment path can write to the existing slot.
				  AussomRef ref = new AussomRef();
				  ref.setList((int)ind, lst.getValue());
				  ret = ref;
				} else {
				  ret = lst.getValue().get((int)ind);
				}
			  } else {
				AussomException e = new AussomException(exType.exRuntime);
				e.setException(this.getLineNum(), "INDEX_OUT_OF_BOUNDS", "astObj.evalObj(): Index out of bounds.", env.getCallStack().getStackTrace());
				return e;
			  }
			} else {
			  AussomException e = new AussomException(exType.exRuntime);
			  e.setException(this.getLineNum(), "INDEX_NOT_FOUND", "astObj.evalObj(): Provided index isn't an integer value.", env.getCallStack().getStackTrace());
			  return e;
			}
		  } else {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(this.getLineNum(), "INDEX_NOT_FOUND", "astObj.evalObj(): List found but no index found.", env.getCallStack().getStackTrace());
			return e;
		  }
		}

		// Object index likely found.
		else if (env.getCurObj() instanceof AussomObject && this.getName().equals("") && this.index != null) {
			Environment ienv = env.clone(null);
			AussomType ctindex = this.index.eval(ienv, false);
			if (ctindex.isEx()) {
				return ctindex;
			}
			if (ctindex.getType() == cType.cString) {
				String ind = ((AussomString)ctindex).getValue();
				AussomObject ao = (AussomObject) env.getCurObj();
				if (ao.containsMember(ind)) {
					// We found it!
					if (this.getChild() != null) {
						if (env.getCurObj() instanceof AussomMap) {
							Environment tenv = env.clone(((AussomMap)env.getCurObj()).getValue().get(ind));
							ret = this.getChild().eval(tenv, getRef);
						} else {
							AussomException e = new AussomException(exType.exRuntime);
							e.setException(this.getLineNum(), "INVALID_EXPRESSION", "astObj.evalObj(): Expecting type Map but found '" + ((AussomObject) env.getCurObj()).getType() + "' instead.", env.getCallStack().getStackTrace());
							return e;
						}
					} else {
						ret = ao.getMember(ind);
					}
				} else {
					AussomException e = new AussomException(exType.exRuntime);
					e.setException(this.getLineNum(), "NO_MEMBER_FOUND", "astObj.evalObj(): Object doesn't have member '" + ind + "'.", env.getCallStack().getStackTrace());
					return e;
				}
			} else {
				AussomException e = new AussomException(exType.exRuntime);
				e.setException(this.getLineNum(), "INDEX_NOT_FOUND", "astObj.evalObj(): Provided index isn't a string value.", env.getCallStack().getStackTrace());
				return e;
			}
		}

		else {
		  AussomException e = new AussomException(exType.exInternal);
		  e.setException(this.getLineNum(), "NO_MEMBER_FOUND", "astObj.evalObj(): Unmatched object type for '" + this.getName() + "'.", env.getCallStack().getStackTrace());
		  return e;
		}
		
		return ret;
	}
	
	private boolean memberHasAccess (Environment env, String memberName) {
		if (env.getClassInstance() != env.getCurObj()) {
			astClass ac = ((AussomObject)env.getCurObj()).getClassDef();
			if (ac.containsMember(memberName)) {
			  if (ac.getMember(memberName).getAccessType() == AccessType.aPrivate) {
				return false;
			  } else {
				return true;
			  }
			} else {
			  return true;
			}
		  } else {
			return true;
		  }
	}
	
	@Override
	public String toString(int Level) {
		String rstr = "";
		rstr += getTabs(Level) + "{\n";
		rstr += this.getNodeStr(Level + 1) + ",\n";
		if(this.index != null) {
		  rstr += getTabs(Level + 1) + "\"index\":\n";
			rstr += ((astNodeInt)this.index).toString(Level + 1) + ",\n";
		}
		if(this.getChild() != null) {
			rstr += getTabs(Level + 1) + "\"child\":\n";
			rstr += ((astNodeInt)this.getChild()).toString(Level + 1) + ",\n";
		}
		rstr += getTabs(Level) + "}";
		return rstr;
	}
}

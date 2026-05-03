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

import com.aussom.Environment;
import com.aussom.types.AussomBool;
import com.aussom.types.AussomDouble;
import com.aussom.types.AussomException;
import com.aussom.types.AussomException.exType;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomList;
import com.aussom.types.AussomMap;
import com.aussom.types.AussomNull;
import com.aussom.types.AussomObject;
import com.aussom.types.AussomRef;
import com.aussom.types.AussomString;
import com.aussom.types.AussomType;
import com.aussom.types.AussomTypeInt;
import com.aussom.types.cType;

public class astExpression extends astNode implements astNodeInt {
	private astNode left = null;
	private astNode right = null;
	private expType eType = expType.UNDEF;
	
	public astExpression() {
		this.setType(astNodeType.EXP);
	}
	
	public astExpression(astNode Left) {
		this.setType(astNodeType.EXP);
		this.setLeft(Left);
	}

	@Override
	public String toString() {
		return this.toString(0);
	}
	
	@Override
	public String toString(int Level) {
		String rstr = "";
		rstr += getTabs(Level) + "{\n";
		rstr += this.getNodeStr(Level + 1) + ",\n";
		rstr += getTabs(Level + 1) + "\"eType\": \"" + this.eType.name() + "\"\n";
		if(this.left != null) {
			rstr += getTabs(Level + 1) + "\"left\":\n";
			rstr += ((astNodeInt)this.left).toString(Level + 1) + ",\n";
		}
		if(this.right != null) {
			rstr += getTabs(Level + 1) + "\"right\":\n";
			rstr += ((astNodeInt)this.right).toString(Level + 1) + ",\n";
		}
		if (this.getChild() != null) {
			rstr += getTabs(Level + 1) + "\"child\":\n";
			rstr += ((astNodeInt)this.getChild()).toString(Level + 1) + ",\n";
		}
		rstr += getTabs(Level) + "}";
		return rstr;
	}

	public astNode getLeft() {
		return left;
	}

	public void setLeft(astNode left) {
		this.left = left;
	}

	public astNode getRight() {
		return right;
	}

	public void setRight(astNode right) {
		this.right = right;
	}

	public expType geteType() {
		return eType;
	}

	public void seteType(expType eType) {
		this.eType = eType;
	}

	@Override
	public AussomType evalImpl(Environment env, boolean getRef) throws aussomException {
		// Load-bearing init: when both `left` and `right` are null
		// neither switch below assigns ret, but the trailing
		// `!ret.isEx()` guard reads it. Keep this allocation; the
		// other initializers in this file are pure waste and have
		// been dropped under O5.
		AussomType ret = new AussomNull();

		if((this.left != null)&&(this.right != null)) {
			switch(this.eType) {
				case ASSIGNMENT: {
					ret = this.assignment(env, getRef);
					break;
				} case SET: {
					ret = this.set(env, getRef);
					break;
				} case ADD: {
					ret = this.oper(env, getRef);
					break;
				} case SUBTRACT: {
					ret = this.oper(env, getRef);
					break;
				} case MULTIPLY: {
					ret = this.oper(env, getRef);
					break;
				} case DIVIDE: {
					ret = this.oper(env, getRef);
					break;
				} case FLOORDIV: {
					ret = this.oper(env, getRef);
					break;
				} case MODULUS: {
					ret = this.oper(env, getRef);
					break;
				} case EQEQ: {
					ret = this.oper(env, getRef);
					break;
				} case NOTEQ: {
					ret = this.oper(env, getRef);
					break;
				} case LT: {
					ret = this.oper(env, getRef);
					break;
				} case GT: {
					ret = this.oper(env, getRef);
					break;
				} case LTEQ: {
					ret = this.oper(env, getRef);
					break;
				} case GTEQ: {
					ret = this.oper(env, getRef);
					break;
				} case AND: {
					ret = this.oper(env, getRef);
					break;
				} case OR: {
					ret = this.oper(env, getRef);
					break;
				} case INSERT: {
					ret = this.oper(env, getRef);
					break;
				} case INSTANCEOF: {
					ret = this.oper(env, getRef);
					break;
				} case PLEQ: {
					ret = this.operEquals(env, getRef);
					break;
				} case MIEQ: {
					ret = this.operEquals(env, getRef);
					break;
				} case MUEQ: {
					ret = this.operEquals(env, getRef);
					break;
				} case DIEQ: {
					ret = this.operEquals(env, getRef);
					break;
				} default: {
					AussomException e = new AussomException(exType.exInternal);
					e.setException(this.getLineNum(), "UNDEFINED_EXPRESSION", "Undefined expression '" + this.eType.name() + "' found. (aExp::call)", "Undefined expression '" + this.eType.name() + "' found. (aExp::call)", env.getCallStack().getStackTrace());
					ret = e;
					break;
				}
			}
		} else if(this.left != null) {
			switch(this.eType) {
				case PLPL: {
					ret = this.operIncDec(env, getRef);
					break;
				} case MIMI: {
					ret = this.operIncDec(env, getRef);
					break;
				} case NOT: {
					ret = this.operLeft(env, getRef);
					break;
				} case MISSNULL: {
					ret = this.operLeft(env, getRef);
					break;
				} case COUNT: {
					ret = this.operLeft(env, getRef);
					break;
				} case INCLUDE: {
					ret = this.operLeft(env, getRef);
					break;
				} default: {
					AussomException e = new AussomException(exType.exInternal);
					e.setException(this.getLineNum(), "UNDEFINED_EXPRESSION", "Undefined expression '" + this.eType.name() + "' found. (aExp::call)", "Undefined expression '" + this.eType.name() + "' found. (aExp::call)", env.getCallStack().getStackTrace());
					ret = e;
					break;
				}
			}
		}

		// If child is defined, evaluate it as well.
		if (!ret.isEx() && this.getChild() != null) {
		  Environment tenv = env;
		  if (ret instanceof AussomObject || ret.getType() == cType.cMap || ret.getType() == cType.cList) {
			tenv = env.clone(ret);
		  }
		  ret = this.getChild().eval(tenv, getRef);
		}

		return ret;
	}

	private AussomType assignment(Environment env, boolean getRef) throws aussomException {
		AussomType ret;
		
		AussomType lres = this.left.eval(env, true);
		if (!lres.isEx()) {
		  if (lres.getType() == cType.cRef) {
			AussomRef ref = (AussomRef)lres;
			AussomType rval = this.right.eval(env, getRef);
			if (!rval.isEx()) {
			  ref.assign(rval);
			  ret = rval;
			} else {
			  ret = rval;
			}
		  } else {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(this.getLineNum(), "ASSIGN_NOT_POSSIBLE", "astExpression.assignment(): Left side of assignment expression returned an object of type '" + lres.getType().name() + "' which can't be assigned.", env.getCallStack().getStackTrace());
			return e;
		  }
		} else {
		  ret = lres;
		}
		return ret;
	}

	private AussomType set(Environment env, boolean getRef) throws aussomException {
		AussomType ret;

		AussomType lres = this.left.eval(env, false);
		if (!lres.isEx()) {
			if (lres instanceof AussomObject) {
				AussomObject ao = (AussomObject)lres;

				AussomType rres = this.right.eval(env, false);
				if (!rres.isEx()) {
					if (rres instanceof AussomMap) {
						AussomMap am = (AussomMap)rres;
						AussomType tret = this.setSet(ao, am, env, getRef);
						if (tret.isEx()) {
							return tret;
						}
						ret = lres;
					} else {
						AussomException e = new AussomException(exType.exRuntime);
						e.setException(this.getLineNum(), "INIT_NOT_POSSIBLE", "astExpression.init(): Right side of init expression returned invalid type '" + lres.getType().name() + "', expecting 'map'.", env.getCallStack().getStackTrace());
						return e;
					}
				} else {
					return rres;
				}
			} else {
				AussomException e = new AussomException(exType.exRuntime);
				e.setException(this.getLineNum(), "INIT_NOT_POSSIBLE", "astExpression.init(): Left side of init expression returned invalid type '" + lres.getType().name() + "', expecting 'object'.", env.getCallStack().getStackTrace());
				return e;
			}
		} else {
			return lres;
		}

		return ret;
	}

	private AussomType oper(Environment env, boolean getRef) throws aussomException {
		AussomType ret;

		AussomType r_left  = left.eval(env, getRef);
		if(!r_left.isEx()) {

			// Short circuit evaluation of OR and AND.
			if (this.eType == expType.AND) {
				boolean left = this.boolVal(r_left);
				if (!left)
					return new AussomBool(false);
			} else if (this.eType == expType.OR) {
				boolean left = this.boolVal(r_left);
				if (left)
					return new AussomBool(true);
			}

			AussomType r_right = right.eval(env, getRef);
			if(!r_right.isEx()) {
				switch(this.eType) {
					case ADD: {
						ret = evalPlus(env, r_left, r_right);
						break;
					} case SUBTRACT: {
						ret = evalMinus(env, r_left, r_right);
						break;
					} case MULTIPLY: {
						ret = evalMult(env, r_left, r_right);
						break;
					} case DIVIDE: {
						ret = evalDiv(env, r_left, r_right);
						break;
					} case FLOORDIV: {
						ret = evalFloorDiv(env, r_left, r_right);
						break;
					} case MODULUS: {
						ret = evalModulus(env, r_left, r_right);
						break;
					} case EQEQ: {
						ret = evalEqualsEquals(env, r_left, r_right);
						break;
					} case NOTEQ: {
						ret = evalNotEquals(env, r_left, r_right);
						break;
					} case LT: {
						ret = evalLessThan(env, r_left, r_right);
						break;
					} case GT: {
						ret = evalGreaterThan(env, r_left, r_right);
						break;
					} case LTEQ: {
						ret = evalLessThanEquals(env, r_left, r_right);
						break;
					} case GTEQ: {
						ret = evalGreaterThanEquals(env, r_left, r_right);
						break;
					} case AND: {
						ret = evalAnd(env, r_right);
						break;
					} case OR: {
						ret = evalOr(env, r_right);
						break;
					} case INSERT: {
						ret = evalInsert(env, r_left, r_right);
						break;
					} case INSTANCEOF: {
						ret = evalInstanceOf(env, r_left, r_right);
						break;
					} default: {
						AussomException e = new AussomException(exType.exInternal);
						e.setException(this.getLineNum(), "UNDEFINED_EXPRESSION", "Undefined operator expression '" + this.eType.name() + "' found. (aExp::call)", "Undefined operator expression '" + this.eType.name() + "' found. (aExp::call)", env.getCallStack().getStackTrace());
						ret = e;
						break;
					}
				}
			} else {
				ret = r_right;
			}
		} else {
			ret = r_left;
		}

		return ret;
	}
	
	private AussomType evalPlus(Environment env, AussomType r_left, AussomType r_right) {
		AussomType ret;

		// Let's do actual addition.
		if (this.isNumber(r_left) && this.isNumber(r_right)) {
		  if (this.isInt(r_left) && this.isInt(r_right)) {
			ret = new AussomInt(this.getValueInt(r_left) + this.getValueInt(r_right));
		  } else if (this.isInt(r_left)) {
			ret = new AussomDouble((double)this.getValueInt(r_left) + ((AussomDouble)r_right).getValue());
		  } else if (this.isInt(r_right)) {
			ret = new AussomDouble(((AussomDouble)r_left).getValue() + (double)this.getValueInt(r_right));
		  } else {
			  ret = new AussomDouble(((AussomDouble)r_left).getValue() + ((AussomDouble)r_right).getValue());
		  }
		}
		
		// Otherwise, we are going to treat as concatenation.
		else {
		  ret = new AussomString(((AussomTypeInt)r_left).str() + ((AussomTypeInt)r_right).str());
		}

		return ret;
	}
	
	private AussomType evalMinus(Environment env, AussomType r_left, AussomType r_right) {
		AussomType ret;

		if (this.isNumber(r_left) && this.isNumber(r_right)) {
		  if (this.isInt(r_left) && this.isInt(r_right)) {
			ret = new AussomInt(this.getValueInt(r_left) - this.getValueInt(r_right));
		  } else if (this.isInt(r_left)) {
			ret = new AussomDouble((double)this.getValueInt(r_left) - ((AussomDouble)r_right).getValue());
		  } else if (this.isInt(r_right)) {
			ret = new AussomDouble(((AussomDouble)r_left).getValue() - (double)this.getValueInt(r_right));
		  } else {
			  ret = new AussomDouble(((AussomDouble)r_left).getValue() - ((AussomDouble)r_right).getValue());
		  }
		} else {
		  if (!this.isNumber(r_left)) {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "INVALID_EXPRESSION", "astExpression.evalMinus(): Left side of expression isn't a number.", env.getCallStack().getStackTrace());
			return e;
		  } else {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "INVALID_EXPRESSION", "astExpression.evalMinus(): Right side of expression isn't a number.", env.getCallStack().getStackTrace());
			return e;
		  }
		}

		return ret;
	}
	
	private AussomType evalMult(Environment env, AussomType r_left, AussomType r_right) {
		AussomType ret;

		if (this.isNumber(r_left) && this.isNumber(r_right)) {
		  if (this.isInt(r_left) && this.isInt(r_right)) {
			ret = new AussomInt(this.getValueInt(r_left) * this.getValueInt(r_right));
		  } else if (this.isInt(r_left)) {
			ret = new AussomDouble((double)this.getValueInt(r_left) * ((AussomDouble)r_right).getValue());
		  } else if (this.isInt(r_right)) {
			ret = new AussomDouble(((AussomDouble)r_left).getValue() * (double)this.getValueInt(r_right));
		  } else {
			  ret = new AussomDouble(((AussomDouble)r_left).getValue() * ((AussomDouble)r_right).getValue());
		  }
		} else {
		  if (!this.isNumber(r_left)) {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "INVALID_EXPRESSION", "astExpression.evalMult(): Left side of expression isn't a number.", env.getCallStack().getStackTrace());
			return e;
		  } else {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "INVALID_EXPRESSION", "astExpression.evalMult(): Right side of expression isn't a number.", env.getCallStack().getStackTrace());
			return e;
		  }
		}

		return ret;
	}
	
	private AussomType evalDiv(Environment env, AussomType r_left, AussomType r_right) {
		AussomType ret;

		if (this.isNumber(r_left) && this.isNumber(r_right)) {
		  if (r_right.getType() == cType.cInt && this.getValueInt(r_right) == 0) {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "DIV_BY_0", "astExpression.evalDiv(): Division by 0 exception.", env.getCallStack().getStackTrace());
			return e;
		  } else if (r_right.getType() == cType.cDouble && ((AussomDouble)r_right).getValue() == 0.0) {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "DIV_BY_0", "astExpression.evalDiv(): Division by 0 exception.", env.getCallStack().getStackTrace());
			return e;
		  } else {
			if (this.isInt(r_left) && this.isInt(r_right)) {
			  ret = new AussomDouble((double)this.getValueInt(r_left) / (double)this.getValueInt(r_right));
			} else if (this.isInt(r_left)) {
			  ret = new AussomDouble((double)this.getValueInt(r_left) / ((AussomDouble)r_right).getValue());
			} else if (this.isInt(r_right)) {
			  ret = new AussomDouble(((AussomDouble)r_left).getValue() / (double)this.getValueInt(r_right));
			} else {
				ret = new AussomDouble(((AussomDouble)r_left).getValue() / ((AussomDouble)r_right).getValue());
			}
		  }
		} else {
		  if (!this.isNumber(r_left)) {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "INVALID_EXPRESSION", "astExpression.evalDiv(): Left side of expression isn't a number.", env.getCallStack().getStackTrace());
			return e;
		  } else {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "INVALID_EXPRESSION", "astExpression.evalDiv(): Right side of expression isn't a number.", env.getCallStack().getStackTrace());
			return e;
		  }
		}

		return ret;
	}
	
	private AussomType evalModulus(Environment env, AussomType r_left, AussomType r_right) {
		AussomType ret;

		if (this.isNumber(r_left) && this.isNumber(r_right)) {
		  if (r_right.getType() == cType.cInt && this.getValueInt(r_right) == 0) {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "DIV_BY_0", "astExpression.evalModulus(): Division by 0 exception.", env.getCallStack().getStackTrace());
			return e;
		  } else if (r_right.getType() == cType.cDouble && ((AussomDouble)r_right).getValue() == 0.0) {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "DIV_BY_0", "astExpression.evalModulus(): Division by 0 exception.", env.getCallStack().getStackTrace());
			return e;
		  } else {
			if (this.isInt(r_left) && this.isInt(r_right)) {
			  ret = new AussomInt(this.getValueInt(r_left) % this.getValueInt(r_right));
			} else if (this.isInt(r_left)) {
			  ret = new AussomDouble((double)this.getValueInt(r_left) % ((AussomDouble)r_right).getValue());
			} else if (this.isInt(r_right)) {
			  ret = new AussomDouble(((AussomDouble)r_left).getValue() % (double)this.getValueInt(r_right));
			} else {
				ret = new AussomDouble(((AussomDouble)r_left).getValue() % ((AussomDouble)r_right).getValue());
			}
		  }
		} else {
		  if (!this.isNumber(r_left)) {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "INVALID_EXPRESSION", "astExpression.evalModulus(): Left side of expression isn't a number.", env.getCallStack().getStackTrace());
			return e;
		  } else {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "INVALID_EXPRESSION", "astExpression.evalModulus(): Right side of expression isn't a number.", env.getCallStack().getStackTrace());
			return e;
		  }
		}

		return ret;
	}

	private AussomType evalFloorDiv(Environment env, AussomType r_left, AussomType r_right) {
		AussomType ret;

		if (this.isNumber(r_left) && this.isNumber(r_right)) {
		  if (r_right.getType() == cType.cInt && this.getValueInt(r_right) == 0) {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "DIV_BY_0", "astExpression.evalFloorDiv(): Division by 0 exception.", env.getCallStack().getStackTrace());
			return e;
		  } else if (r_right.getType() == cType.cDouble && ((AussomDouble)r_right).getValue() == 0.0) {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "DIV_BY_0", "astExpression.evalFloorDiv(): Division by 0 exception.", env.getCallStack().getStackTrace());
			return e;
		  } else {
			if (this.isInt(r_left) && this.isInt(r_right)) {
			  ret = new AussomInt(Math.floorDiv(this.getValueInt(r_left), this.getValueInt(r_right)));
			} else if (this.isInt(r_left)) {
			  ret = new AussomDouble(Math.floor((double)this.getValueInt(r_left) / ((AussomDouble)r_right).getValue()));
			} else if (this.isInt(r_right)) {
			  ret = new AussomDouble(Math.floor(((AussomDouble)r_left).getValue() / (double)this.getValueInt(r_right)));
			} else {
			  ret = new AussomDouble(Math.floor(((AussomDouble)r_left).getValue() / ((AussomDouble)r_right).getValue()));
			}
		  }
		} else {
		  if (!this.isNumber(r_left)) {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "INVALID_EXPRESSION", "astExpression.evalFloorDiv(): Left side of expression isn't a number.", env.getCallStack().getStackTrace());
			return e;
		  } else {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "INVALID_EXPRESSION", "astExpression.evalFloorDiv(): Right side of expression isn't a number.", env.getCallStack().getStackTrace());
			return e;
		  }
		}

		return ret;
	}

	private AussomType evalEqualsEquals(Environment env, AussomType r_left, AussomType r_right) {
		AussomType ret;

		if (this.isNumber(r_left) && this.isNumber(r_right)) {
		  if (this.isInt(r_left) && this.isInt(r_right)) {
			if (this.getValueInt(r_left) == this.getValueInt(r_right)) ret = new AussomBool(true);
			else ret = new AussomBool(false);
		  } else if (this.isInt(r_left)) {
			if ((double)this.getValueInt(r_left) == ((AussomDouble)r_right).getValue()) ret = new AussomBool(true);
			else ret = new AussomBool(false);
		  } else if (this.isInt(r_right)) {
			if (((AussomDouble)r_left).getValue() == (double)this.getValueInt(r_right)) ret = new AussomBool(true);
			else ret = new AussomBool(false);
		  } else {
			if (((AussomDouble)r_left).getValue() == ((AussomDouble)r_right).getValue()) ret = new AussomBool(true);
			else ret = new AussomBool(false);
		  }
		} else if (r_left.getType() == cType.cString || r_right.getType() == cType.cString) {
		  if (((AussomTypeInt)r_left).str().equals(((AussomTypeInt)r_right).str())) ret = new AussomBool(true);
		  else ret = new AussomBool(false);
		} else if (r_left.isNull() && r_right.isNull()) {
		  ret = new AussomBool(true);
		} else if (r_left == r_right) ret = new AussomBool(true);
		else ret = new AussomBool(false);

		return ret;
	}
	
	private AussomType evalLessThan(Environment env, AussomType r_left, AussomType r_right) {
		AussomType ret;

		if (this.isNumber(r_left) && this.isNumber(r_right)) {
			// If both are int
			if (this.isInt(r_left) && this.isInt(r_right)) {
				if (this.getValueInt(r_left) < this.getValueInt(r_right)) ret = new AussomBool(true);
				else ret = new AussomBool(false);
			}
			// Else cast them to double and compare.
			else {
				if (this.getValueDouble(r_left) < this.getValueDouble(r_right)) ret = new AussomBool(true);
				else ret = new AussomBool(false);
			}
		}
		else if (r_left instanceof AussomString && r_right instanceof AussomString) {
			int cmp = ((AussomString) r_left).getValue().compareTo(((AussomString) r_right).getValue());
			ret = new AussomBool(cmp < 0);
		}
		else {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "INVALID_COMPARISON", "astExpression.evalLessThan(): Less than comparison of non-number.", env.getCallStack().getStackTrace());
		  return e;
		}

		return ret;
	}
	
	private AussomType evalGreaterThan(Environment env, AussomType r_left, AussomType r_right) {
		AussomType ret;

		if (this.isNumber(r_left) && this.isNumber(r_right)) {
			// If both are int
			if (this.isInt(r_left) && this.isInt(r_right)) {
				if (this.getValueInt(r_left) > this.getValueInt(r_right)) ret = new AussomBool(true);
				else ret = new AussomBool(false);
			}
			// Else cast them to double and compare.
			else {
				if (this.getValueDouble(r_left) > this.getValueDouble(r_right)) ret = new AussomBool(true);
				else ret = new AussomBool(false);
			}
		}
		else if (r_left instanceof AussomString && r_right instanceof AussomString) {
			int cmp = ((AussomString) r_left).getValue().compareTo(((AussomString) r_right).getValue());
			ret = new AussomBool(cmp > 0);
		}
		else {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "INVALID_COMPARISON", "astExpression.evalGreaterThan(): Greater than comparison of non-number.", env.getCallStack().getStackTrace());
		  return e;
		}

		return ret;
	}
	
	private AussomType evalLessThanEquals(Environment env, AussomType r_left, AussomType r_right) {
		AussomType ret;

		if (this.isNumber(r_left) && this.isNumber(r_right)) {
			// If both are int
			if (this.isInt(r_left) && this.isInt(r_right)) {
				if (this.getValueInt(r_left) <= this.getValueInt(r_right)) ret = new AussomBool(true);
				else ret = new AussomBool(false);
			}
			// Else cast them to double and compare.
			else {
				if (this.getValueDouble(r_left) <= this.getValueDouble(r_right)) ret = new AussomBool(true);
				else ret = new AussomBool(false);
			}
		}
		else if (r_left instanceof AussomString && r_right instanceof AussomString) {
			int cmp = ((AussomString) r_left).getValue().compareTo(((AussomString) r_right).getValue());
			ret = new AussomBool(cmp <= 0);
		}
		else {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "INVALID_COMPARISON", "astExpression.evalLessThanEquals(): Less than equals comparison of non-number.", env.getCallStack().getStackTrace());
		  return e;
		}

		return ret;
	}
	
	private AussomType evalGreaterThanEquals(Environment env, AussomType r_left, AussomType r_right) {
		AussomType ret;

		if (this.isNumber(r_left) && this.isNumber(r_right)) {
			// If both are int
			if (this.isInt(r_left) && this.isInt(r_right)) {
				if (this.getValueInt(r_left) >= this.getValueInt(r_right)) ret = new AussomBool(true);
				else ret = new AussomBool(false);
			}
			// Else cast them to double and compare.
			else {
				if (this.getValueDouble(r_left) >= this.getValueDouble(r_right)) ret = new AussomBool(true);
				else ret = new AussomBool(false);
			}
		}
		else if (r_left instanceof AussomString && r_right instanceof AussomString) {
			int cmp = ((AussomString) r_left).getValue().compareTo(((AussomString) r_right).getValue());
			ret = new AussomBool(cmp >= 0);
		}
		else {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "INVALID_COMPARISON", "astExpression.evalGreaterThanEquals(): Greater than equals comparison of non-number.", env.getCallStack().getStackTrace());
		  return e;
		}

		return ret;
	}

	private AussomType evalAnd(Environment env, AussomType r_right) {
		return new AussomBool(this.boolVal(r_right));
	}
	
	private AussomType evalOr(Environment env, AussomType r_right) {
		return new AussomBool(this.boolVal(r_right));
	}
	
	private AussomType evalInsert(Environment env, AussomType r_left, AussomType r_right) {
		if (r_left.getType() == cType.cList) {
		  ((AussomList)r_left).add(r_right);
		  return r_left;
		} else {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "INSERT_NOT_POSSIBLE", "astExpression.evalInsert(): Left side of insert expression not a list.", env.getCallStack().getStackTrace());
		  return e;
		}
	}
	
	private AussomType evalInstanceOf(Environment env, AussomType r_left, AussomType r_right) {
		if (r_left instanceof AussomObject) {
			if (r_right instanceof AussomString) {
				  if (((AussomObject)r_left).getClassDef().instanceOf(((AussomString)r_right).getValue())) {
					  return new AussomBool(true);
				  } else {
					  return new AussomBool(false);
				  }
			} else {
				AussomException e = new AussomException(exType.exRuntime);
				e.setException(getLineNum(), "UNEXPECTED_TYPE_FOUND", "astExpression.evalInstanceOf(): Left side of expression is not an instance of AussomObject.", env.getCallStack().getStackTrace());
				return e;
			}
		} else {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "UNEXPECTED_TYPE_FOUND", "astExpression.evalInstanceOf(): Left side of expression is not an instance of AussomObject.", env.getCallStack().getStackTrace());
		  return e;
		}
	}
	
	private AussomType evalNotEquals(Environment env, AussomType r_left, AussomType r_right) {
		AussomType ret = this.evalEqualsEquals(env, r_left, r_right);
		return new AussomBool(!((AussomBool)ret).getValue());
	}
	
	private AussomType operEquals(Environment env, boolean getRef) throws aussomException {
		AussomType ret;

		AussomType r_left  = left.eval(env, true);
		if(!r_left.isEx()) {
			if (r_left.getType() == cType.cRef) {
			  AussomRef ref = (AussomRef)r_left;
			  AussomType r_right = right.eval(env, getRef);
			  if(!r_right.isEx()) {
				  switch(this.eType) {
					  case PLEQ: {
						  ret = evalPlusEquals(env, ref, r_right);
						  break;
					  } case MIEQ: {
						  ret = evalMinusEquals(env, ref, r_right);
						  break;
					  } case MUEQ: {
						  ret = evalMultEquals(env, ref, r_right);
						  break;
					  } case DIEQ: {
						  ret = evalDivEquals(env, ref, r_right);
						  break;
					  } default: {
							AussomException e = new AussomException(exType.exInternal);
							e.setException(this.getLineNum(), "UNDEFINED_EXPRESSION", "Undefined oper-equals expression '" + this.eType.name() + "' found. (aExp::call)", "Undefined oper-equals expression '" + this.eType.name() + "' found. (aExp::call)", env.getCallStack().getStackTrace());
							ret = e;
							break;
						}
				  }
			  } else {
				  ret = r_right;
			  }
			} else {
			  AussomException e = new AussomException(exType.exRuntime);
			  e.setException(getLineNum(), "ASSIGN_NOT_POSSIBLE", "astExpression.operEquals(): Left side of assignment expression returned an object of type '" + r_left.getType().name() + "' which can't be assigned.", env.getCallStack().getStackTrace());
			  return e;
			}
		}
		else {
			ret = r_left;
		}

		return ret;
	}
	
	private AussomType evalPlusEquals(Environment env, AussomType r_left, AussomType r_right) {
		AussomRef ref = (AussomRef)r_left;
		try {
		  AussomType lval = ref.getValue();
		  AussomType res = this.evalPlus(env, lval, r_right);
		  if (!res.isEx()) {
			ref.assign(res);
			return res;
		  } else {
			return res;
		  }
		} catch (Exception ex) {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "ASSIGN_NOT_POSSIBLE", "astExpression.evalPlusEquals(): " + ex.getMessage(), env.getCallStack().getStackTrace());
		  return e;
		}
	}
	
	private AussomType evalMinusEquals(Environment env, AussomType r_left, AussomType r_right) {
		AussomRef ref = (AussomRef)r_left;
		try {
		  AussomType lval = ref.getValue();
		  AussomType res = this.evalMinus(env, lval, r_right);
		  if (!res.isEx()) {
			ref.assign(res);
			return res;
		  } else {
			return res;
		  }
		} catch (Exception ex) {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "ASSIGN_NOT_POSSIBLE", "astExpression.evalMinusEquals(): " + ex.getMessage(), env.getCallStack().getStackTrace());
		  return e;
		}
	}
	
	private AussomType evalMultEquals(Environment env, AussomType r_left, AussomType r_right) {
		AussomRef ref = (AussomRef)r_left;
		try {
		  AussomType lval = ref.getValue();
		  AussomType res = this.evalMult(env, lval, r_right);
		  if (!res.isEx()) {
			ref.assign(res);
			return res;
		  } else {
			return res;
		  }
		} catch (Exception ex) {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "ASSIGN_NOT_POSSIBLE", "astExpression.evalMultEquals(): " + ex.getMessage(), env.getCallStack().getStackTrace());
		  return e;
		}
	}
	
	private AussomType evalDivEquals(Environment env, AussomType r_left, AussomType r_right) {
		AussomRef ref = (AussomRef)r_left;
		try {
		  AussomType lval = ref.getValue();
		  AussomType res = this.evalDiv(env, lval, r_right);
		  if (!res.isEx()) {
			ref.assign(res);
			return res;
		  } else {
			return res;
		  }
		} catch (Exception ex) {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "ASSIGN_NOT_POSSIBLE", "astExpression.evalDivEquals(): " + ex.getMessage(), env.getCallStack().getStackTrace());
		  return e;
		}
	}
	
	private AussomType operIncDec(Environment env, boolean getRef) throws aussomException {
		AussomType ret;

		AussomType r_left  = left.eval(env, true);
		if(!r_left.isEx()) {
			if (r_left.getType() == cType.cRef) {
			  AussomRef ref = (AussomRef)r_left;
			  switch(this.eType) {
				  case PLPL: {
					  ret = evalPlusPlus(env, ref);
					  break;
				  } case MIMI: {
					  ret = evalMinusMinus(env, ref);
					  break;
				  } default: {
						AussomException e = new AussomException(exType.exInternal);
						e.setException(this.getLineNum(), "UNDEFINED_EXPRESSION", "Undefined oper inc/dec expression '" + this.eType.name() + "' found. (aExp::call)", "Undefined oper inc/dec expression '" + this.eType.name() + "' found. (aExp::call)", env.getCallStack().getStackTrace());
						ret = e;
						break;
					}
			  }
			  
			} else {
			  AussomException e = new AussomException(exType.exRuntime);
			  e.setException(getLineNum(), "ASSIGN_NOT_POSSIBLE", "astExpression.operIncDec(): Left side of assignment expression returned an object of type '" + r_left.getType().name() + "' which can't be assigned.", env.getCallStack().getStackTrace());
			  return e;
			}
		} else {
			ret = r_left;
		}

		return ret;
	}
	
	private AussomType evalPlusPlus(Environment env, AussomType r_left) {
		AussomRef ref = (AussomRef)r_left;
		try {
		  AussomType lval = ref.getValue();
		  if (this.isNumber(lval)) {
			  if (this.isInt(lval)) {
				AussomInt val = new AussomInt(((AussomInt)lval).getValue() + 1);
				ref.assign(val);
				return val;
			  } else {
				AussomDouble val = new AussomDouble(((AussomDouble)lval).getValue() + 1.0);
				ref.assign(val);
				return val;
			  }
		  } else {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "ASSIGN_NOT_POSSIBLE", "astExpression.evalDivEquals(): Attempt to increment data type '" + lval.getType().name() + "'.", env.getCallStack().getStackTrace());
			return e;
		  }
		} catch (Exception ex) {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "ASSIGN_NOT_POSSIBLE", "astExpression.evalDivEquals(): " + ex.getMessage(), env.getCallStack().getStackTrace());
		  return e;
		}
	}
	
	private AussomType evalMinusMinus(Environment env, AussomType r_left) {
		AussomRef ref = (AussomRef)r_left;
		try {
		  AussomType lval = ref.getValue();
		  if (this.isNumber(lval)) {
			  if (this.isInt(lval)) {
				AussomInt val = new AussomInt(((AussomInt)lval).getValue() - 1);
				ref.assign(val);
				return val;
			  } else {
				AussomDouble val = new AussomDouble(((AussomDouble)lval).getValue() - 1.0);
				ref.assign(val);
				return val;
			  }
		  } else {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "ASSIGN_NOT_POSSIBLE", "astExpression.evalMinusMinus(): Attempt to increment data type '" + lval.getType().name() + "'.", env.getCallStack().getStackTrace());
			return e;
		  }
		} catch (Exception ex) {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "ASSIGN_NOT_POSSIBLE", "astExpression.evalMinusMinus(): " + ex.getMessage(), env.getCallStack().getStackTrace());
		  return e;
		}
	}
	
	private AussomType operLeft(Environment env, boolean getRef) throws aussomException {
		AussomType ret;

		if (this.eType == expType.MISSNULL) {
			ret = evalMissingNull(env, getRef);
		} else {
			AussomType r_left  = left.eval(env, getRef);
			if(!r_left.isEx()) {
				switch(this.eType) {
					case NOT: {
						ret = evalNot(env, r_left);
						break;
					} case COUNT: {
						ret = evalCount(env, r_left);
						break;
					} case INCLUDE: {
						ret = evalInclude(env, r_left);
						break;
					} default: {
						AussomException e = new AussomException(exType.exInternal);
						e.setException(this.getLineNum(), "UNDEFINED_EXPRESSION", "Undefined oper left expression '" + this.eType.name() + "' found. (aExp::call)", "Undefined oper left expression '" + this.eType.name() + "' found. (aExp::call)", env.getCallStack().getStackTrace());
						ret = e;
						break;
					}
				}
			} else {
				ret = r_left;
			}
		}

		return ret;
	}
	
	private AussomType evalNot(Environment env, AussomType r_left) {
		return new AussomBool(!this.boolVal(r_left));
	}

	private AussomType evalMissingNull(Environment env, boolean getRef) throws aussomException {
		AussomType ret;

		AussomType r_left  = left.eval(env, getRef);
		if(
			r_left.isEx() &&
			(
					((AussomException)r_left).getId().equals("NO_MEMBER_FOUND")
					|| ((AussomException)r_left).getId().equals("MAP_MISSING_KEY")
					|| ((AussomException)r_left).getId().equals("INDEX_NOT_FOUND")
					|| ((AussomException)r_left).getId().equals("INDEX_OUT_OF_BOUNDS")
			)
		) {
			ret = new AussomNull();
		} else {
			ret = r_left;
		}

		return ret;
	}

	private AussomType evalCount(Environment env, AussomType r_left) {
		if (r_left.getType() == cType.cList) {
		  return new AussomInt(((AussomList)r_left).getValue().size());
		} else if (r_left.getType() == cType.cMap) {
		  return new AussomInt(((AussomMap)r_left).size());
		} else if (r_left.getType() == cType.cString) {
		  return new AussomInt(((AussomString)r_left).getValue().length());
		} else {
		  AussomException e = new AussomException(exType.exRuntime);
		  e.setException(getLineNum(), "ASSIGN_NOT_POSSIBLE", "astExpression.evalCount(): Count operator (#) cannot be used for data type '" + r_left.getType().name() + "'.", env.getCallStack().getStackTrace());
		  return e;
		}
	}
	
	private AussomType evalInclude(Environment env, AussomType r_left) {
		if (r_left instanceof AussomString) {
			String includePath = ((AussomString)r_left).getValue();
			try {
				env.getEngine().addInclude(includePath);
			} catch (Exception ex) {
				AussomException e = new AussomException(exType.exRuntime);
				e.setException(getLineNum(), "INCLUDE_FAILED", "astExpression.evalInclude(): Failed to include '" + includePath + "'. " + ex.getMessage(), env.getCallStack().getStackTrace());
				return e;
			}
			return new AussomBool(true);
		} else {
			AussomException e = new AussomException(exType.exRuntime);
			e.setException(getLineNum(), "UNEXPECTED_DATA_TYPE", "astExpression.evalInclude(): Expecting string data type but found '" + r_left.getType().name() + "' instead.", env.getCallStack().getStackTrace());
			return e;
		}
	}
	
	
	private boolean isNumber(AussomType Item) {
		if (Item.getType() == cType.cInt || Item.getType() == cType.cDouble || Item.getType() == cType.cBool) return true;
		return false;
	}
	
	private boolean isInt(AussomType Item) {
		if (Item.getType() == cType.cInt || Item.getType() == cType.cBool) return true;
		return false;
	}
	
	private long getValueInt(AussomType Item) {
		if (Item.getType() == cType.cInt) {
			return (int) ((AussomInt)Item).getValue();
		} else if (((AussomBool)Item).getValue()) {
			return 1;
		}
		return 0;
	}

	private double getValueDouble(AussomType Item) {
		if (Item.getType() == cType.cDouble) {
			return (double) ((AussomDouble)Item).getValue();
		} else if (Item.getType() == cType.cInt) {
			return (double) ((AussomInt)Item).getValue();
		} else if (((AussomBool)Item).getValue()) {
			return 1.0;
		}
		return 0.0;
	}
	
	private boolean boolVal(AussomType Item) {
		if (Item.getType() == cType.cNull) {
			return false;
		} else if (Item.getType() == cType.cBool) {
			if (((AussomBool)Item).getValue() == false) return false;
			else return true;
		} else if (Item.getType() == cType.cInt) {
			if (((AussomInt)Item).getValue() == 0) return false;
			else return true;
		} else if (Item.getType() == cType.cDouble) {
			if (((AussomDouble)Item).getValue() == 0.0) return false;
			else return true;
		} else if (Item.getType() == cType.cString) {
			if (((AussomString)Item).getValue() == "") return false;
			else return true;
		}
		else if (Item instanceof AussomObject) return true;
		else if (Item.getType() == cType.cList) return true;
		else if (Item.getType() == cType.cMap) return true;
		else return false;
	}

	public AussomType setSet(AussomObject ao, AussomMap mp, Environment env, boolean getRef) throws aussomException {
		// Load-bearing init: the loop body never assigns ret on the
		// happy path, only the early-return arms do. Returning the
		// AussomNull mimics "no error".
		AussomType ret = new AussomNull();
		for (String key : mp.getValue().keySet()) {
			if (ao.getMembers().contains(key) && ao.getClassDef().getMember(key).getAccessType() == AccessType.aPublic) {
				ao.getMembers().getMap().put(key, mp.getValue().get(key));
			} else {
				// Compute the setter name once and route through the
				// overload dispatcher; it picks the matching 1-arg
				// overload (any signature) for us.
				if (key.length() < 2) {
					AussomException e = new AussomException(exType.exRuntime);
					e.setException(this.getLineNum(), "INIT_VAL_NOT_FOUND", "astExpression.init(): The provided init value '" + key + "' not found in object '" + ao.getClassDef().getName() + "'.", env.getCallStack().getStackTrace());
					return e;
				}
				String setterName = "set" + key.substring(0, 1).toUpperCase() + key.substring(1);
				astClass cls = ao.getClassDef();
				if (cls.hasAnyFunctionByName(setterName)) {
					AussomList args = new AussomList();
					args.add(mp.getValue().get(key));
					Environment tenv = env.clone(ao);
					tenv.setClassInstance(ao);
					AussomType tret = cls.call(tenv, getRef, setterName, args);
					if (tret.isEx()) {
						return tret;
					}
				} else {
					AussomException e = new AussomException(exType.exRuntime);
					e.setException(this.getLineNum(), "INIT_VAL_NOT_FOUND", "astExpression.init(): The provided init value '" + key + "' not found in object '" + ao.getClassDef().getName() + "'.", env.getCallStack().getStackTrace());
					return e;
				}
			}
		}
		return ret;
	}
}

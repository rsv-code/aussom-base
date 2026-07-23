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

import com.aussom.CallStack;
import com.aussom.Environment;
import com.aussom.types.AussomException;
import com.aussom.types.AussomList;
import com.aussom.types.AussomObject;
import com.aussom.types.AussomType;
import com.aussom.types.cType;

/**
 * Central helper for operator overloading. Maps operator expression
 * types to their reserved operator method names (the double
 * underscore "dunder" names), answers whether a value's class
 * defines one, and routes the call through the standard
 * astClass overload resolver. See design/operator-overloading.md.
 */
public final class OperOverload {
	// Unary and dereference operator method names used by callers
	// outside the binary method table.
	public static final String OP_NOT = "__opNot__";
	public static final String OP_COUNT = "__opCount__";
	public static final String OP_SET = "__opSet__";
	public static final String OP_INDEX = "__opIndex__";
	public static final String OP_INDEX_SET = "__opIndexSet__";
	public static final String OP_EQ = "__opEq__";
	public static final String OP_NOT_EQ = "__opNotEq__";

	private OperOverload() { }

	/**
	 * Returns the primary operator method name for a binary
	 * expression type, called on the left operand. Null when the
	 * expression type has no overloadable method.
	 * @param Type is the expression type.
	 * @return A String with the method name or null.
	 */
	public static String binaryMethod(expType Type) {
		switch (Type) {
			case ADD: return "__opAdd__";
			case SUBTRACT: return "__opSub__";
			case MULTIPLY: return "__opMul__";
			case DIVIDE: return "__opDiv__";
			case FLOORDIV: return "__opFloorDiv__";
			case MODULUS: return "__opMod__";
			case EQEQ: return OP_EQ;
			case NOTEQ: return OP_NOT_EQ;
			case LT: return "__opLt__";
			case GT: return "__opGt__";
			case LTEQ: return "__opLtEq__";
			case GTEQ: return "__opGtEq__";
			case INSERT: return "__opInsert__";
			case ANDB: return "__opAnd__";
			case ORB: return "__opOr__";
			default: return null;
		}
	}

	/**
	 * Returns the operator method name called on the right
	 * operand when the left operand does not overload. Arithmetic
	 * and bitwise operators use dedicated reflected names; the
	 * comparison operators mirror instead (2 &lt; vec calls
	 * vec.__opGt__(2)). Null when no right-side method exists.
	 * @param Type is the expression type.
	 * @return A String with the method name or null.
	 */
	public static String reflectedMethod(expType Type) {
		switch (Type) {
			case ADD: return "__opRightAdd__";
			case SUBTRACT: return "__opRightSub__";
			case MULTIPLY: return "__opRightMul__";
			case DIVIDE: return "__opRightDiv__";
			case FLOORDIV: return "__opRightFloorDiv__";
			case MODULUS: return "__opRightMod__";
			case ANDB: return "__opRightAnd__";
			case ORB: return "__opRightOr__";
			// Mirrored comparisons.
			case LT: return "__opGt__";
			case GT: return "__opLt__";
			case LTEQ: return "__opGtEq__";
			case GTEQ: return "__opLtEq__";
			default: return null;
		}
	}

	/**
	 * Returns the source glyph for an operator expression type.
	 * Used in error messages and stack trace frames so failures
	 * name the operator the way the user wrote it.
	 * @param Type is the expression type.
	 * @return A String with the operator glyph.
	 */
	public static String symbol(expType Type) {
		switch (Type) {
			case ADD: return "+";
			case SUBTRACT: return "-";
			case MULTIPLY: return "*";
			case DIVIDE: return "/";
			case FLOORDIV: return "~/";
			case MODULUS: return "%";
			case EQEQ: return "==";
			case NOTEQ: return "!=";
			case LT: return "<";
			case GT: return ">";
			case LTEQ: return "<=";
			case GTEQ: return ">=";
			case INSERT: return "@=";
			case ANDB: return "&";
			case ORB: return "|";
			case SET: return ":=";
			case NOT: return "!";
			case COUNT: return "#";
			default: return "?";
		}
	}

	/**
	 * True when the value is a class instance whose class defines
	 * the named operator method. Primitives (int, string, list, map
	 * and friends) have non-cObject types and exit on the first
	 * check, so the hot expression path only pays an enum compare.
	 * @param Val is the operand to check.
	 * @param Name is the operator method name.
	 * @return A boolean with true when the call is possible.
	 */
	public static boolean hasOp(AussomType Val, String Name) {
		if (Name == null) return false;
		if (Val.getType() != cType.cObject) return false;
		if (!(Val instanceof AussomObject)) return false;
		astClass def = ((AussomObject)Val).getClassDef();
		if (def == null) return false;
		return def.hasAnyFunctionByName(Name);
	}

	/**
	 * Calls an operator method on the target object. The
	 * environment is cloned onto the target the same way member
	 * calls do, so the method body runs with normal this access.
	 * Overload resolution, mocks and spies all apply because the
	 * call goes through astClass.call(). A call-site stack frame is
	 * pushed first so stack traces show the line where the operator
	 * was used, and resolution failures are reworded to name the
	 * operator glyph instead of reading like a plain method call.
	 * @param env is the current Environment object.
	 * @param Site is the AST node of the operator expression.
	 * @param Glyph is the operator as written in source (e.g. "+").
	 * @param Target is the receiving class instance.
	 * @param Name is the operator method name.
	 * @param Args is the argument list.
	 * @return The AussomType result of the method call.
	 * @throws aussomException on internal errors.
	 */
	public static AussomType call(Environment env, astNode Site, String Glyph, AussomObject Target, String Name, AussomList Args) throws aussomException {
		// Push a call-site frame, matching what astFunctCall does for
		// ordinary method calls.
		CallStack cst;
		synchronized (env.getCallStack()) {
			cst = new CallStack(Site.getFileName(), Site.getLineNum(), Target.getClassDef().getName(), Name, "Operator '" + Glyph + "' called.");
			cst.setParent(env.getCallStack());
		}
		Environment tenv = env.clone(Target);
		tenv.setEnvironment(Target, env.getLocals(), cst);
		AussomType ret = Target.getClassDef().call(tenv, false, Name, Args);

		// A resolution failure on the operator method itself reads
		// poorly without operator context. Reword it in place; errors
		// raised deeper inside the method body pass through untouched.
		if (ret.isEx()) {
			AussomException ex = (AussomException) ret;
			String id = ex.getId();
			boolean resolutionFailure = "FUNCT_NOT_FOUND".equals(id) || "AMBIGUOUS_OVERLOAD".equals(id);
			if (resolutionFailure && ex.getText() != null && ex.getText().contains(Name)) {
				String ctx = "Operator '" + Glyph + "' on object '" + Target.getClassDef().getName() + "': " + ex.getText();
				ex.setText(ctx);
				ex.setDetails(ctx);
				ex.setLineNumber(Site.getLineNum());
			}
		}
		return ret;
	}

	/**
	 * Convenience call for a single-argument operator method.
	 * @param env is the current Environment object.
	 * @param Site is the AST node of the operator expression.
	 * @param Glyph is the operator as written in source (e.g. "+").
	 * @param Target is the receiving class instance.
	 * @param Name is the operator method name.
	 * @param Arg is the single argument.
	 * @return The AussomType result of the method call.
	 * @throws aussomException on internal errors.
	 */
	public static AussomType callOne(Environment env, astNode Site, String Glyph, AussomObject Target, String Name, AussomType Arg) throws aussomException {
		AussomList args = new AussomList();
		args.add(Arg);
		return call(env, Site, Glyph, Target, Name, args);
	}
}

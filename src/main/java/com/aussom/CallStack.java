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

import com.aussom.ast.astFunctDef;

/**
 * CallStack object is a linked list representation of the Aussom function call
 * stack. It handles the accounting of call information for use in debugging
 * and exception handling.
 * @author austin
 */
public class CallStack {
	private CallStack parent = null;
	private String fileName = "";
	private int lineNumber = -1;
	private String className = "";
	private String functionName = "";
	private String text = "";
	private astFunctDef calledFunction = null;

	/**
	 * Default constructor.
	 */
	public CallStack() { }
	
	/**
	 * Constructor which takes the current call information.
	 * @param FileName is a String with the Aussom code file name.
	 * @param LineNumber is an integer with the source code line number.
	 * @param ClassName is a String with the current object class name.
	 * @param FunctionName is a String with the current function name.
	 * @param Text is a String with any text description of the call.
	 */
	public CallStack(String FileName, int LineNumber, String ClassName, String FunctionName, String Text) {
		this.fileName = FileName;
		this.lineNumber = LineNumber;
		this.className = ClassName;
		this.functionName = FunctionName;
		this.text = Text;
	}
	
	/**
	 * Gets the parent call object.
	 * @return A parent CallStack object or null if it doesn't exist.
	 */
	public CallStack getParent() {
		synchronized(this) {
			return this.parent;
		}
	}
	
	/**
	 * Sets the parent CallStack object.
	 * @param parent is a CallStack object to set as the parent.
	 */
	public void setParent(CallStack parent) {
		synchronized(this) {
			this.parent = parent;
		}
	}

	/**
	 * Gets the source file name as a String.
	 * @return A String with the source file name.
	 */
	public String getFileName() {
		synchronized (this) {
			return fileName;
		}
	}

	/**
	 * Gets the source file line number.
	 * @return An int with the source file line number.
	 */
	public int getLineNumber() {
		synchronized (this) {
			return lineNumber;
		}
	}

	/**
	 * Gets the class name.
	 * @return A String with the class name.
	 */
	public String getClassName() {
		synchronized (this) {
			return className;
		}
	}

	/**
	 * Gets the function name.
	 * @return A String with the function name.
	 */
	public String getFunctionName() {
		synchronized (this) {
			return functionName;
		}
	}

	/**
	 * Gets the text value.
	 * @return A String with the text value.
	 */
	public String getText() {
		synchronized(this) {
			return this.text;
		}
	}
	
	/**
	 * Sets the text value.
	 * @param str is a String with the text value.
	 */
	public void setText(String str) {
		synchronized(this) {
			this.text = str;
		}
	}

	/**
	 * Gets the astFunctDef this frame represents, or null if the
	 * frame does not correspond to a single Aussom function call
	 * (e.g. the engine's root frame, class-level synthetic frames
	 * like {@code <member-init>}, {@code <static-init>}, or
	 * {@code <reflect.getMethods>}).
	 *
	 * Useful for debuggers that need to read the function's
	 * declared arg list, annotations, or other metadata at pause
	 * time without re-walking the AST to find it.
	 *
	 * @return The astFunctDef bound to this frame, or null.
	 */
	public astFunctDef getCalledFunction() {
		synchronized(this) {
			return this.calledFunction;
		}
	}

	/**
	 * Sets the astFunctDef this frame represents. Callers in
	 * astFunctDef.call / initArgs / getExternArgs set it to `this`
	 * when they push the frame. Frames that are not function-scoped
	 * leave it null.
	 *
	 * @param f The astFunctDef bound to this frame.
	 */
	public void setCalledFunction(astFunctDef f) {
		synchronized(this) {
			this.calledFunction = f;
		}
	}

	/**
	 * Builds the stack trace from the current CallStack object and 
	 * returns it as a String.
	 * @return A String with the call stack trace.
	 */
	public String getStackTrace() {
		synchronized(this) {
			String rstr = "";
			if (!this.className.equals("") && !this.functionName.equals("")) {
				rstr += "\t[" + this.fileName + ":" + this.lineNumber + "] ";
				rstr += this.text;
				rstr += " { " + this.className + "." + this.functionName + "() }";
				rstr += "\n";
				if(parent != null) {
					rstr += this.parent.getStackTrace();
				}
			}
			return rstr;
		}
	}
	
	/**
	 * Obligatory toString method.
	 * @return A String with the call stack trace.
	 */
	@Override
	public String toString() { return this.getStackTrace(); }
}

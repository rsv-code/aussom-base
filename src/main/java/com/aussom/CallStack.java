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

	/*
	 * How many Aussom function calls are active on this chain, with the
	 * root frame at 0. Recorded when the frame is parented so reading it
	 * is a field read rather than a walk up the chain. The interpreter
	 * checks it once per call against the engine's call depth limit; see
	 * astNode.checkCallGate and design/security-evaluation-f4-f5.md
	 * section 3.1.
	 *
	 * Counted in calls, not frames. One Aussom call produces more than
	 * one frame: the call site pushes "Function called." and the body
	 * pushes "Defined.", and there are synthetic frames for argument
	 * defaults, static initializers and reflection. Only the body frame
	 * calls enterCall(), so a limit of 50 means 50 nested Aussom calls
	 * rather than some multiple of it that would depend on how the
	 * frames happen to be laid out.
	 *
	 * Read and written without synchronizing, unlike the rest of this
	 * class. A frame belongs to the one thread that created it, and that
	 * thread is the only one that writes this field or reads it for the
	 * depth check. Since JDK 15 removed biased locking every monitor is a
	 * real atomic operation, and taking two of them per Aussom call to
	 * guard a thread-confined int was measurable on the call path.
	 */
	private int depth = 0;

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
			if (parent == null) {
				this.depth = 0;
			} else {
				// Inherit rather than increment: a frame is not by itself
				// a function call. See the depth field's comment.
				this.depth = parent.depth;
			}
		}
	}

	/**
	 * Records that this frame is the body of an Aussom function call, one
	 * level deeper than its parent. Called by astFunctDef.call after it
	 * parents the frame, and by nothing else: it is what makes the depth
	 * count calls rather than frames.
	 */
	public void enterCall() {
		this.depth = this.depth + 1;
	}

	/**
	 * Gets how many Aussom function calls are active on this chain, with
	 * the root frame at 0. Recorded as frames are pushed rather than
	 * counted on demand, so the call depth check stays a field read.
	 * @return An int with the call depth of this frame.
	 */
	public int getDepth() {
		return this.depth;
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

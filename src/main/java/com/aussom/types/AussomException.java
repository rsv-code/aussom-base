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

package com.aussom.types;

import com.aussom.Environment;
import com.aussom.ast.astClass;

import java.util.ArrayList;

/**
 * User code exception. This is one that has to do with exceptions occurring
 * in the users aussom code. This is in contrast to aussomException which is
 * an engine level exception.
 */
public class AussomException extends AussomObject implements AussomTypeInt {
	public enum exType {
		exUndef,
		exInternal,
		exRuntime
	};
	
	private exType et = exType.exUndef;
	private int lineNumber = -1;
	private String id = "";
	private String text = "";
	private String details = "";
	private String stackTrace = "";

	// This flag is needed to differentiate thrown exceptions from
	// ones being passed aroudn as objects. Throw needs to set this
	// flag to false and catch needs to set it to true.
	private boolean isLocalObject = false;

	// Marks the exception the interpreter raises when Engine.cancel()
	// is called. Set only by the loop back-edge check in
	// astNode.checkCancellation, so Aussom code cannot forge one by
	// throwing an exception that carries the same id. astTryCatch
	// reads it to keep a catch block from
	// swallowing the cancellation. See the "Cancellation" section of
	// Engine.
	private boolean cancellation = false;

	// Set by the debugger hook in astNode.eval the first time the
	// value flows through eval. Used to dedupe the
	// DebuggerInt.onException(AussomException, Environment) call
	// so the hook fires once per logical exception rather than
	// once per stack frame the value passes through. Volatile so a
	// value shared across threads dedupes there too. See
	// design/debugging-interface-design.md.
	private volatile boolean debuggerSeen = false;

	public AussomException() {
		this.setType(cType.cException);

		// Setup linkage for string object.
		this.setExternObject(this);
		// No class definition is bound here. A primitive does not need
		// one to exist, only to dispatch, and dispatch always has an
		// Environment to resolve it from. Binding one at construction
		// would mean reaching for a process-wide global, which is what
		// let one engine's classes leak into another's.
		// See design/multitenancy-safety.md section 7.2.
	}
	
	public AussomException(exType ExType) {
		this();
		this.et = ExType;
	}
	
	public AussomException(String Text) {
		this(exType.exRuntime);
		this.text = Text;
		this.details = Text;
	}

	public void setException(int LineNum, String Id, String Text, String Details, String StackTrace) {
		this.lineNumber = LineNum;
		this.id = Id;
		this.text = Text;
		this.details = Details;
		this.stackTrace = StackTrace;
	}
	
	public void setException(int LineNum, String Id, String Text, String StackTrace) {
		this.lineNumber = LineNum;
		this.id = Id;
		this.text = Text;
		this.details = Text;
		this.stackTrace = StackTrace;
	}
	
	public exType getEt() {
		return et;
	}

	public void setEt(exType et) {
		this.et = et;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	public String getStackTrace() {
		return stackTrace;
	}

	public void setStackTrace(String stackTrace) {
		this.stackTrace = stackTrace;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}
	
	public String getExceptionTypeString() {
		return this.et.name();
	}

	public boolean isLocalObject() {
		return isLocalObject;
	}

	public void setLocalObject(boolean localObject) {
		isLocalObject = localObject;
	}

	/**
	 * Returns true if this exception was raised by the interpreter to
	 * stop a cancelled program, rather than by a fault in the Aussom
	 * code. Hosts that run scripts under a timeout should treat this
	 * as "stopped" rather than "failed".
	 * @return A boolean with true for a cancellation and false for not.
	 */
	public boolean isCancellation() {
		return this.cancellation;
	}

	/**
	 * Marks this exception as the interpreter's cancellation signal.
	 * Called only by the loop back-edge check in astNode.
	 * @param cancellation is a boolean with the flag value.
	 */
	public void setCancellation(boolean cancellation) {
		this.cancellation = cancellation;
	}

	/**
	 * Returns true if the debugger hook in astNode.eval has
	 * already observed this exception value. Used to dedupe the
	 * DebuggerInt.onException(AussomException, Environment) call
	 * across stack frames the value passes through.
	 * @return A boolean with true for seen and false for not.
	 */
	public boolean isDebuggerSeen() {
		return this.debuggerSeen;
	}

	/**
	 * Sets the debuggerSeen flag. Called by the debugger hook in
	 * astNode.eval the first time the value flows through eval.
	 * @param debuggerSeen the flag value.
	 */
	public void setDebuggerSeen(boolean debuggerSeen) {
		this.debuggerSeen = debuggerSeen;
	}

	/**
	 * Canonical Aussom exception formatter used everywhere user-visible
	 * exception output is produced. Emits the multi-line indented block
	 * with line, type, id, text, details, and stack trace.
	 *
	 * P4: previously stackTraceToString() returned a one-line summary
	 * while toString(int) returned the multi-line form; different code
	 * paths surfaced different shapes for the same error. Both helpers
	 * now route through the same formatter so the user sees one
	 * consistent layout.
	 */
	public String stackTraceToString() {
		return this.toString(0);
	}

	@Override
	public String toString(int Level) {
		String rstr = "";

		rstr += AussomType.getTabs(Level);
		rstr += "line ";
		rstr += this.lineNumber;
		rstr += ": ";
		rstr += "[";
		rstr += this.getType().name();
		rstr += "] ";
		rstr += this.et.name();
		rstr += " Exception\n";

		rstr += AussomType.getTabs(Level + 1);
		rstr += "id: " + this.id + "\n";

		rstr += AussomType.getTabs(Level + 1);
		rstr += "text: " + this.text + "\n";

		rstr += AussomType.getTabs(Level + 1);
		rstr += "details: " + this.details + "\n";

		rstr += AussomType.getTabs(Level + 1);
		rstr += "stackTrace: " + this.stackTrace + "\n";

		return rstr;
	}

	@Override
	public String str() {
		return this.stackTraceToString();
	}
	
	public String str(int Level) {
		return this.str();
	}
	
	public AussomType getLineNumber(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.lineNumber);
	}
	
	public AussomType getExceptionType(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.et.name());
	}
	
	public AussomType getId(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.id);
	}
	
	public AussomType getText(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.text);
	}
	
	public AussomType getDetails(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.details);
	}
	
	public AussomType getStackTrace(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.stackTrace);
	}
}

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

package com.aussom.script;

import javax.script.ScriptException;

import com.aussom.ast.aussomException;
import com.aussom.types.AussomException;

/**
 * Thin specialization of ScriptException that carries the original
 * Aussom-side error so hosts can unwrap getCause() and introspect
 * the Aussom stack trace without scraping the message.
 */
public class AussomScriptException extends ScriptException {
	private static final long serialVersionUID = 1L;

	private final AussomException aussomEx;

	public AussomScriptException(String message) {
		super(message);
		this.aussomEx = null;
	}

	public AussomScriptException(String message, String fileName, int lineNumber) {
		super(message, fileName, lineNumber);
		this.aussomEx = null;
	}

	public AussomScriptException(aussomException cause, String fileName) {
		super(cause.getMessage() == null ? cause.toString() : cause.getMessage(),
			fileName, -1);
		this.aussomEx = null;
		initCause(cause);
	}

	public AussomScriptException(AussomException cause, String fileName) {
		super(cause.getText() == null || cause.getText().isEmpty()
			? cause.stackTraceToString() : cause.getText(),
			fileName, cause.getLineNumber());
		this.aussomEx = cause;
	}

	/**
	 * Returns the underlying AussomException when this exception was
	 * raised from a script-level error. Null when the failure came
	 * from the parser or the marshaller.
	 */
	public AussomException getAussomException() {
		return this.aussomEx;
	}
}

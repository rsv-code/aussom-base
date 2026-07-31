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

/**
 * A single parse-time diagnostic with its source position kept as
 * data rather than folded into a message string.
 *
 * Parse errors have always been reported by printing prose to the
 * console. Tooling that needs the position back -- an LSP server, a
 * debug adapter, a notebook host -- had to recover it with regular
 * expressions over that prose. Engine collects these objects during a
 * parse so those consumers can read integers instead. See
 * design/error-reporting-fix.md.
 *
 * Console output is unchanged; a diagnostic is emitted alongside the
 * existing message, not in place of it.
 *
 * Instances are immutable.
 */
public class ParseDiagnostic {
	/**
	 * Severity for a diagnostic that prevents the parse from
	 * succeeding. Every diagnostic the engine emits today is an
	 * error; the field exists so a future warning-producing site
	 * does not force a signature change.
	 */
	public static final String SEVERITY_ERROR = "error";

	/**
	 * Line and column value used when a diagnostic applies to a file
	 * as a whole and no position is available. Consumers should treat
	 * a zero line as "file-level, position unknown" rather than
	 * pointing at the first line.
	 */
	public static final int NO_POSITION = 0;

	private final String fileName;
	private final int line;
	private final int col;
	private final String severity;
	private final String message;

	/**
	 * Constructs a diagnostic with the error severity.
	 * @param FileName is the file the diagnostic belongs to.
	 * @param Line is the 1-based line number, or NO_POSITION.
	 * @param Col is the 1-based column number, or NO_POSITION.
	 * @param Message is the human-readable description.
	 */
	public ParseDiagnostic(String FileName, int Line, int Col, String Message) {
		this(FileName, Line, Col, SEVERITY_ERROR, Message);
	}

	/**
	 * Constructs a diagnostic with an explicit severity.
	 * @param FileName is the file the diagnostic belongs to.
	 * @param Line is the 1-based line number, or NO_POSITION.
	 * @param Col is the 1-based column number, or NO_POSITION.
	 * @param Severity is the severity string, e.g. SEVERITY_ERROR.
	 * @param Message is the human-readable description.
	 */
	public ParseDiagnostic(String FileName, int Line, int Col, String Severity, String Message) {
		this.fileName = FileName;
		this.line = Line;
		this.col = Col;
		this.severity = Severity;
		this.message = Message;
	}

	/**
	 * Gets the file name the diagnostic belongs to.
	 * @return A String with the file name.
	 */
	public String getFileName() {
		return this.fileName;
	}

	/**
	 * Gets the 1-based line number, or NO_POSITION if the diagnostic
	 * is file-level.
	 * @return An int with the line number.
	 */
	public int getLine() {
		return this.line;
	}

	/**
	 * Gets the 1-based column number, or NO_POSITION if the
	 * diagnostic is file-level.
	 * @return An int with the column number.
	 */
	public int getCol() {
		return this.col;
	}

	/**
	 * Gets the severity string.
	 * @return A String with the severity.
	 */
	public String getSeverity() {
		return this.severity;
	}

	/**
	 * Gets the diagnostic message. The message does not include the
	 * file name or position; those are separate fields.
	 * @return A String with the message.
	 */
	public String getMessage() {
		return this.message;
	}

	/**
	 * Obligatory toString method. Intended for debugging, not for
	 * consumers to parse -- read the fields instead.
	 * @return A String representing the diagnostic.
	 */
	@Override
	public String toString() {
		return this.fileName + " [" + this.line + ":" + this.col + "] "
			+ this.severity + ": " + this.message;
	}
}

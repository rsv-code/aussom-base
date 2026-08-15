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

package com.aussom.stdlib;

/**
 * Thrown out of RegexSubject.charAt when the host cancelled the engine
 * while a regular expression was running.
 *
 * The match is abandoned where it stood. The ARegex or AussomString
 * method that started it converts this into the interpreter's standard
 * cancellation exception, so a cancelled regex reports the same id and
 * carries the same "a script cannot catch this" rule as a cancelled
 * loop. See Engine.cancelledException.
 *
 * A pause produces no throwable at all: RegexSubject blocks instead,
 * and the match continues when the engine is resumed.
 *
 * @author austin
 */
public class RegexCancelledError extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public RegexCancelledError() {
		super("Execution cancelled while running a regular expression.");
	}
}

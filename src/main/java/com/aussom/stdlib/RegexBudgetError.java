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
 * Thrown out of RegexSubject.charAt when a regular expression has read
 * more of its subject than its budget allows.
 *
 * A throw is the only way out: the code that notices is running inside
 * Matcher.find(), which has no way to return a value early. It travels
 * straight out of the matcher, because java.util.regex does not catch
 * exceptions raised by the subject it was handed, and the ARegex or
 * AussomString method that started the match converts it into an
 * ordinary Aussom exception.
 *
 * Not part of the Aussom-facing API; a script sees
 * REGEX_BUDGET_EXCEEDED instead.
 *
 * @author austin
 */
public class RegexBudgetError extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final long steps;
	private final long budget;

	/**
	 * @param Steps is how many character reads had been made.
	 * @param Budget is the budget that was exceeded.
	 */
	public RegexBudgetError(long Steps, long Budget) {
		super("Regular expression step budget of " + Budget
			+ " exceeded after " + Steps + " character reads.");
		this.steps = Steps;
		this.budget = Budget;
	}

	/** @return A long with the character reads made. */
	public long getSteps() { return this.steps; }

	/** @return A long with the budget that was exceeded. */
	public long getBudget() { return this.budget; }
}

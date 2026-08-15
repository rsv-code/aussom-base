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

import com.aussom.ControlState;
import com.aussom.Engine;
import com.aussom.Environment;
import com.aussom.Limits;
import com.aussom.types.AussomException;
import com.aussom.types.AussomException.exType;

/**
 * The subject handed to java.util.regex, wrapped so the interpreter can
 * see and control the work the matcher does.
 *
 * <h2>Why this exists</h2>
 *
 * java.util.regex backtracks. For a pattern like <code>(a+)+\1b</code>
 * it tries one way of splitting the input, fails, backs up and tries
 * another, and the number of tries doubles with every extra character.
 * Measured on a current JVM: 20 characters take 0.1 seconds, 26 take
 * 2.25 seconds, and 30 take just under a minute. Both the pattern and
 * the subject come from the script.
 *
 * All of that happens inside one call to Matcher.find(), which never
 * looks at a clock and never checks whether the thread was interrupted.
 * So there is no ordinary place for the engine to step in.
 *
 * <h2>The hook</h2>
 *
 * Pattern.matcher() does not take a String. It takes a CharSequence,
 * whose central method is charAt(int). So the subject can be an object
 * of ours, and <b>every time the matcher wants a character it calls our
 * method</b>. No timer and no extra thread: the matcher itself calls
 * back into interpreter code millions of times a second while it works.
 *
 * Character reads track the work closely. The cases above made 4.3
 * million, 276 million and several billion reads, in step with their run
 * times, so counting reads meters the match.
 *
 * <h2>What this class does with that</h2>
 *
 * Two things, on every read:
 *
 * <ul>
 * <li><b>Counts against a budget.</b> A plain field compare, so it
 *     happens on every read. Past the budget it throws
 *     RegexBudgetError, which ARegex turns into a normal Aussom
 *     exception. A budget of 1,000,000 stops the 30-character case
 *     above in 12 milliseconds instead of 59 seconds.</li>
 * <li><b>Checks the engine's control state, in batches.</b> That state
 *     is volatile, and reading a volatile on every character would put
 *     a memory barrier in the hottest loop the interpreter has, so it
 *     is read once per 65,536 characters, which is roughly half a
 *     millisecond of matcher work. A cancel throws
 *     RegexCancelledError; a pause blocks here and the match carries on
 *     from the same spot when the engine is resumed.</li>
 * </ul>
 *
 * A thread parked here counts as stopped for Engine.awaitPaused. That
 * looks like an exception to the rule that a thread inside an extern
 * call has not stopped, and it is not: the rule is about ownership.
 * A thread blocked on a socket sits in code the engine does not
 * control, while this is the engine's own checkpoint that happens to
 * live inside an extern call.
 *
 * Cost when nothing is wrong is not measurable: 20,000 matches over a
 * 240 KB subject took 9 milliseconds wrapped against 10 unwrapped.
 *
 * <p>See design/security-evaluation-f4-f5.md section 4.2.
 *
 * @author austin
 */
public final class RegexSubject implements CharSequence {
	/**
	 * The engine's control state is read once per this many character
	 * reads. A power of two minus one so the test is a mask.
	 */
	private static final long CONTROL_CHECK_MASK = 0xFFFFL;   // every 65,536

	private final CharSequence text;
	private final Engine engine;
	private final long budget;      // 0 means unlimited
	private long steps = 0L;

	/**
	 * Wraps a subject for one regex operation.
	 * @param Text is the subject to match against.
	 * @param Eng is the engine to consult for pause and cancel, may be null.
	 * @param Budget is the maximum character reads, 0 for unlimited.
	 */
	public RegexSubject(CharSequence Text, Engine Eng, long Budget) {
		this.text = Text;
		this.engine = Eng;
		this.budget = Budget;
	}

	/**
	 * Wraps a subject using the engine's configured regex budget.
	 *
	 * The effective budget is the configured number plus the length of
	 * the subject. The length term is not padding: a plain scan of the
	 * input costs about one read per character (measured at 239,999
	 * reads for a 240 KB subject), so charging a long subject against
	 * the same fixed allowance as a short one would refuse ordinary
	 * code on large input. The configured number is the slack for
	 * backtracking on top of that honest scan.
	 *
	 * @param env is the current Environment, may be null.
	 * @param Text is the subject to match against.
	 * @return A RegexSubject ready to hand to Pattern.matcher.
	 */
	public static RegexSubject of(Environment env, CharSequence Text) {
		Engine eng = null;
		if (env != null) eng = env.getEngine();
		long budget = 0L;
		if (eng != null) {
			long configured = eng.getLimits().getRegexSteps();
			if (configured > 0L) {
				long len = 0L;
				if (Text != null) len = Text.length();
				budget = configured + len;
			}
		}
		return new RegexSubject(Text, eng, budget);
	}

	/**
	 * Character reads made so far. What the budget is measured in.
	 * @return A long with the number of reads.
	 */
	public long getSteps() {
		return this.steps;
	}

	/**
	 * The budget in force for this operation, 0 for unlimited.
	 * @return A long with the budget.
	 */
	public long getBudget() {
		return this.budget;
	}

	@Override
	public char charAt(int Index) {
		this.steps++;

		// Every read: plain field compare, no memory barrier.
		if (this.budget > 0L && this.steps > this.budget) {
			throw new RegexBudgetError(this.steps, this.budget);
		}

		// Every 65,536 reads: one volatile read, then off the fast path.
		// PAUSED blocks and returns here; CANCELLED throws.
		if ((this.steps & CONTROL_CHECK_MASK) == 0L && this.engine != null
				&& this.engine.getControlState() != ControlState.RUNNING) {
			if (this.engine.awaitResumeOrCancel()) {
				throw new RegexCancelledError();
			}
		}

		return this.text.charAt(Index);
	}

	@Override
	public int length() {
		return this.text.length();
	}

	@Override
	public CharSequence subSequence(int Start, int End) {
		// Unwrapped on purpose: Matcher.group builds its result through
		// subSequence, and handing back a result should not spend budget.
		return this.text.subSequence(Start, End);
	}

	@Override
	public String toString() {
		return this.text.toString();
	}

	/**
	 * Converts a budget breach into the Aussom exception a script sees.
	 * @param env is the current Environment, may be null.
	 * @param e is the error thrown out of the matcher.
	 * @param Pattern is the pattern that ran, for the message.
	 * @return An AussomException with id REGEX_BUDGET_EXCEEDED.
	 */
	public static AussomException toException(Environment env, RegexBudgetError e,
			String Pattern) {
		String trace = "";
		if (env != null && env.getCallStack() != null) {
			trace = env.getCallStack().getStackTrace();
		}
		AussomException ex = new AussomException(exType.exRuntime);
		String text = "Regular expression step budget of " + e.getBudget()
			+ " exceeded matching '" + Pattern + "'. The pattern is backtracking "
			+ "far more than the input size justifies.";
		ex.setException(-1, REGEX_BUDGET_EXCEEDED_ID, text,
			text + " Raise the security property '"
				+ Limits.REGEX_STEPS_PROP + "' if this much work is "
				+ "intended, or rewrite the pattern to backtrack less.",
			trace);
		return ex;
	}

	/** Exception id reported when a regex runs past its step budget. */
	public static final String REGEX_BUDGET_EXCEEDED_ID = "REGEX_BUDGET_EXCEEDED";
}

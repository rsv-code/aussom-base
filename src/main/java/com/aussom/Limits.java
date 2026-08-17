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
 * Numeric runtime settings for one engine, read from its security
 * manager.
 *
 * <p>Four of them, and each bounds something the JVM will not bound for
 * you:
 *
 * <ul>
 * <li><b>Call depth</b> (aussom.limit.call.depth, default 1000). How deep
 *     Aussom calls may nest. Without it, runaway recursion ends the
 *     interpreter thread with a StackOverflowError that no script and no
 *     ordinary host catch.</li>
 * <li><b>Regex steps</b> (aussom.limit.regex.steps, default 0 for no
 *     budget). How many characters of its subject one regular expression
 *     may read. Without it, an eight-character pattern can burn a core
 *     for a minute on a thirty-character subject, and java.util.regex
 *     offers no timeout and does not check thread interrupts.</li>
 * <li><b>Sleep slice</b> (aussom.limit.sleep.slice, default 50 ms). How
 *     long a sleeping program may run before the interpreter reads the
 *     engine's control state again, which is the ceiling on how long a
 *     pause or a cancel waits to take effect. 0 turns slicing off and
 *     restores one uninterruptible wait.</li>
 * <li><b>Source bytes</b> (aussom.limit.source.bytes, default 0 for no
 *     limit). The largest source file the engine will parse, checked
 *     against the file's length before it is read. Source is the one
 *     parse cost worth bounding: a file is read whole into memory before
 *     the parser sees a token, and the definitions it becomes measure out
 *     at roughly 37 times the source. See
 *     design/security-evaluation-g1-g3.md.</li>
 * </ul>
 *
 * <p><b>Why there are no per-value size caps here.</b> Earlier drafts
 * capped the length of one string, the elements of one list or map, and
 * the bytes of one buffer. All four were removed, because a cap on one
 * value bounds neither memory nor anything else a host can reason about:
 *
 * <ul>
 * <li>Element count is not memory. Five million ints hold about 160 MB;
 *     a thousand elements holding megabyte strings hold a gigabyte. There
 *     is no value a host could derive from a memory budget.</li>
 * <li>A cap on one value does not bound many values. A script under any
 *     per-string cap can hold thousands of strings.</li>
 * <li>The failure a size cap looks like it prevents is not the dangerous
 *     one. Measured: a single allocation larger than the heap is refused
 *     cleanly and the process keeps working, while sustained growth that
 *     fills the heap is what escapes and breaks other tenants. Retention
 *     is the risk, and retention is what Engine.getAllocatedBytes,
 *     Engine.measureRetainedFootprint and a host deadline address.</li>
 * </ul>
 *
 * <p>Argument validation is a different matter and stayed: ABuffer still
 * refuses a negative size or one larger than a Java array can address,
 * because narrowing a 64-bit size into an int used to allocate something
 * other than what the script asked for and report nothing.
 *
 * <p>A Limits object is an immutable snapshot. Engine builds one in its
 * constructor and again at the start of each program, so a host that
 * rewrites policy between runs is honored without the interpreter paying
 * for a map lookup on every operation.
 *
 * <p>See design/security-evaluation-f4-f5.md section 5.
 *
 * @author austin
 */
public class Limits {
	/** Security property names. All are read as numbers. */
	public static final String CALL_DEPTH_PROP  = "aussom.limit.call.depth";
	public static final String REGEX_STEPS_PROP = "aussom.limit.regex.steps";
	public static final String SLEEP_SLICE_PROP = "aussom.limit.sleep.slice";
	public static final String SOURCE_BYTES_PROP = "aussom.limit.source.bytes";

	/**
	 * Call depth used when the security manager says nothing. Chosen
	 * above what a program can reach on a default 1 MB thread stack,
	 * which measures out at roughly 550 Aussom frames, so switching
	 * this on cannot refuse a program that runs today. A host that
	 * gives the interpreter a bigger stack, or runs it on a virtual
	 * thread, gets this as the operative limit.
	 */
	public static final long DEFAULT_CALL_DEPTH = 1000L;

	/**
	 * Milliseconds a sleeping program may run before the interpreter
	 * looks at the engine's control state again, used when the security
	 * manager says nothing.
	 *
	 * This is the one setting here where 0 does not mean "no limit" but
	 * "no slicing": sys.sleep() then waits in a single call, and a pause
	 * or a cancel cannot reach the program until the whole sleep is over.
	 * The default keeps a sleeping tenant reclaimable within a twentieth
	 * of a second, which costs one wake-up per slice and nothing else.
	 */
	public static final long DEFAULT_SLEEP_SLICE_MS = 50L;

	private final long callDepth;
	private final long regexSteps;
	private final long sleepSliceMs;
	private final long sourceBytes;

	/**
	 * Builds a snapshot with no limits at all and the default call
	 * depth. Used when an engine has no security manager to read.
	 */
	public Limits() {
		this.callDepth = DEFAULT_CALL_DEPTH;
		this.regexSteps = 0L;
		this.sleepSliceMs = DEFAULT_SLEEP_SLICE_MS;
		this.sourceBytes = 0L;
	}

	/**
	 * Reads every limit from the supplied security manager. A key that
	 * is missing, null, or not stored as an integer falls back to the
	 * default for that limit, which is 0 (no limit) for everything except
	 * the call depth. A negative value is treated the same way, since a
	 * negative limit has no meaning.
	 * @param SecMan is the security manager to read, may be null.
	 */
	public Limits(SecurityManagerInt SecMan) {
		this.callDepth    = read(SecMan, CALL_DEPTH_PROP, DEFAULT_CALL_DEPTH);
		this.regexSteps   = read(SecMan, REGEX_STEPS_PROP, 0L);
		this.sleepSliceMs = read(SecMan, SLEEP_SLICE_PROP, DEFAULT_SLEEP_SLICE_MS);
		this.sourceBytes  = read(SecMan, SOURCE_BYTES_PROP, 0L);
	}

	/**
	 * Copy constructor with one setting replaced. For a caller that has a
	 * snapshot in hand and wants the same one with a single number
	 * changed.
	 *
	 * Note that this builds a value; it does not change any engine's
	 * policy. Limits come from the security manager, which is where a
	 * host sets them. See Engine.refreshLimits.
	 *
	 * @param Other is the snapshot to copy.
	 * @param PropName is the setting to replace.
	 * @param Value is the new value.
	 */
	public Limits(Limits Other, String PropName, long Value) {
		long v = Value;
		if (v < 0L) v = 0L;
		this.callDepth    = pick(PropName, CALL_DEPTH_PROP,  v, Other.callDepth);
		this.regexSteps   = pick(PropName, REGEX_STEPS_PROP, v, Other.regexSteps);
		this.sleepSliceMs = pick(PropName, SLEEP_SLICE_PROP, v, Other.sleepSliceMs);
		this.sourceBytes  = pick(PropName, SOURCE_BYTES_PROP, v, Other.sourceBytes);
	}

	/**
	 * Reads one setting from policy. The typed read and its default
	 * belong to the security manager (see
	 * SecurityManagerInt.getPropertyInt), which answers with the default
	 * for anything that is not stored as an integer; it does not parse
	 * strings. What is left here is the one rule specific to a limit,
	 * that a negative value has no meaning and falls back to the default
	 * rather than being honored.
	 * @param SecMan is the security manager to read.
	 * @param PropName is the property name.
	 * @param Dflt is the value to use when the property is unusable.
	 * @return A long with the setting value.
	 */
	private static long read(SecurityManagerInt SecMan, String PropName, long Dflt) {
		long v = SecMan.getPropertyInt(PropName, (int) Dflt);
		if (v < 0L) return Dflt;
		return v;
	}

	private static long pick(String PropName, String Match, long Value, long Current) {
		if (PropName.equals(Match)) {
			return Value;
		}
		return Current;
	}

	/** @return Maximum Aussom call depth, 0 for no limit. */
	public long getCallDepth() { return this.callDepth; }

	/** @return Maximum regex subject reads per call, 0 for no budget. */
	public long getRegexSteps() { return this.regexSteps; }

	/**
	 * @return Milliseconds a sleeping program may run between control
	 * checks, 0 to sleep in a single uninterruptible call.
	 *
	 * This bounds how long a host waits for a pause or a cancel to reach
	 * a program sitting in sys.sleep(). It is here rather than as a
	 * constant in the standard library because it is a control setting,
	 * and control settings belong in policy where a host can see and
	 * change them with the rest.
	 */
	public long getSleepSliceMs() { return this.sleepSliceMs; }

	/**
	 * @return Largest source file the engine will parse in bytes, 0 for
	 * no limit.
	 *
	 * Checked against the file length before the file is read, so an
	 * oversized source is refused rather than loaded and then rejected.
	 * It bounds files only: source handed to the engine as a string, by
	 * a host or through JSR 223, has already been measured by whoever
	 * built the string. The standard library is loaded from the jar as a
	 * string for that reason, so this cannot refuse lang.aus and brick
	 * an engine at construction.
	 */
	public long getSourceBytes() { return this.sourceBytes; }

}

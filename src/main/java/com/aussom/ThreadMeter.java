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

import java.lang.management.ManagementFactory;

import com.sun.management.ThreadMXBean;

/**
 * Reads the JVM's per-thread CPU and allocation counters.
 *
 * <p>The JVM already counts, for every thread, how many nanoseconds of
 * CPU it has used and how many bytes it has allocated. Both counters
 * are cheap to read (measured at roughly 100 to 260 nanoseconds a
 * read) and can be read for any thread from any other thread, which is
 * what lets an engine report its own usage while it runs.
 *
 * <p>Two properties of those counters shape everything built on them.
 * They are <b>cumulative per thread</b>, so a pooled thread carries
 * whatever ran on it earlier, and they return <b>-1 once the thread
 * has terminated</b>. So a per-engine total cannot be read off a
 * thread on demand: it has to be recorded as a baseline when the
 * thread starts running engine code and banked as a difference before
 * that thread goes away. Engine.enterInterpreterThread does exactly
 * that.
 *
 * <p>Everything here degrades to -1 when the JVM does not offer the
 * counters, so a caller can tell "no CPU used" from "no accounting
 * available".
 *
 * <p>See design/security-evaluation-f4-f5.md sections 5.1 and 5.5.
 *
 * @author austin
 */
public final class ThreadMeter {
	/**
	 * The extended thread bean, or null when this JVM does not provide
	 * it. Resolved without naming java.lang.management.ThreadMXBean:
	 * the factory hands back the base interface, and the extended one
	 * is only usable if the instance actually implements it.
	 */
	private static final ThreadMXBean BEAN = resolveBean();

	/** True when per-thread CPU time can be read. */
	private static final boolean CPU_OK = resolveCpu();

	/** True when per-thread allocated bytes can be read. */
	private static final boolean ALLOC_OK = resolveAlloc();

	private ThreadMeter() { }

	private static ThreadMXBean resolveBean() {
		try {
			Object bean = ManagementFactory.getThreadMXBean();
			if (bean instanceof ThreadMXBean) {
				return (ThreadMXBean) bean;
			}
		} catch (Throwable t) {
			// A JVM without the management modules. Accounting is off.
		}
		return null;
	}

	private static boolean resolveCpu() {
		if (BEAN == null) return false;
		try {
			if (!BEAN.isThreadCpuTimeSupported()) return false;
			if (!BEAN.isThreadCpuTimeEnabled()) {
				BEAN.setThreadCpuTimeEnabled(true);
			}
			return BEAN.isThreadCpuTimeEnabled();
		} catch (Throwable t) {
			return false;
		}
	}

	private static boolean resolveAlloc() {
		if (BEAN == null) return false;
		try {
			if (!BEAN.isThreadAllocatedMemorySupported()) return false;
			if (!BEAN.isThreadAllocatedMemoryEnabled()) {
				BEAN.setThreadAllocatedMemoryEnabled(true);
			}
			return BEAN.isThreadAllocatedMemoryEnabled();
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * True when this JVM can report per-thread CPU time.
	 * @return A boolean with true for available.
	 */
	public static boolean isCpuAvailable() { return CPU_OK; }

	/**
	 * True when this JVM can report per-thread allocated bytes.
	 * @return A boolean with true for available.
	 */
	public static boolean isAllocAvailable() { return ALLOC_OK; }

	/**
	 * CPU nanoseconds the given thread has used since it started.
	 * @param ThreadId is the thread id to read.
	 * @return A long with CPU nanoseconds, or -1 when unavailable or
	 * the thread has terminated.
	 */
	public static long cpuNanos(long ThreadId) {
		if (!CPU_OK) return -1L;
		try {
			return BEAN.getThreadCpuTime(ThreadId);
		} catch (Throwable t) {
			return -1L;
		}
	}

	/**
	 * Bytes the given thread has allocated since it started.
	 * @param ThreadId is the thread id to read.
	 * @return A long with bytes allocated, or -1 when unavailable or
	 * the thread has terminated.
	 */
	public static long allocatedBytes(long ThreadId) {
		if (!ALLOC_OK) return -1L;
		try {
			return BEAN.getThreadAllocatedBytes(ThreadId);
		} catch (Throwable t) {
			return -1L;
		}
	}
}

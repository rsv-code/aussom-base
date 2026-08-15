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
 * One thread's registration with an engine, for the length of one
 * program body.
 *
 * <p>Two jobs. It tells the engine which threads are running its code,
 * which is what lets isFullyPaused() know when everything has actually
 * stopped. And it records the thread's CPU and allocation counters on
 * entry so the difference can be banked on the way out.
 *
 * <p>The banking step is required rather than tidy. The JVM's
 * per-thread counters are cumulative, so a pooled thread carries
 * whatever ran on it earlier, and they read -1 once the thread has
 * terminated. Reading them at the end of the program body is the last
 * chance to attribute the work correctly.
 *
 * <p>Use it in a try-with-resources, or in a try/finally:
 *
 * <pre>
 * try (ThreadScope scope = eng.enterInterpreterThread()) {
 *     ... run the program ...
 * }
 * </pre>
 *
 * <p>See design/security-evaluation-f4-f5.md section 5.5.
 *
 * @author austin
 */
public class ThreadScope implements AutoCloseable {
	private final Engine engine;
	private final long threadId;
	private volatile long cpuBaseline;
	private volatile long allocBaseline;
	private volatile boolean closed = false;

	/**
	 * A scope that does nothing, handed back when the calling thread is
	 * already registered with this engine. The outer scope owns the
	 * accounting and the registration; closing an inner one must not
	 * unregister the thread out from under it.
	 * @return A ThreadScope whose close() is a no-op.
	 */
	static ThreadScope nested() {
		return new ThreadScope();
	}

	/** Builds the no-op scope. See nested(). */
	private ThreadScope() {
		this.engine = null;
		this.threadId = Thread.currentThread().getId();
		this.cpuBaseline = -1L;
		this.allocBaseline = -1L;
		this.closed = true;
	}

	/**
	 * Registers the calling thread. Built by
	 * Engine.enterInterpreterThread rather than directly.
	 * @param Eng is the engine this thread is running.
	 */
	ThreadScope(Engine Eng) {
		this.engine = Eng;
		this.threadId = Thread.currentThread().getId();
		this.cpuBaseline = ThreadMeter.cpuNanos(this.threadId);
		this.allocBaseline = ThreadMeter.allocatedBytes(this.threadId);
	}

	/**
	 * The thread this scope belongs to.
	 * @return A long with the thread id.
	 */
	public long getThreadId() {
		return this.threadId;
	}

	/**
	 * CPU nanoseconds this thread has used since it entered. Returns 0
	 * when the counters are unavailable or the thread is gone, so a
	 * live read never subtracts a -1 into a negative total.
	 * @return A long with CPU nanoseconds since entry.
	 */
	long liveCpuNanos() {
		long now = ThreadMeter.cpuNanos(this.threadId);
		if (now < 0L || this.cpuBaseline < 0L) return 0L;
		long delta = now - this.cpuBaseline;
		if (delta < 0L) return 0L;
		return delta;
	}

	/**
	 * Bytes this thread has allocated since it entered.
	 * @return A long with bytes allocated since entry.
	 */
	long liveAllocBytes() {
		long now = ThreadMeter.allocatedBytes(this.threadId);
		if (now < 0L || this.allocBaseline < 0L) return 0L;
		long delta = now - this.allocBaseline;
		if (delta < 0L) return 0L;
		return delta;
	}

	/**
	 * Moves the baseline to now, discarding usage recorded so far.
	 * Called by Engine.resetAccounting so a per-request meter does not
	 * pick up work from before the reset when this thread finishes.
	 */
	void rebaseline() {
		this.cpuBaseline = ThreadMeter.cpuNanos(this.threadId);
		this.allocBaseline = ThreadMeter.allocatedBytes(this.threadId);
	}

	/**
	 * Banks this thread's usage and unregisters it. Safe to call more
	 * than once; only the first call counts.
	 */
	@Override
	public void close() {
		if (this.closed) return;
		this.closed = true;
		this.engine.bankThreadUsage(this.liveCpuNanos(), this.liveAllocBytes());
		this.engine.leaveInterpreterThread(this.threadId);
	}
}

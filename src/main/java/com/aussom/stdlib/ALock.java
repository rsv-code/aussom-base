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

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.aussom.Environment;
import com.aussom.types.AussomCallback;
import com.aussom.types.AussomException;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomList;
import com.aussom.types.AussomType;

/**
 * Aussom Lock runtime. Wraps
 * {@link java.util.concurrent.locks.ReentrantLock} but does NOT
 * expose lock()/unlock() as separate script calls: a script
 * exception between the two would leak the lock permanently. The
 * only way to hold the lock is the scoped callback form withLock,
 * where acquire, run, and guaranteed release happen inside one
 * extern call. The wait timeout turns a deadlock into a visible
 * error instead of a frozen worker thread. The escape hatch when no
 * atomic primitive fits; see design/aussom-concurrency.md.
 */
public class ALock {
	private final ReentrantLock lock = new ReentrantLock();

	public ALock() { }

	public AussomType withLock(Environment env, ArrayList<AussomType> args) {
		if (!(args.get(0) instanceof AussomCallback)) {
			return new AussomException("Lock.withLock(): expecting a callback as the first argument.");
		}
		AussomCallback cb = (AussomCallback) args.get(0);
		long waitMs = ((AussomInt) args.get(1)).getValue();
		try {
			if (!this.lock.tryLock(waitMs, TimeUnit.MILLISECONDS)) {
				return new AussomException("Lock.withLock(): timed out after " + waitMs
					+ " ms waiting for the lock. Possible deadlock or a slow critical section.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new AussomException("Lock.withLock(): interrupted while waiting for the lock.");
		}
		try {
			return cb.call(env, new AussomList());
		} finally {
			this.lock.unlock();
		}
	}
}

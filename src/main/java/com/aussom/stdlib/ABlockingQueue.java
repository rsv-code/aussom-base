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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.aussom.Environment;
import com.aussom.types.AussomBool;
import com.aussom.types.AussomException;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomNull;
import com.aussom.types.AussomType;

/**
 * Aussom BlockingQueue runtime. Wraps
 * {@link java.util.concurrent.LinkedBlockingQueue} holding Aussom
 * values. The bridge between threads with backpressure built in:
 * many producers offer() work items and one dedicated worker
 * take()s them, owning the non-thread-safe resources. Values cross
 * threads through the queue with the queue's own happens-before
 * guarantee; treat a handed-off value as given away. null cannot be
 * queued because null is poll()'s "empty" return. Each method call
 * is atomic; a sequence of method calls is not. See
 * design/aussom-concurrency.md.
 */
public class ABlockingQueue {
	private LinkedBlockingQueue<AussomType> queue = new LinkedBlockingQueue<AussomType>();

	public ABlockingQueue() { }

	public AussomType newBlockingQueue(Environment env, ArrayList<AussomType> args) {
		long capacity = ((AussomInt) args.get(0)).getValue();
		if (capacity > 0) {
			this.queue = new LinkedBlockingQueue<AussomType>((int) capacity);
		}
		return env.getClassInstance();
	}

	public AussomType offer(Environment env, ArrayList<AussomType> args) {
		AussomType val = args.get(0);
		if (val.isNull()) {
			return new AussomException("BlockingQueue.offer(): null values cannot be queued.");
		}
		long timeoutMs = ((AussomInt) args.get(1)).getValue();
		try {
			if (timeoutMs <= 0) {
				return new AussomBool(this.queue.offer(val));
			}
			return new AussomBool(this.queue.offer(val, timeoutMs, TimeUnit.MILLISECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new AussomException("BlockingQueue.offer(): interrupted while waiting for space.");
		}
	}

	public AussomType poll(Environment env, ArrayList<AussomType> args) {
		long timeoutMs = ((AussomInt) args.get(0)).getValue();
		AussomType ret;
		try {
			if (timeoutMs <= 0) {
				ret = this.queue.poll();
			} else {
				ret = this.queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new AussomException("BlockingQueue.poll(): interrupted while waiting for a value.");
		}
		if (ret == null) {
			return new AussomNull();
		}
		return ret;
	}

	public AussomType put(Environment env, ArrayList<AussomType> args) {
		AussomType val = args.get(0);
		if (val.isNull()) {
			return new AussomException("BlockingQueue.put(): null values cannot be queued.");
		}
		try {
			this.queue.put(val);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new AussomException("BlockingQueue.put(): interrupted while waiting for space.");
		}
		return env.getClassInstance();
	}

	public AussomType take(Environment env, ArrayList<AussomType> args) {
		try {
			return this.queue.take();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new AussomException("BlockingQueue.take(): interrupted while waiting for a value.");
		}
	}

	public AussomType size(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.queue.size());
	}

	public AussomType clear(Environment env, ArrayList<AussomType> args) {
		this.queue.clear();
		return env.getClassInstance();
	}
}

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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.aussom.Environment;
import com.aussom.types.AussomBool;
import com.aussom.types.AussomException;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomType;

/**
 * Aussom Semaphore runtime. Wraps
 * {@link java.util.concurrent.Semaphore}. The use case is
 * throttling: at most N concurrent holders of a resource. There is
 * deliberately no timeout-free acquire, so a misbehaving script
 * cannot park a host worker thread forever; tryAcquire(0) is the
 * non-blocking form. Each method call is atomic; a sequence of
 * method calls is not. See design/aussom-concurrency.md.
 */
public class ASemaphore {
	private Semaphore sem = new Semaphore(1);

	public ASemaphore() { }

	public AussomType newSemaphore(Environment env, ArrayList<AussomType> args) {
		this.sem = new Semaphore((int) ((AussomInt) args.get(0)).getValue());
		return env.getClassInstance();
	}

	public AussomType tryAcquire(Environment env, ArrayList<AussomType> args) {
		long timeoutMs = ((AussomInt) args.get(0)).getValue();
		try {
			if (timeoutMs <= 0) {
				return new AussomBool(this.sem.tryAcquire());
			}
			return new AussomBool(this.sem.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new AussomException("Semaphore.tryAcquire(): interrupted while waiting for a permit.");
		}
	}

	public AussomType release(Environment env, ArrayList<AussomType> args) {
		this.sem.release();
		return env.getClassInstance();
	}

	public AussomType availablePermits(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.sem.availablePermits());
	}
}

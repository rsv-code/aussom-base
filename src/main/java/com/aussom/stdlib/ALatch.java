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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.aussom.Environment;
import com.aussom.types.AussomBool;
import com.aussom.types.AussomException;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomType;

/**
 * Aussom Latch runtime. Wraps
 * {@link java.util.concurrent.CountDownLatch}. The use case is one
 * thread waiting until N events have happened (N workers finished,
 * N messages arrived). await always takes a timeout so a script
 * cannot park a host worker thread forever. Each method call is
 * atomic; a sequence of method calls is not. See
 * design/aussom-concurrency.md.
 */
public class ALatch {
	private CountDownLatch latch = new CountDownLatch(1);

	public ALatch() { }

	public AussomType newLatch(Environment env, ArrayList<AussomType> args) {
		this.latch = new CountDownLatch((int) ((AussomInt) args.get(0)).getValue());
		return env.getClassInstance();
	}

	public AussomType countDown(Environment env, ArrayList<AussomType> args) {
		this.latch.countDown();
		return env.getClassInstance();
	}

	public AussomType await(Environment env, ArrayList<AussomType> args) {
		long timeoutMs = ((AussomInt) args.get(0)).getValue();
		try {
			return new AussomBool(this.latch.await(timeoutMs, TimeUnit.MILLISECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new AussomException("Latch.await(): interrupted while waiting.");
		}
	}

	public AussomType getCount(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.latch.getCount());
	}
}

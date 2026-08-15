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
 * What a running Aussom program should do when it reaches its next
 * checkpoint. The engine holds one of these in a volatile field, so
 * the common case costs a single reference read and a compare.
 *
 * Checkpoints are the loop back edges, every Aussom function call,
 * every batch of regex subject reads, and every sleep slice. See the
 * "Control: cancel, pause and resume" section of Engine and
 * design/security-evaluation-f4-f5.md.
 *
 * @author austin
 */
public enum ControlState {
	/** Keep going. The state a program runs in. */
	RUNNING,

	/**
	 * Stop at the next checkpoint and block there until the state
	 * becomes RUNNING or CANCELLED. The program keeps its stack, its
	 * locals and its data, so resuming continues where it stopped.
	 */
	PAUSED,

	/**
	 * Stop at the next checkpoint and unwind with the cancellation
	 * exception. Outranks PAUSED: a paused program that is cancelled
	 * ends without having to be resumed first.
	 */
	CANCELLED
}

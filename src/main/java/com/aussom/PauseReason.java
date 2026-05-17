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
 * Reason the interpreter is calling DebuggerInt.onPause. The two
 * values correspond to the two pre-eval pause paths the engine
 * knows about. Implementations that want to track richer intent
 * (external pause, step-over hit target, step-into landed, etc.)
 * do so internally - the engine's job is only to report which of
 * the two paths fired.
 *
 * See design/debugging-interface-design.md section 6.
 */
public enum PauseReason {
	/** The current node's breakpoint flag was true. */
	BREAKPOINT,
	/** DebuggerInt.shouldPauseForStep returned true. */
	STEP
}

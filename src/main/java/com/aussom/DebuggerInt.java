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

import com.aussom.ast.astNode;
import com.aussom.ast.aussomException;
import com.aussom.types.AussomException;

/**
 * Java interface that an external debugger implements to drive
 * an Aussom interpreter through breakpoints, stepping, and
 * exception inspection.
 *
 * The interpreter calls into the registered DebuggerInt from a
 * single hook point at the top of astNode.eval. The hook is
 * gated by Engine.isDebugMode() so this interface is only
 * exercised when debugging is active.
 *
 * See design/debugging-interface-design.md for the full design.
 */
public interface DebuggerInt {

	/**
	 * Called when the interpreter has decided to pause on the
	 * given node. The implementation is expected to block the
	 * calling thread until the debugger control thread issues a
	 * continue, step, or terminate command.
	 *
	 * Inside this call the implementation has full access to:
	 *   - node                     -- the AST node about to be evaluated
	 *   - env.getCallStack()       -- the call stack
	 *   - env.getLocals()          -- the locals (Members)
	 *   - env.getClassInstance()   -- the receiver (this)
	 *   - Thread.currentThread()   -- the paused thread
	 *
	 * Implementations typically:
	 *   1. Record per-thread paused state.
	 *   2. Notify the debugger UI (DAP "stopped" event).
	 *   3. Block on a per-thread Semaphore / CountDownLatch.
	 *   4. Return when the control thread releases the lock.
	 *
	 * @param node The AST node the interpreter is about to evaluate.
	 * @param env The current Environment (class instance, locals, callstack, curObj).
	 * @param reason Why the interpreter is pausing.
	 * @throws aussomException if the implementation needs to abort.
	 */
	void onPause(astNode node, Environment env, PauseReason reason)
		throws aussomException;

	/**
	 * Called on every eval when engine.debugMode is true and the
	 * current node's breakpoint flag is false. Returns true if the
	 * interpreter should call onPause for this node because the
	 * current thread is in a stepping state (step over, step into,
	 * step out, or external pause request).
	 *
	 * Hot path: must return quickly. The default (no step pending)
	 * is typically a single ThreadLocal lookup returning false.
	 * The interface deliberately keeps the step semantics opaque
	 * - aussom-base does not know about step modes; that logic
	 * lives in the implementation.
	 *
	 * @param node The AST node the interpreter is about to evaluate.
	 * @param env The current Environment.
	 * @return true to request a STEP pause, false to continue.
	 */
	boolean shouldPauseForStep(astNode node, Environment env);

	/**
	 * Called the first time an AussomException VALUE flows out of
	 * an eval (the value-form error path: ret.isEx() is true).
	 * Fires once per exception, not once per stack frame the value
	 * passes through. The engine sets a debuggerSeen flag on the
	 * value to dedupe.
	 *
	 * The implementation decides whether to surface the event to
	 * the user (DAP "exception breakpoints" come in two flavors:
	 * "all" and "uncaught only"; both are implementation-side
	 * policy). The implementation may block the calling thread if
	 * it wants to pause on the throw - the event is delivered
	 * before the exception unwinds further up the stack.
	 *
	 * @param ex The AussomException value just observed.
	 * @param env The current Environment.
	 * @throws aussomException if the implementation needs to abort.
	 */
	void onException(AussomException ex, Environment env)
		throws aussomException;

	/**
	 * Called the first time a Java Exception is thrown out of an
	 * eval (the throw-form error path). Fires once per logical
	 * throw, not once per stack frame the throwable unwinds
	 * through; dedup uses a per-thread "last seen" ThreadLocal on
	 * the Engine.
	 *
	 * The Exception is most commonly aussomException (the
	 * runtime's internal throwable for parse / setup /
	 * not-implemented errors), but can also be an unchecked Java
	 * exception (NullPointerException, ClassCastException, etc.)
	 * that escaped from extern code. Implementations typically
	 * instanceof-check the subtype if they want to theme the UI
	 * differently per source.
	 *
	 * The implementation may block the calling thread to pause
	 * the user on the throw. The throwable is re-thrown by the
	 * engine immediately after this method returns, so any
	 * blocking happens before the unwinding continues.
	 *
	 * @param ex The thrown Java Exception.
	 * @param env The current Environment.
	 * @throws aussomException if the implementation needs to abort.
	 */
	void onException(Exception ex, Environment env)
		throws aussomException;
}

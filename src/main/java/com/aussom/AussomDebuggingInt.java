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

import java.util.List;

import com.aussom.ast.astNode;
import com.aussom.types.AussomType;

/**
 * Engine-side debugger control surface. Standardizes the
 * debugger-related public methods on Engine so external clients
 * (a DAP server, a custom debug REPL, a test harness) can talk
 * to "whatever Engine the embedder has" through a uniform
 * interface, without depending on the concrete Engine class.
 *
 * Engine implements this interface directly. Embedders that wrap
 * or substitute Engine (testing decorators, auth proxies, etc.)
 * can implement it themselves and forward to an underlying
 * Engine instance.
 *
 * Scope: this interface covers the engine-side surface only
 * (register the debugger, find AST nodes for breakpoints,
 * evaluate against a paused frame). Step semantics, per-thread
 * state, continue / step / pause commands, stack-trace and
 * variable inspection all live in the DebuggerInt
 * implementation the embedder or external client provides.
 *
 * See design/debugging-interface-design.md and
 * design/usage-docs/debugger-implementation-guide.md.
 */
public interface AussomDebuggingInt {

	/**
	 * Registers (or clears) the debugger. Setting a non-null
	 * debugger turns debug mode on; setting null turns it off.
	 *
	 * Contract: must be called before any interpreter thread
	 * starts running. The debugger reference itself is
	 * volatile, so the reference may be swapped during a
	 * session, but the engine's underlying debugMode flag is
	 * not guaranteed to flip across threads mid-run.
	 *
	 * @param d The DebuggerInt implementation, or null to clear.
	 */
	void setDebugger(DebuggerInt d);

	/**
	 * Returns the currently registered debugger, or null.
	 * @return A DebuggerInt or null.
	 */
	DebuggerInt getDebugger();

	/**
	 * Returns true if a debugger is currently registered.
	 * @return A boolean with true for enabled and false for not.
	 */
	boolean isDebugMode();

	/**
	 * Walks every class registered in the engine (and the
	 * synthetic script class, if script mode is on) recursively
	 * and returns every astNode whose getFileName() and
	 * getLineNum() match the supplied values. The debugger uses
	 * this to translate a user-supplied "set breakpoint at
	 * file.aus:42" into the AST node(s) to mark.
	 *
	 * Cost is O(N) in total node count. Run once per "set
	 * breakpoint" request, not on the hot path.
	 *
	 * @param fileName The file name to match (must equal getFileName()).
	 * @param lineNumber The line number to match (must equal getLineNum()).
	 * @return A list of matching nodes in source order, possibly empty.
	 */
	List<astNode> findNodesByLine(String fileName, int lineNumber);

	/**
	 * Convenience method that sets a breakpoint at the given
	 * file and line. Marks the first node returned by
	 * findNodesByLine — which is typically the outermost
	 * statement-level node at that location — and leaves the
	 * rest unmarked. Returns true if a node was found and
	 * marked, false if the line has no executable code (a
	 * blank line, a comment, an unrecognized file name).
	 *
	 * For more control (marking multiple nodes on the same
	 * line, picking a specific sub-expression, etc.), call
	 * findNodesByLine directly and flip the breakpoint flag on
	 * the nodes you want.
	 *
	 * @param fileName The file name (must equal getFileName() on the AST node).
	 * @param lineNumber The line number (must equal getLineNum()).
	 * @return true if at least one node was found and marked, false otherwise.
	 */
	boolean setBreakpoint(String fileName, int lineNumber);

	/**
	 * Convenience method that clears any breakpoints set at
	 * the given file and line. Unsets the breakpoint flag on
	 * every matching node — not just the first — so that this
	 * call also undoes whatever a prior setBreakpoint set, as
	 * well as any nodes the caller may have marked manually
	 * via findNodesByLine. Returns true if at least one
	 * breakpoint flag was cleared.
	 *
	 * @param fileName The file name to match.
	 * @param lineNumber The line number to match.
	 * @return true if at least one breakpoint was cleared, false otherwise.
	 */
	boolean clearBreakpoint(String fileName, int lineNumber);

	/**
	 * Convenience method that clears every breakpoint flag in
	 * the AST — across every registered class and the
	 * synthetic script class. Useful for handling DAP-style
	 * "remove all breakpoints" requests in one call. Returns
	 * true if at least one flag was cleared.
	 *
	 * @return true if at least one breakpoint was cleared, false otherwise.
	 */
	boolean clearAllBreakpoints();

	/**
	 * Parses an Aussom source snippet and evaluates it against
	 * the supplied frame's environment. Used by debuggers to
	 * implement DAP "evaluate" requests (and similar tooling)
	 * that need to inspect or compute values in the context of
	 * a paused frame.
	 *
	 * Returns the value of the last evaluated statement, or
	 * AussomNull if the source produced no statements. A runtime
	 * error from a statement is returned as an AussomException
	 * value (caught and converted; not thrown). Parse errors
	 * throw an aussomException.
	 *
	 * @param source The Aussom source snippet to evaluate.
	 * @param frame The Environment of the paused frame.
	 * @return An AussomType with the last value.
	 * @throws Exception on parse error.
	 */
	AussomType evalInFrame(String source, Environment frame) throws Exception;
}

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

import java.util.Stack;

/**
 * The CUP parse stack, without Vector's synchronization.
 *
 * <p>The CUP runtime keeps its parse stack in a java.util.Stack, which
 * extends Vector, so every access is a synchronized call. The volume is
 * high: the generated parser has 771 elementAt and 763 peek call sites,
 * and lr_parser pushes and pops per shift and per reduction on top of
 * that. Biased locking used to make the uncontended case nearly free,
 * but it was disabled in JDK 15 and removed in 18. Profiling put 11% of
 * parse CPU in Stack.peek, Stack.pop, Vector.addElement and
 * Vector.elementAt.
 *
 * <p>This extends java.util.Stack only so it typechecks against the
 * signature CUP fixes as java.util.Stack, both on the do_action methods
 * and on the protected lr_parser.stack field. None of Vector's own
 * storage is used; the array below is the real stack. That is a
 * deliberate deviation from this project's rule of following the
 * surrounding types, and the reason is that the type is not ours to
 * choose: the generated code names java.util.Stack.
 *
 * <p><b>Why dropping the locking is safe.</b> A parse stack is never
 * shared. Engine.parseSource builds a new Lexer and a new parser for
 * every call (Engine.java:900 and Engine.java:904), and each parser gets
 * its own stack here, so one stack belongs to one parse for the length
 * of that parse. Two situations that look like they might break this do
 * not:
 *
 * <ul>
 * <li><b>Nested parses.</b> An include resolves during the parse that
 *     reads it, through Engine.addInclude (Engine.java:565), which calls
 *     back into parseString. That is a second parser with a second
 *     stack on the same thread, not two users of one stack.</li>
 * <li><b>Concurrent parses on one engine.</b> A program can reach a
 *     parse at run time, through an include inside a conditional or
 *     through reflect, so two threads can be parsing into one Engine.
 *     They still hold separate parser objects and separate stacks. What
 *     they share is engine state such as the class table, which this
 *     class neither touches nor protects.</li>
 * </ul>
 *
 * <p>Only the seven methods the CUP runtime and the generated parser
 * actually call are overridden. Everything else inherited from Vector
 * sees an empty collection, which is harmless because nothing calls it,
 * and is left alone rather than papered over.
 *
 * <p>See design/starup-perf-improvements.md section 3.
 *
 * @author austin
 */
class AussomParseStack extends Stack<Object> {

	private static final long serialVersionUID = 1L;

	/**
	 * Starting slots. The parse of a typical source file does not get
	 * deep, but the grammar allows nesting, so the array grows rather
	 * than being fixed.
	 */
	private static final int INITIAL_SLOTS = 256;

	private Object[] slots = new Object[INITIAL_SLOTS];

	private int count = 0;

	@Override
	public Object push(Object Item) {
		if (this.count == this.slots.length) {
			Object[] bigger = new Object[this.slots.length * 2];
			System.arraycopy(this.slots, 0, bigger, 0, this.count);
			this.slots = bigger;
		}
		this.slots[this.count] = Item;
		this.count++;
		return Item;
	}

	@Override
	public Object pop() {
		this.count--;
		Object out = this.slots[this.count];
		// Cleared so a finished parse does not keep the Symbol, and the
		// AST hanging off it, reachable from this stack.
		this.slots[this.count] = null;
		return out;
	}

	@Override
	public Object peek() {
		return this.slots[this.count - 1];
	}

	@Override
	public Object elementAt(int Index) {
		return this.slots[Index];
	}

	@Override
	public int size() {
		return this.count;
	}

	@Override
	public boolean empty() {
		return this.count == 0;
	}

	@Override
	public void removeAllElements() {
		for (int i = 0; i < this.count; i++) {
			this.slots[i] = null;
		}
		this.count = 0;
	}
}

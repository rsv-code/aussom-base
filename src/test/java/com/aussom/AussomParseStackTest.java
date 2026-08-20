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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

/**
 * The unsynchronized CUP parse stack.
 *
 * <p>This is a hand written data structure sitting under every parse, so
 * it is tested directly rather than only through whatever the grammar
 * happens to exercise. It is also package private, which is why this
 * test declares a package instead of sitting in the default one like
 * most of the suite.
 *
 * <p>The growth path in particular needs its own test. Parsing lang.aus
 * reaches a stack depth of 29 against 256 starting slots, so no ordinary
 * source makes the array grow; it takes something like 400 nested
 * parentheses, which nothing in either test suite contains.
 *
 * <p>Only the seven methods the CUP runtime calls are overridden, so
 * only those are tested. The inherited Vector methods are deliberately
 * left seeing an empty collection; see AussomParseStack.
 *
 * @author austin
 */
@DisplayName("Unsynchronized CUP parse stack")
public class AussomParseStackTest {

	@Test
	@DisplayName("Push, peek and pop return items in stack order.")
	void pushPeekPop() {
		AussomParseStack s = new AussomParseStack();
		assertTrue(s.empty());
		assertEquals(0, s.size());

		Object a = new Object();
		Object b = new Object();
		assertSame(a, s.push(a), "push returns the item it was given.");
		s.push(b);

		assertEquals(2, s.size());
		assertFalse(s.empty());
		assertSame(b, s.peek(), "peek returns the top without removing it.");
		assertEquals(2, s.size(), "peek must not remove.");

		assertSame(b, s.pop());
		assertSame(a, s.pop());
		assertTrue(s.empty());
	}

	@Test
	@DisplayName("elementAt indexes from the bottom, which is what the generated actions do.")
	void elementAtIndexesFromBottom() {
		AussomParseStack s = new AussomParseStack();
		Object a = new Object();
		Object b = new Object();
		Object c = new Object();
		s.push(a);
		s.push(b);
		s.push(c);

		assertSame(a, s.elementAt(0));
		assertSame(b, s.elementAt(1));
		assertSame(c, s.elementAt(2));
		assertSame(s.elementAt(s.size() - 1), s.peek(),
			"The top is the last element, which is how CUP addresses it.");
	}

	@Test
	@DisplayName("Grows past its starting slots and keeps every item in order.")
	void growsBeyondInitialSlots() {
		AussomParseStack s = new AussomParseStack();
		int n = 5000;
		for (int i = 0; i < n; i++) {
			s.push(Integer.valueOf(i));
		}
		assertEquals(n, s.size());
		for (int i = 0; i < n; i++) {
			assertEquals(Integer.valueOf(i), s.elementAt(i),
				"Item at " + i + " survived the array growing.");
		}
		for (int i = n - 1; i >= 0; i--) {
			assertEquals(Integer.valueOf(i), s.pop());
		}
		assertTrue(s.empty());
	}

	/**
	 * A parse leaves its stack behind, and the Symbols on it hold AST
	 * nodes. If pop kept the reference the whole tree would stay
	 * reachable from a finished parser, which Engine.measureRetainedFootprint
	 * exists to let a host reason about.
	 */
	@Test
	@DisplayName("Pop clears the slot so a finished parse retains nothing.")
	void popClearsTheSlot() throws Exception {
		AussomParseStack s = new AussomParseStack();
		s.push(new Object());
		s.push(new Object());
		s.pop();
		s.pop();
		assertTrue(allSlotsNull(s), "Popped slots must not keep references.");
	}

	@Test
	@DisplayName("removeAllElements empties the stack and clears the slots.")
	void removeAllElementsClears() throws Exception {
		AussomParseStack s = new AussomParseStack();
		for (int i = 0; i < 10; i++) {
			s.push(Integer.valueOf(i));
		}
		s.removeAllElements();

		assertTrue(s.empty());
		assertEquals(0, s.size());
		assertTrue(allSlotsNull(s), "Cleared slots must not keep references.");

		// lr_parser.parse calls removeAllElements and then reuses the
		// stack, so it has to still work afterwards.
		s.push("again");
		assertEquals(1, s.size());
		assertEquals("again", s.peek());
	}

	private static boolean allSlotsNull(AussomParseStack Stack) throws Exception {
		Field f = AussomParseStack.class.getDeclaredField("slots");
		f.setAccessible(true);
		Object[] slots = (Object[]) f.get(Stack);
		for (Object o : slots) {
			if (o != null) {
				return false;
			}
		}
		return true;
	}
}

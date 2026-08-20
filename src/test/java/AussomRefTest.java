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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import com.aussom.types.AussomIndexRef;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomRef;
import com.aussom.types.AussomType;

/**
 * References to a slot in a list or a map.
 *
 * <p>The constructor allocates nothing. It used to allocate an empty
 * map that both setters immediately discarded, which was 13% of all
 * interpreter allocation.
 *
 * <p>These pin what the interpreter relies on: a reference reads and
 * writes through the caller's container rather than a copy of it, and
 * binding one side clears the other.
 *
 * <p>See design/interpreter-perf-investigation.md.
 *
 * @author austin
 */
@DisplayName("Aussom references")
public class AussomRefTest {

	@Nested
	@DisplayName("construction")
	class Construction {

		@Test
		@DisplayName("Neither container is allocated by the constructor.")
		void allocatesNothing() {
			AussomRef r = new AussomRef();
			assertNull(r.getMap(), "The constructor must not allocate a map.");
			assertNull(r.getList(), "The constructor must not allocate a list.");
		}



	}

	@Nested
	@DisplayName("bound to a map")
	class BoundToMap {

		@Test
		@DisplayName("Reads and writes go through the caller's map, not a copy.")
		void readsAndWritesTheGivenMap() throws Exception {
			ConcurrentHashMap<String, AussomType> m =
				new ConcurrentHashMap<String, AussomType>();
			AussomInt first = new AussomInt(1L);
			m.put("k", first);

			AussomRef r = new AussomRef();
			r.setMap("k", m);

			assertSame(m, r.getMap(), "The reference must not copy the map.");
			assertSame(first, r.getValue());
			assertNull(r.getList(), "A map reference has no list.");

			AussomInt second = new AussomInt(2L);
			r.assign(second);
			assertSame(second, m.get("k"), "The write must land in the caller's map.");
		}

		@Test
		@DisplayName("A missing key reports the key, not the binding.")
		void missingKeyReportsTheKey() {
			ConcurrentHashMap<String, AussomType> m =
				new ConcurrentHashMap<String, AussomType>();
			final AussomRef r = new AussomRef();
			r.setMap("absent", m);
			Exception e = assertThrows(Exception.class, () -> r.getValue());
			assertTrue(e.getMessage().contains("absent"),
				"Expected the key in the message, got: " + e.getMessage());
		}
	}

	@Nested
	@DisplayName("bound to a list")
	class BoundToList {

		@Test
		@DisplayName("Reads and writes go through the caller's list, not a copy.")
		void readsAndWritesTheGivenList() throws Exception {
			ArrayList<AussomType> lst = new ArrayList<AussomType>();
			AussomInt first = new AussomInt(1L);
			lst.add(first);

			AussomRef r = new AussomRef();
			r.setList(0, lst);

			assertSame(lst, r.getList(), "The reference must not copy the list.");
			assertSame(first, r.getValue());
			assertNull(r.getMap(), "A list reference has no map.");

			AussomInt second = new AussomInt(2L);
			r.assign(second);
			assertSame(second, lst.get(0), "The write must land in the caller's list.");
		}

		@Test
		@DisplayName("An out of range index reports the index.")
		void outOfRangeReportsTheIndex() {
			final AussomRef r = new AussomRef();
			r.setList(7, new ArrayList<AussomType>());
			Exception e = assertThrows(Exception.class, () -> r.getValue());
			assertTrue(e.getMessage().contains("7"),
				"Expected the index in the message, got: " + e.getMessage());
		}

		@Test
		@DisplayName("Rebinding to a list clears the map side, and back again.")
		void rebindingSwapsSides() {
			ConcurrentHashMap<String, AussomType> m =
				new ConcurrentHashMap<String, AussomType>();
			ArrayList<AussomType> lst = new ArrayList<AussomType>();
			lst.add(new AussomInt(1L));

			AussomRef r = new AussomRef();
			r.setMap("k", m);
			assertNotNull(r.getMap());
			assertNull(r.getList());

			r.setList(0, lst);
			assertNull(r.getMap(), "Binding a list must clear the map.");
			assertNotNull(r.getList());

			r.setMap("k", m);
			assertNotNull(r.getMap());
			assertNull(r.getList(), "Binding a map must clear the list.");
		}
	}

	@Nested
	@DisplayName("overloaded index references")
	class IndexRefs {

		@Test
		@DisplayName("An index ref binds no container and its assign is a no-op.")
		void indexRefAssignIsANoOp() {
			AussomIndexRef r =
				new AussomIndexRef(null, new AussomInt(1L));
			assertNull(r.getMap());
			assertNull(r.getList());
			assertDoesNotThrow(() -> r.assign(new AussomInt(2L)),
				"Overloaded index writes route through __opIndexSet__.");
		}

		@Test
		@DisplayName("An index ref read points at the operator.")
		void indexRefReadExplainsItself() {
			final AussomIndexRef r =
				new AussomIndexRef(null, new AussomInt(1L));
			Exception e = assertThrows(Exception.class, () -> r.getValue());
			assertTrue(e.getMessage().contains("__opIndex__"),
				"Got: " + e.getMessage());
		}
	}

	@Test
	@DisplayName("A reference reports itself as a ref type.")
	void typeIsRef() {
		assertEquals("cRef", new AussomRef().getType().name());
	}
}

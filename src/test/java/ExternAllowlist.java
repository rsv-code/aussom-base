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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aussom.Engine;
import com.aussom.SecurityManagerImpl;
import com.aussom.types.AussomInt;
import com.aussom.types.AussomList;
import com.aussom.types.AussomMap;
import com.aussom.types.AussomString;
import com.aussom.types.AussomType;

/**
 * The extern class allowlist, and the security manager value handling
 * it depends on. See design/security-evaluation-f1-f3.md sections 2
 * and 3.
 *
 * Every denial test is paired with the same operation succeeding
 * somewhere, so a test cannot pass because the script was broken.
 */
@DisplayName("Extern class allowlist")
public class ExternAllowlist {

	/** The two packages every base module extern class lives in. */
	private static final List<String> BASE =
		Arrays.asList("com.aussom.stdlib.*", "com.aussom.types.*");

	/** Binds a stdlib class the engine would otherwise reach by include. */
	private static final String BINDS_AMATH =
		"extern class m : com.aussom.stdlib.AMath { public extern abs(Value); }\n"
		+ "class Main { public Main() {} public main(list argv) {\n"
		+ "  return new m().abs(-5); } }";

	/** Exposes the protected converter so the tests can drive it. */
	private static class Probe extends SecurityManagerImpl {
		AussomType convert(Object Value) {
			return this.toAussom(Value);
		}
	}

	/** Enforcing, with an allowlist that is not a collection at all. */
	private static class Malformed extends SecurityManagerImpl {
		Malformed() {
			this.props.put("aussom.extern.allowlist.enforce", true);
			this.props.put("aussom.extern.allowed", Long.valueOf(7));
		}
	}

	private static class Sm extends SecurityManagerImpl {
		Sm(boolean enforce, List<String> allowed) {
			this.props.put("aussom.extern.allowlist.enforce", enforce);
			if (allowed == null) {
				this.props.remove("aussom.extern.allowed");
			} else {
				this.props.put("aussom.extern.allowed", allowed);
			}
		}
	}

	private static Object run(boolean enforce, List<String> allowed, String src)
			throws Exception {
		Engine eng = new Engine(new Sm(enforce, allowed));
		eng.parseString("t.aus", src);
		return eng.run();
	}

	@Nested
	@DisplayName("Enforcement")
	class Enforcement {

		@Test
		@DisplayName("off by default: a script may bind any visible class")
		void offByDefault() throws Exception {
			// The positive leg for every denial below. Also the
			// upgrade guarantee: unchanged behavior when untouched.
			assertEquals(5, run(false, BASE, BINDS_AMATH));
			assertFalse((Boolean) new SecurityManagerImpl()
				.getProperty("aussom.extern.allowlist.enforce"));
		}

		@Test
		@DisplayName("on with the shipped default list: standard library still works")
		void defaultListKeepsStdlibWorking() throws Exception {
			// The test that proves turning enforcement on does not
			// break the engine it is protecting.
			assertEquals(5, run(true, BASE, BINDS_AMATH));
		}

		@Test
		@DisplayName("on with a narrowed list: the binding is refused")
		void narrowedListDenies() {
			Exception e = assertThrows(Exception.class,
				() -> run(true, Arrays.asList("com.aussom.types.*"), BINDS_AMATH));
			assertTrue(e.getMessage().contains("is not permitted"),
				"denial must say so plainly: " + e.getMessage());
			assertTrue(e.getMessage().contains("aussom.extern.allowed"),
				"denial must name the property to change: " + e.getMessage());
		}

		@Test
		@DisplayName("an exact class name permits that class")
		void exactEntryPermits() throws Exception {
			List<String> exact = new ArrayList<String>();
			exact.add("com.aussom.types.*");
			exact.add("com.aussom.stdlib.console");
			exact.add("com.aussom.stdlib.ADate");
			exact.add("com.aussom.stdlib.ABuffer");
			exact.add("com.aussom.stdlib.ALang");
			exact.add("com.aussom.stdlib.AJson");
			exact.add("com.aussom.stdlib.ASecMan");
			exact.add("com.aussom.stdlib.ASecurityManager");
			exact.add("com.aussom.stdlib.SBool");
			exact.add("com.aussom.stdlib.SInt");
			exact.add("com.aussom.stdlib.SDouble");
			exact.add("com.aussom.stdlib.AMath");
			assertEquals(5, run(true, exact, BINDS_AMATH));
		}

		@Test
		@DisplayName("a prefix is a package boundary, not a text prefix")
		void prefixIsAPackageBoundary() {
			// 'com.aussom.std.*' must not admit 'com.aussom.stdlib.X'.
			// A naive startsWith on the entry text gets this wrong.
			assertThrows(Exception.class, () -> run(true,
				Arrays.asList("com.aussom.types.*", "com.aussom.std.*"), BINDS_AMATH));
		}

		@Test
		@DisplayName("an empty or absent list denies everything")
		void absenceDenies() {
			assertThrows(Exception.class,
				() -> run(true, new ArrayList<String>(), BINDS_AMATH));
			assertThrows(Exception.class, () -> run(true, null, BINDS_AMATH));
		}

		@Test
		@DisplayName("a list of the wrong type denies rather than faulting")
		void malformedListDenies() {
			// A non-collection value must be treated as an empty list.
			// Construction then fails on lang.aus's own externs, which
			// is the denial; a NullPointerException or ClassCastException
			// would mean the bad value crashed instead of denying.
			Exception e = assertThrows(Exception.class,
				() -> new Engine(new Malformed()));
			assertTrue(e.getMessage() != null && e.getMessage().contains("is not permitted"),
				"malformed list must deny, not fault: " + e.getMessage());
		}
	}

	@Nested
	@DisplayName("Security manager values")
	class Values {

		@Test
		@DisplayName("a list property reads back as an Aussom list")
		void listReadsAsList() {
			Probe sm = new Probe();
			AussomType v = sm.convert(sm.getProperty("aussom.extern.allowed"));
			assertTrue(v instanceof AussomList, "expected a list, got " + v);
			assertEquals(2, ((AussomList) v).getValue().size());
		}

		@Test
		@DisplayName("an Integer property reads back as an int, not a string")
		void integerReadsAsInt() {
			Probe sm = new Probe();
			AussomType v = sm.convert(Integer.valueOf(1000));
			assertFalse(v instanceof AussomString,
				"an embedder-written int must not read back as a string");
			assertEquals(1000L, ((AussomInt) v).getValue());
		}

		@Test
		@DisplayName("a map property reads back as an Aussom map")
		void mapReadsAsMap() {
			Probe sm = new Probe();
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("depth", Long.valueOf(3));
			m.put("tags", Arrays.asList("a", "b"));
			AussomType v = sm.convert(m);
			assertTrue(v instanceof AussomMap, "expected a map, got " + v);
			assertTrue(((AussomMap) v).getValue().get("tags") instanceof AussomList);
		}

		@Test
		@DisplayName("values handed out are copies, not the stored collection")
		void handedOutValuesAreCopies() {
			Probe sm = new Probe();
			AussomType v = sm.convert(sm.getProperty("aussom.extern.allowed"));
			((AussomList) v).add(new AussomString("com.evil.*"));

			AussomType again = sm.convert(sm.getProperty("aussom.extern.allowed"));
			assertEquals(2, ((AussomList) again).getValue().size(),
				"mutating a handed-out value must not change policy");
		}
	}
}

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
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.aussom.Engine;
import com.aussom.TestSecurityManagerImpl;

/**
 * JUnit 5 coverage for the aussom.include.symlink.follow policy.
 *
 * Following a symbolic link is ordinary filesystem behaviour and often
 * deliberate: a shared module directory or a versioned library linked
 * into a root. It is not a defect and the default does not change it.
 * What was missing is the ability for a host to say otherwise, which is
 * what these tests cover. A host running untrusted tenants, whose
 * include root is a directory those tenants write into, can now require
 * that modules come from inside the root.
 *
 * Note what this deliberately does not claim to do. It blocks symbolic
 * links, not every way one file can wear two names: a hard link into an
 * include root is not a link on the path and is not caught, and neither
 * is a bind mount. Both need write access inside a root, and a host with
 * untrusted writers there has a bigger problem than includes.
 *
 * See design/security-evaluation-g1-g3.md.
 */
@DisplayName("Include symlink policy")
public class IncludeSymlink {

	/** A security manager that refuses to follow links into an include. */
	private static class NoLinks extends TestSecurityManagerImpl {
		NoLinks() {
			this.props.put("aussom.include.symlink.follow", false);
		}
	}

	private static Engine engine(boolean followLinks, Path root) throws Exception {
		Engine eng;
		if (followLinks) eng = new Engine(new TestSecurityManagerImpl());
		else eng = new Engine(new NoLinks());
		eng.addIncludePath(root.toString());
		return eng;
	}

	/** A module class named after the file it lives in. */
	private static void writeModule(Path file, String name) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, "class " + name + " {\n"
			+ "    public " + name + "() { }\n"
			+ "    public who() { return \"" + name + "\"; }\n"
			+ "}\n");
	}

	/** A program that includes a module and instantiates it. */
	private static Path writeProgram(Path dir, String includeName, String className)
			throws IOException {
		Path p = dir.resolve("prog.aus");
		Files.writeString(p, "include " + includeName + ";\n"
			+ "class app {\n"
			+ "    public app() { }\n"
			+ "    public main(args) { x = new " + className + "(); return 0; }\n"
			+ "}\n");
		return p;
	}

	/**
	 * True when this filesystem lets the test create a symbolic link.
	 * Windows without developer mode does not, and a test that cannot
	 * build its own fixture should say so rather than fail.
	 */
	private static boolean canSymlink(Path dir) {
		try {
			Path target = dir.resolve("probe-target");
			Files.writeString(target, "x");
			Files.createSymbolicLink(dir.resolve("probe-link"), target);
			return true;
		} catch (IOException e) {
			return false;
		} catch (UnsupportedOperationException e) {
			return false;
		}
	}

	/* ============================================================ */

	@Nested
	@DisplayName("ordinary includes")
	class Ordinary {

		@Test
		@DisplayName("1. A file under the root loads under both settings")
		void plainFileLoadsEitherWay(@TempDir Path tmp) throws Exception {
			Path root = tmp.resolve("root");
			writeModule(root.resolve("plain.aus"), "plain");
			Path prog = writeProgram(tmp, "plain", "plain");

			Engine follows = engine(true, root);
			follows.parseFile(prog.toString());
			assertTrue(follows.run() == 0, "An ordinary include should run with links allowed.");

			Engine refuses = engine(false, root);
			refuses.parseFile(prog.toString());
			assertTrue(refuses.run() == 0, "An ordinary include should run with links refused.");
		}

		@Test
		@DisplayName("2. A root registered without a trailing separator still works")
		void rootWithoutTrailingSeparator(@TempDir Path tmp) throws Exception {
			Path root = tmp.resolve("root");
			writeModule(root.resolve("sub").resolve("deep.aus"), "deep");
			Path prog = writeProgram(tmp, "sub.deep", "deep");

			Engine eng = new Engine(new NoLinks());
			// No trailing slash. addIncludePath adds one.
			eng.addIncludePath(root.toString());
			eng.parseFile(prog.toString());
			assertTrue(eng.run() == 0, "A dotted include under a bare root should resolve.");
		}
	}

	@Nested
	@DisplayName("policy")
	class Policy {

		@Test
		@DisplayName("3. Paired: a linked module loads by default and is refused "
			+ "when policy says so")
		void linkedFileFollowsPolicy(@TempDir Path tmp) throws Exception {
			if (!canSymlink(tmp)) return;

			Path root = tmp.resolve("root");
			Files.createDirectories(root);
			Path outside = tmp.resolve("outside");
			writeModule(outside.resolve("secret.aus"), "secret");
			Files.createSymbolicLink(root.resolve("secret.aus"), outside.resolve("secret.aus"));
			Path prog = writeProgram(tmp, "secret", "secret");

			// The default is what the engine has always done.
			Engine follows = engine(true, root);
			follows.parseFile(prog.toString());
			assertTrue(follows.run() == 0, "By default a linked module still loads.");

			Engine refuses = engine(false, root);
			Exception thrown = assertThrows(Exception.class, () -> {
				refuses.parseFile(prog.toString());
			});
			assertTrue(thrown.getMessage().contains("symbolic link"),
				"The refusal should say why: " + thrown.getMessage());
			assertTrue(thrown.getMessage().contains("secret.aus"),
				"The refusal should name the include: " + thrown.getMessage());
		}

		@Test
		@DisplayName("4. A linked directory in the middle of the name is refused too")
		void linkedDirectoryIsRefused(@TempDir Path tmp) throws Exception {
			if (!canSymlink(tmp)) return;

			Path root = tmp.resolve("root");
			Files.createDirectories(root);
			Path outside = tmp.resolve("elsewhere");
			writeModule(outside.resolve("mod.aus"), "mod");
			// root/shared -> elsewhere, so the file itself is not a link.
			Files.createSymbolicLink(root.resolve("shared"), outside);
			Path prog = writeProgram(tmp, "shared.mod", "mod");

			Engine refuses = engine(false, root);
			Exception thrown = assertThrows(Exception.class, () -> {
				refuses.parseFile(prog.toString());
			});
			assertTrue(thrown.getMessage().contains("symbolic link"),
				"A linked directory reaches outside the root exactly as a linked file "
					+ "does, and must be refused the same way: " + thrown.getMessage());
		}

		@Test
		@DisplayName("5. A root that is itself a link keeps working, since that is "
			+ "the host's own choice")
		void rootThatIsALinkStillResolves(@TempDir Path tmp) throws Exception {
			if (!canSymlink(tmp)) return;

			Path real = tmp.resolve("real");
			writeModule(real.resolve("mod.aus"), "mod");
			Path root = tmp.resolve("rootlink");
			Files.createSymbolicLink(root, real);
			Path prog = writeProgram(tmp, "mod", "mod");

			Engine eng = engine(false, root);
			eng.parseFile(prog.toString());
			assertTrue(eng.run() == 0,
				"Links at or above the include path are not policed: the policy is about "
					+ "what the include name traverses.");
		}
	}

	@Nested
	@DisplayName("exclude paths")
	class Excludes {

		// Note the directory is not called "private": include names are
		// identifiers, and "private" is a keyword, so the include would
		// fail to parse before reaching the exclusion at all.
		@Test
		@DisplayName("6. A file in an excluded directory is still refused under "
			+ "both settings")
		void excludedDirectoryStillRefused(@TempDir Path tmp) throws Exception {
			Path root = tmp.resolve("root");
			writeModule(root.resolve("vault").resolve("creds.aus"), "creds");
			Path prog = writeProgram(tmp, "vault.creds", "creds");

			for (boolean follow : new boolean[] { true, false }) {
				Engine eng = engine(follow, root);
				eng.addExcludePath(root.resolve("vault").toString());
				Exception thrown = assertThrows(Exception.class, () -> {
					eng.parseFile(prog.toString());
				});
				assertTrue(thrown.getMessage().contains("excluded path"),
					"An excluded directory is refused whatever the link policy says: "
						+ thrown.getMessage());
			}
		}
	}
}

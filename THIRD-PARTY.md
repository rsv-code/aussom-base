# Third-Party Licenses

License audit of `aussom.base`, which is itself Apache License 2.0.

Audited 26 August 2026 against version 1.4.5. Everything below was read
from the artifacts and their POMs in the local Maven repository, or
from the built jars, rather than taken from memory. Where something
could not be verified it says so.

**Conclusion: no license incompatibilities.** Every library that ships
inside aussom.base is under a permissive license that Apache 2.0 can
redistribute. Two GPL tools are used at build time only and do not
affect the output. There are three compliance gaps in the packaging,
listed at the end, none of which change what you may do with the code.

---

## 1. Libraries that ship inside the jar

These are compile-scope dependencies. They are bundled into
`aussom.base-<version>-jar-with-dependencies.jar`, so their licenses
travel with anything that ships it.

| Library | Version | License | Compatible with Apache 2.0 |
|---------|---------|---------|-----------------------------|
| commons-cli | 1.11.0 | Apache License 2.0 | Yes, same license |
| commons-codec | 1.22.1 | Apache License 2.0 | Yes, same license |
| json-simple | 1.1.1 | Apache License 2.0 | Yes, same license |
| java-cup-runtime | 11b-20160615 | CUP Parser Generator license | Yes, permissive |

**commons-cli and commons-codec.** Their POMs inherit the license from
the Apache Commons parent, so the POM itself has no `<licenses>` block.
Both jars carry `META-INF/LICENSE.txt` holding the full Apache License
2.0 text, and a `META-INF/NOTICE.txt` attributing the Apache Software
Foundation. Verified by reading both files out of the jars.

**json-simple.** Its POM declares "The Apache Software License, Version
2.0". The jar carries no LICENSE or NOTICE file of its own, so there is
nothing extra to preserve.

**java-cup-runtime.** This is the only shipped dependency that is not
Apache 2.0. Its POM declares:

```
<name>CUP Parser Generator Copyright Notice, License, and Disclaimer</name>
<url>http://www2.cs.tum.edu/projects/cup/install.php (dead; see below)</url>
<comments>GPL-compatible open-source license</comments>
```

The CUP license is a permissive notice-and-disclaimer license in the
style of MIT or BSD: it grants use, copying, modification and
distribution provided the copyright notice is preserved, and disclaims
warranty. It is not copyleft and imposes no obligation on code that
merely uses it. Authors are Scott E. Hudson, Frank Flannery, Michael
Petter and C. Scott Ananian, confirmed from strings inside the jar.

The full text is in Appendix A. It grants use, copying, modification
and distribution for any purpose without fee, on two conditions: the
copyright notice must appear in all copies, and the copyright notice,
permission notice and warranty disclaimer must appear in supporting
documentation. It also forbids using the authors' names in advertising
without written permission. There is no copyleft obligation.

**One clause changes what counts as covered code.** The license says:

> The portions of CUP output which are hard-coded into the CUP source
> code are (naturally) covered by this same license, as is the CUP
> runtime code linked with the generated parser.

So `src/main/java/com/aussom/parser.java` is a mixed file. The grammar
actions in it come from `src/main/cup/aussom.cup` and are yours under
Apache 2.0. The surrounding boilerplate that CUP emits from its own
templates stays under the CUP license. Both are permissive, so there is
no conflict, but it means the CUP notice requirement reaches the
generated parser in this repository and not only the bundled runtime
classes. See gap 2.

## 2. Libraries used only for tests

These are test-scope. They are not in the shipped jar and place no
obligation on anyone who uses aussom.base.

| Library | Version | License |
|---------|---------|---------|
| junit-jupiter (api, engine, params) | 5.11.4 | Eclipse Public License 2.0 |
| junit-platform-commons, junit-platform-engine | 1.11.4 | Eclipse Public License 2.0 |
| opentest4j | 1.3.0 | Apache License 2.0 |
| apiguardian-api | 1.1.2 | Apache License 2.0 |

EPL 2.0 is a weak copyleft license. It would matter if these shipped,
because EPL and Apache 2.0 have different terms for modified files.
They do not ship. Confirmed by listing the fat jar, which contains no
`org/junit` or `org/opentest4j` classes.

Note that `json-simple` declares a compile-scope dependency on JUnit
4.10, which would otherwise be pulled in and bundled. The POM already
excludes it, both for licensing tidiness and because that JUnit version
has known vulnerabilities.

## 3. Tools used at build time

These run during the build. They do not ship, and their licenses do not
reach the output.

| Tool | Version | License | Effect on output |
|------|---------|---------|------------------|
| cup-maven-plugin | 11b-20160615 | GPL v2 | None, see below |
| java-cup (generator) | 11b-20160615 | CUP license | None |
| maven-jflex-plugin | 1.4.3 | GPL | None, see below |
| jflex | 1.4.3 | GPL | None, see below |
| exec-maven-plugin | 3.1.0 | Apache License 2.0 | None |

**The GPL on these tools does not spread to Aussom.** Using a GPL
program to produce a file does not place the file under the GPL, the
same way compiling with GCC does not make a program GPL. What matters
is whether the tool copies its own GPL code into the output.

**JFlex settles this explicitly.** Its POM says:

> The code generated by JFlex inherits the copyright of the
> specification it was produced from. If it was your specification, you
> may use the generated code without restriction.

`src/main/jflex/Scanner.jflex` is yours, so `src/main/java/com/aussom/Lexer.java`
is yours. The generated file already carries the Aussom Apache 2.0
header, inherited from the specification.

**CUP is close, but not identical.** The grammar actions in
`src/main/java/com/aussom/parser.java` come from
`src/main/cup/aussom.cup`, which is yours. Unlike JFlex, though, CUP
does not disclaim its output: its license states that the portions of
CUP output hard-coded into the CUP source code stay under the CUP
license, as does the runtime linked with the generated parser. The
generated file carries only a "generated by CUP" comment and no license
header, checked by reading the first lines of the file, so nothing in
the file itself records this.

The practical effect is small, because the CUP license is permissive
and imposes only a notice requirement. It does mean the CUP copyright
notice should travel with this repository, not just with the runtime
jar. See gap 2.

Note the split: the **plugin** is GPL v2, the **runtime** is the
permissive CUP license. Only the runtime ships.

## 4. Compliance gaps, all now fixed

None of these ever changed what you may do with the code. They were
packaging problems. All three are resolved as of 26 August 2026; the
original finding is kept with each so the reasoning is on record.

**Gap 1: the Commons CLI NOTICE is lost.** The fat jar contains one
`META-INF/NOTICE.txt`, and it is Commons Codec's. The assembly plugin
overwrites rather than merges, so the last one written wins. Apache
License 2.0 section 4(d) requires that a NOTICE file be carried in
redistributions. Verified by reading `META-INF/NOTICE.txt` out of the
fat jar and seeing only "Apache Commons Codec".

**Fixed.** `src/assembly/jar-with-dependencies.xml` replaces the
built-in descriptor and excludes every dependency's `META-INF/LICENSE*`
and `META-INF/NOTICE*` from the unpack. Their content is preserved in
`META-INF/THIRD-PARTY-NOTICES.txt`, a file this project owns, which no
unpack can overwrite.

**Gap 2: the CUP copyright notice is not carried.** The CUP license
requires the copyright notice to appear in all copies, and the notice
plus permission and disclaimer to appear in supporting documentation.
Two places fall short:

- The fat jar bundles the `java_cup` classes with no CUP license or
  notice text. `META-INF/maven/com.github.vbmacher/java-cup-runtime/pom.xml`
  is bundled and names the license, but that is not the notice.
- `src/main/java/com/aussom/parser.java` contains CUP-derived
  boilerplate under the CUP license and carries no notice at all.

**Fixed in both places.** The notice ships in
`META-INF/THIRD-PARTY-NOTICES.txt`, and `src/build/SplitParserActions.java`
now stamps a header onto `parser.java` naming both licenses and linking
the full text. The stamp is idempotent, so a rebuild that does not
regenerate the parser does not repeat it.

**Gap 3: the Aussom LICENSE is not inside either jar.** The repository
has `LICENSE` with the full Apache 2.0 text, but neither the thin jar
nor the fat jar includes it. This was not a violation, since you own the
code, but shipping your own license inside your own artifact is the
normal convention.

**Fixed.** The assembly descriptor copies the repository's `LICENSE`
to `META-INF/LICENSE.txt`, placed after the dependency unpack so
nothing can clobber it. Verified: the file in the built jar is 11358
bytes, which is Aussom's LICENSE, not the 11359 byte copy that Commons
Codec used to win with.

## 5. How to reproduce this audit

```bash
# what ships, and at what scope
mvn dependency:tree
mvn dependency:list

# declared license per artifact
unzip -p ~/.m2/repository/<path>/<artifact>.pom | grep -A6 '<licenses>'

# license files carried inside a dependency jar
unzip -l <artifact>.jar | grep -iE 'license|notice'

# what the shipped fat jar actually contains
mvn clean package -DskipTests
unzip -l target/aussom.base-*-jar-with-dependencies.jar | grep META-INF
```

---

## Appendix A: CUP Parser Generator license

Verbatim, from
https://github.com/DrMichaelPetter/cup/blob/master/licence.txt
The URL in the artifact's POM is dead. This is the license covering
`java-cup-runtime`, which ships inside the Aussom fat jar, and the
CUP-derived portions of the generated parser.

```
CUP Parser Generator Copyright Notice, License, and Disclaimer

Copyright 1996-2015 by Scott Hudson, Frank Flannery, C. Scott Ananian,
Michael Petter

Permission to use, copy, modify, and distribute this software and its
documentation for any purpose and without fee is hereby granted,
provided that the above copyright notice appear in all copies and that
both the copyright notice and this permission notice and warranty
disclaimer appear in supporting documentation, and that the names of
the authors or their employers not be used in advertising or publicity
pertaining to distribution of the software without specific, written
prior permission.

The authors and their employers disclaim all warranties with regard to
this software, including all implied warranties of merchantability and
fitness. In no event shall the authors or their employers be liable for
any special, indirect or consequential damages or any damages
whatsoever resulting from loss of use, data or profits, whether in an
action of contract, negligence or other tortious action, arising out of
or in connection with the use or performance of this software.

This is an open source license. It is also GPL-Compatible (see entry
for "Standard ML of New Jersey"). The portions of CUP output which are
hard-coded into the CUP source code are (naturally) covered by this
same license, as is the CUP runtime code linked with the generated
parser.

Java is a trademark of Sun Microsystems, Inc. References to the Java
programming language in relation to JLex are not meant to imply that
Sun endorses this product.
```

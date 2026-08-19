![alt tag](doc/img/logo.png)

This is the base interpreter implementation of the Aussom language. You can run it from the
command line, but the package was built to be embedded: it provides the language core and the
most common standard library functionality, plus the hooks a host program needs to sandbox,
measure, and control a running script.

## [Docs](https://aussom-lang.com/docsProduct?product=aussom-base)
This README covers building and embedding at a high level. For all details about using the
Aussom interpreter and the language itself, visit
[https://aussom-lang.com](https://aussom-lang.com).

## Command Line Usage

```
> java -jar target/aussom.base-1.4.2-jar-with-dependencies.jar -h
Aussom Version 1.4.2
Copyright 2023 Austin Lehman
Apache License Version 2

usage: aussom [options] <aussom-file>
 -d,--doc         generate aussomdoc for file
 -h,--help        print this message
 -t,--test        run tests for file
 -ta,--test-all   run tests for all classes loaded in the engine
```

Run a script:

```
> java -jar target/aussom.base-1.4.2-jar-with-dependencies.jar examples/helloworld.aus
```

## Embedding

The easiest way to embed the Aussom interpreter is to add it as a Maven dependency. For the most
recent version, [search for aussom on Maven
Central](https://central.sonatype.com/search?q=aussom).

```
<dependency>
    <groupId>io.gitlab.cupofcode</groupId>
    <artifactId>aussom.base</artifactId>
    <version>1.4.2</version>
</dependency>
```

Alternately you can download the JAR from Maven Central, or [clone this repository and
build](#building). Once you have the Aussom JAR, include it in your project like any other JAR.

### Running a Script

These are the basic steps to create an Engine, parse a source file, and run it. `run()` finds the
first class with a `main` function, calls it, and returns its exit code (0 for success).

```java
import com.aussom.Engine;
import com.aussom.DefaultSecurityManagerImpl;
import com.aussom.ast.aussomException;

...

// Create a new Aussom engine with a security manager.
Engine eng = new Engine(new DefaultSecurityManagerImpl());

// Parse an Aussom code file.
eng.parseFile("aussom-src/test.aus");

// Run the program. Execution starts in the main function.
int result = eng.run();
```

An engine can also parse source it is handed directly, with `parseString(fileName, contents)`. The
file name is only a label; it is what shows up in error messages and stack traces.

Every engine is independent. The `Engine(SecurityManagerInt, LangRegistry)` constructor parses
`lang.aus` into that engine's own class table, so nothing one engine does to a class definition is
visible to another. That makes it safe to run several tenants in one JVM.

### Registering Your Own Modules

An engine holds a `LangRegistry`: a map of module name to Aussom source that `include` statements
resolve against. The base standard library (`lang.aus`, `sys.aus`, `reflect.aus`, `aunit.aus`,
`math.aus`, `util.aus`, `concurrent.aus`) is always present. Use `addModule` to add your own to a
single engine:

```java
Engine eng = new Engine(new DefaultSecurityManagerImpl());
eng.addModule("test.aus", "enum tenum { one; two; three; }");
```

Aussom code then reaches it by name without the extension. The `include` statement parses the
module, after which the enum is available:

```
include test;
...
en_val = tenum.one;
...
```

Because the registry is per engine and copied at construction, two engines in the same JVM can be
given different standard libraries. A sandboxed engine can be handed a smaller set than a trusted
one. Pass a prepared registry to the `Engine(SecurityManagerInt, LangRegistry)` constructor to do
that.

### JSR 223 Scripting

The interpreter registers a `javax.script` engine, so a host that already speaks JSR 223 does not
need to touch the `Engine` API at all. It is discovered by the names `aussom`, `aus`, and
`Aussom`, and by the `.aus` file extension.

```java
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

ScriptEngine se = new ScriptEngineManager().getEngineByName("aussom");
Object result = se.eval("x = 2 + 3; return x;");   // returns Long 5
```

The engine implements `Compilable` and `Invocable`, reports `MULTITHREADED` threading, and
marshals values between Aussom types and ordinary Java types across `Bindings`. Engines from the
default factory get a `DefaultSecurityManagerImpl`; to supply your own, subclass
`AussomScriptEngineFactory` and override `getScriptEngine()`.

### Security Manager

Security is controlled at the engine level through the `SecurityManagerInt` interface.
`SecurityManagerImpl` is the base implementation, and it denies nearly everything: system
information, reflection, script mode, the debugger, and the test runner are all off by default.
`DefaultSecurityManagerImpl` extends it and turns on only the aussomdoc actions. This is why
running `examples/helloworld.aus` prints `null` for every system property -- `os.info.view` is
false unless a host enables it.

To customize, extend `SecurityManagerImpl` (or implement `SecurityManagerInt` directly) and set
the properties you want, then pass an instance to the Engine constructor.

```java
import com.aussom.SecurityManagerImpl;
...

public class MySecurityManager extends SecurityManagerImpl {
  public MySecurityManager() {
    // Add your custom security manager properties here ...
    this.props.put("dir.current.read", true);
    this.props.put("dir.current.write", false);
    this.props.put("remote.log.write", true);
  }
  // And override functions here if you like ...
}

...

// Create an instance of my security manager.
MySecurityManager mySecMan = new MySecurityManager();

// Create a new Aussom engine with our custom security manager.
Engine eng = new Engine(mySecMan);
```

Numeric runtime limits live in the same property map under the `aussom.limit.*` prefix and are
read into a `Limits` snapshot at construction and again at the start of every program. There is no
setter for them on the Engine; a host that wants a different limit puts it in the security manager
it passes in.

| Property | Default | Bounds |
| --- | --- | --- |
| `aussom.limit.call.depth` | 1000 | How deep Aussom calls may nest |
| `aussom.limit.regex.steps` | 0 (unlimited) | Regex engine steps per match |
| `aussom.limit.sleep.slice` | 50 (ms) | How long a sleeping program runs between control checks |
| `aussom.limit.source.bytes` | 0 (unlimited) | Size of a source file the engine will parse |

See `com.aussom.Limits`, whose javadoc records the reasoning behind each one.

### Host Control and Accounting

A running engine can be measured and steered from outside, so a host can govern a tenant without
the engine deciding policy for itself.

- `cancel()`, `pause()`, `resume()`, `awaitPaused()`, `isFullyPaused()`, `getControlState()` --
  checked at every loop back edge, every Aussom call, every batch of regex subject reads, and
  every sleep slice.
- `getCpuNanos()`, `getAllocatedBytes()`, `resetAccounting()` -- per engine totals banked from the
  JVM's per-thread counters.
- `measureRetainedFootprint()` -- estimated bytes the engine's own values hold. Call it while the
  engine is not running.

Nothing in the engine samples these on a timer or acts on them. The host decides what to do.

## Building

Requires Maven and a JDK. The build targets Java 8 bytecode (`maven-compiler-plugin` source and
target are both 1.8), so a JDK 8 or newer will do.

```
> cd aussom-base/
> mvn clean package
[INFO] Scanning for projects...
[INFO] ------------------< io.gitlab.cupofcode:aussom.base >-------------------
[INFO] Building aussom.base 1.4.2
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] --- clean:3.2.0:clean (default-clean) @ aussom.base ---
[INFO] --- cup:11b-20160615:generate (default) @ aussom.base ---
[INFO] --- jflex:1.4.3:generate (default) @ aussom.base ---
[INFO] --- compiler:3.6.1:compile (default-compile) @ aussom.base ---
[INFO] --- surefire:3.5.2:test (default-test) @ aussom.base ---

...

[INFO] --- assembly:3.7.1:single (make-assembly) @ aussom.base ---
[INFO] Building jar: /home/austin/git/gitlab/aussom-base/target/aussom.base-1.4.2-jar-with-dependencies.jar
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

The target directory then holds an executable JAR named
`aussom.base-1.4.2-jar-with-dependencies.jar`.

The lexer and parser are generated during the build. `src/main/jflex/Scanner.jflex` produces
`src/main/java/com/aussom/Lexer.java`, and `src/main/cup/aussom.cup` produces `parser.java` and
`sym.java` in the same package. They are written into the source tree rather than `target/` so
IDEs index them, and they are committed. Never edit them by hand; change the grammar source and
rebuild.

## Testing

There are two test suites.

**JUnit 5**, covering the Java-facing API -- the JSR 223 engine, script mode, closures, the
debugger, AST node ranges, parse diagnostics, cancellation, multi-engine isolation, the extern
allowlist, the call depth limit, the regex step budget, resource limits, engine control, the
include symlink policy, and footprint accuracy:

```
> mvn test
...
Tests run: 290, Failures: 0, Errors: 0, Skipped: 0
```

Surefire runs an explicit include list in `pom.xml` rather than a name pattern, so a new test
class has to be added there or it will compile and silently not run.

**Aussom integration tests**, which exercise interpreter behavior through the CLI:

```
> java -jar target/aussom.base-1.4.2-jar-with-dependencies.jar -t tests/interpreter.aus
[info] Running tests for file 'tests/interpreter.aus'.

[info] **************************************************************
[info] RUNNING TESTS
[info] **************************************************************

[info] Running Test [ aunitHookFilterTests : aunit hooks, tags, and timeout ]

...

[info] **************************************************************
[info] PASSED: 819 SKIPPED: 0 FAILED: 0 TOTAL: 819
[info] Elapsed: 1.268s
[info] **************************************************************
```

Test counts in both suites grow as tests are added.

## License
Aussom is licensed under the Apache 2.0 license. See accompanying LICENSE file for details.

## Credits

Much thanks to the authors of the [CUP Parser
Generator](http://www2.cs.tum.edu/projects/cup/) and the [JFlex](https://jflex.de/) scanner
generator, which together build the front end. The interpreter also relies on
[json-simple](https://code.google.com/archive/p/json-simple/), [Apache Commons
CLI](https://commons.apache.org/proper/commons-cli/), and [Apache Commons
Codec](https://commons.apache.org/proper/commons-codec/).

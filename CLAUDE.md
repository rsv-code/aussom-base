# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Overall Guidelines

- When working in a git repo, only change files within that repo, never make any changes
  outside the repo, and especially never in the system files, even if they are owned by the
  current user. If you need to modify a file outside the current git repo, ask for
  permission first.

# Documentation

- When writing documentation assume a United States 12th grade reading level.

- Do a thorough job explaining the subject, but keep descriptions consise and avoid
  wordy descriptions.

- For complex subjects, start writing about key building blocks first and then build
  up to explaining the complex parts to give the reader the foundation to understand
  the complex subject.

- Use documentation best practices and try to keep the characters written to those in the
  ASCII table as much as possible.

# Aussom Coding Guides

Use the following guides whenever writing Aussom code.

Always use inheritance over composition.

- Aussom Language Overview: https://aussom-lang.com/docPage?page=written/aussom.md&title=Aussom%20Language%20Overview
- Aussom Style Guide: https://aussom-lang.com/docPage?page=written/aussom-style-guide.md&title=Aussom%20Style%20Guide

Keep in mind error handling and implement try/catch blocks where appropriate. 

## Build Commands

```bash
# Build fat JAR with all dependencies
mvn clean package

# Output: target/aussom.base-1.1.0-jar-with-dependencies.jar
```

The parser (`parser.java`, `sym.java`) and lexer (`Lexer.java`) are **generated files** — do not edit them directly. Edit the grammar source files instead:
- `src/com/aussom/aussom.cup` — CUP grammar definition
- `src/com/aussom/Scanner.jflex` — JFlex lexer definition

## Running Tests

The test suite is an Aussom script run through the interpreter itself:

```bash
java -jar target/aussom.base-1.1.0-jar-with-dependencies.jar -t tests/interpreter.aus
```

Expected output: `TOTAL: 319 RAN: 319 PASSED: 319` (counts may vary as tests are added).

## Running Scripts

```bash
# Run an Aussom script
java -jar target/aussom.base-1.1.0-jar-with-dependencies.jar script.aus

# Generate documentation from a script
java -jar target/aussom.base-1.1.0-jar-with-dependencies.jar -d script.aus
```

## Architecture

### Execution Pipeline

1. `Main.java` parses CLI flags and invokes `Engine`
2. `Engine.java` orchestrates: parse → load includes → init classes → run `main()`
3. Lexer (`Scanner.jflex` → `Lexer.java`) tokenizes source
4. Parser (`aussom.cup` → `parser.java`) builds an AST from `ast/` node classes
5. `Engine` walks the AST, managing `CallStack` and `Environment` for scope
6. `Universe.java` is a global singleton holding all class definitions

### Key Packages

- `src/com/aussom/` — Core: `Engine`, `Main`, `Universe`, `Lexer`, `CallStack`, `Environment`
- `src/com/aussom/ast/` — 30+ AST node classes; `astNode` is the base class
- `src/com/aussom/types/` — All Aussom runtime types implement `AussomTypeInt`; primitives (`AussomInt`, `AussomDouble`, `AussomBool`, `AussomString`), collections (`AussomList`, `AussomMap`), control flow (`AussomReturn`, `AussomBreak`, `AussomException`)
- `src/com/aussom/stdlib/` — Java implementations of built-in classes (prefixed `A`), plus `Lang.java` which manages loading of stdlib Aussom source files
- `src/com/aussom/stdlib/aus/` — Standard library written in Aussom itself (`lang.aus`, `math.aus`, `sys.aus`, `reflect.aus`, `util.aus`, `aunit.aus`)

### Security Model

`Engine` accepts a `SecurityManagerInt` implementation that enforces property-based access control (e.g., `dir.current.read`, `remote.log.write`). `DefaultSecurityManagerImpl` is the baseline. Custom security managers are passed to the `Engine` constructor.

### Test Framework

The `@Test` annotation marks test methods in Aussom scripts. `UnitTestRunner` (extends `Engine`) detects annotated methods, runs them, and reports results. The annotation AST nodes are `astAnnotation` and `astAnnotationArg`.

### Embedding the Interpreter

```java
Engine eng = new Engine(new DefaultSecurityManagerImpl());
eng.parseFile("script.aus");
int result = eng.run();
```

## Documentation

```bash
# Regenerate stdlib docs (outputs to docs/)
bash build-docs.sh
```

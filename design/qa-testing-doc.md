# QA/UAT type Testing

This project (aussom-lang) is the base interpreter for the Aussom Programming Language. It 
does not have many users currently, so aside from my own usage there is no one testing it and 
reporting bugs and such. I'd like to identify any existing bugs with this project.

This project already has a large set of tests for the interpreter tests/interpreter.aus. The 
goal is not to just re-implement these, but rather to identify potential bugs and create new 
tests (outside of this file) to test and recreate those bugs.

# Deliverable

Please create a design doc (design/qa-testing.md) with a test plan to achieve the testing 
goals outlined in this doc. If need be, break the implementation up into phases in the design 
if that makes more sense.

# Task Overview

Please read CLAUDE.md for general instructions for this project.

## Functional Tests

What needs to be done is that all files in src/com/aussom need to be read 
including Java (.java) and Aussom (.aus) code files. We need to scrutinize those files 
and look for places where potential bugs can occur in the interpreter. Any bugs 
that you find please add to a new test file `tests/functional-bugs.aus` and recreate 
the bug with a failing test so that we can see all the failures when the tests run.

## Non-Functional Tests

Please evaluate the same code base at src/com/aussom for potential developer pain 
points where developers may get tripped up, or where the API doesn't make logical sense. Please 
create a new document `design/proposed-non-functional-changes.md` detailing these recommended 
changes.


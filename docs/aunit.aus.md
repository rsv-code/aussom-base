# file: aunit.aus

## class: test

[26:21] `static` (extern: com.aussom.stdlib.ATest) **extends: object** 

Implements various static test functions. All expect* helpers throw
an Aussom exception on failure and return true on success. Every
helper takes an optional trailing Msg parameter that is prepended to
the failure message so test authors can record why the assertion
should hold.

#### Methods

- **mkMsg** (`Msg, string Base`)

	> Internal helper: prepend the optional Msg to a base failure string, separated by " :: ". Returns just the base when Msg is null or empty. Exposed so test authors writing their own expect* helpers can produce consistent failure messages.

	- **@p** `Msg` Optional caller-supplied message.
	- **@p** `Base` Base failure description.
	- **@r** `A` composed string.


- **expect** (`Item, ToBe, string Msg = null`)

	> Expect helper function compares two items for equality.

	- **@p** `Item` is the first item to compare.
	- **@p** `ToBe` is the second item to compare.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true if equal and throws an exception if not.


- **expectNotNull** (`Item, string Msg = null`)

	> Expect helper function expects value to not be null.

	- **@p** `Item` is the item to check.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true if not null and throws an exception if not.


- **expectNull** (`Item, string Msg = null`)

	> Expect helper function expects value to be null.

	- **@p** `Item` is the item to check.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true if null and throws an exception if not.


- **expectString** (`Item, string Msg = null`)

	> Expect helper function expects the provided item to be a string.

	- **@p** `Item` is the item to check.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true if the item is a string and throws an exception if not.


- **expectBool** (`Item, string Msg = null`)

	> Expect helper function expects the provided item to be a bool.

	- **@p** `Item` is the item to check.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true if the item is a bool and throws an exception if not.


- **expectInt** (`Item, string Msg = null`)

	> Expect helper function expects the provided item to be an int.

	- **@p** `Item` is the item to check.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true if the item is a int and throws an exception if not.


- **expectDouble** (`Item, string Msg = null`)

	> Expect helper function expects the provided item to be a double.

	- **@p** `Item` is the item to check.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true if the item is a double and throws an exception if not.


- **expectNumber** (`Item, string Msg = null`)

	> Expect helper function expects the provided item to be a type of number.

	- **@p** `Item` is the item to check.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true if the item is a type of number and throws an exception if not.


- **expectList** (`Item, string Msg = null`)

	> Expect helper function expects the provided item to be a list.

	- **@p** `Item` is the item to check.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true if the item is a list and throws an exception if not.


- **expectMap** (`Item, string Msg = null`)

	> Expect helper function expects the provided item to be a map.

	- **@p** `Item` is the item to check.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true if the item is a map and throws an exception if not.


- **expectObject** (`Item, string ClassName, string Msg = null`)

	> Expect helper function expects the provided item to be an object.

	- **@p** `Item` is the item to check.
	- **@p** `ClassName` is the class name to check against.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true if the item is an object and throws an exception if not.


- **expectCallback** (`Item, string Msg = null`)

	> Expect helper function expects the provided item to be a callback.

	- **@p** `Item` is the item to check.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true if the item is a callback and throws an exception if not.


- **expectTrue** (`Item, string Msg = null`)

	> Expect helper function expects the value to be true.

	- **@p** `Item` is the item to check.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true on pass and throws an exception if not.


- **expectFalse** (`Item, string Msg = null`)

	> Expect helper function expects the value to be false.

	- **@p** `Item` is the item to check.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true on pass and throws an exception if not.


- **expectClose** (`double A, double B, double Tolerance = 1.0E-4, string Msg = null`)

	> Expect helper compares two doubles within a tolerance. Useful for floating-point math where exact equality is brittle.

	- **@p** `A` is the first value.
	- **@p** `B` is the second value.
	- **@p** `Tolerance` is the allowed absolute difference (default 0.0001).
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true on pass and throws an exception if not.


- **expectThrows** (`callback Cb, string Msg = null`)

	> Expect helper that asserts the provided callback throws when invoked. Aussom propagates AussomException automatically, so exception-path tests must use try-catch.

	- **@p** `Cb` is a callback to invoke.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true on pass and throws an exception if not.


- **expectThrowsMessage** (`callback Cb, string Substr, string Msg = null`)

	> Like expectThrows but also asserts the thrown exception's text contains the provided substring.

	- **@p** `Cb` is a callback to invoke.
	- **@p** `Substr` is the substring expected in the exception text.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true on pass and throws an exception if not.


- **expectContains** (`list Lst, Item, string Msg = null`)

	> Expect helper for list membership. Uses list.contains internally.

	- **@p** `Lst` is the list to search.
	- **@p** `Item` is the item to find.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true on pass and throws an exception if not.


- **expectKey** (`map M, string Key, string Msg = null`)

	> Expect helper for map key presence.

	- **@p** `M` is the map to query.
	- **@p** `Key` is the key to look for.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true on pass and throws an exception if not.


- **expectSize** (`Collection, int N, string Msg = null`)

	> Expect helper for collection size. Accepts list, map, or string. Uses Aussom's '#' length operator.

	- **@p** `Collection` is a list, map, or string.
	- **@p** `N` is the expected length.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true on pass and throws an exception if not.


- **expectMatches** (`string S, string RegexStr, string Msg = null`)

	> Expect helper that asserts a string matches a regular expression. Uses Java regex syntax via the regex.match() utility.

	- **@p** `S` is the string to test.
	- **@p** `RegexStr` is a Java-syntax regex pattern.
	- **@p** `Msg` is an optional message included on failure.
	- **@r** `A` bool with true on pass and throws an exception if not.


- **fail** (`string Msg`)

	> Explicit failure with a human-readable message. Equivalent to 'throw "fail(): " + Msg' but reads more naturally inside a test.

	- **@p** `Msg` is the failure message.
	- **@r** `Throws` an exception every time; never returns.


- **runTestsForClass** (`string ClassName`)

	> Runs the unit tests for the provided class name and returns a unit test result with the results. Individual messages are logged to standard out. This function requires test.aussom.runner security manager property to be set to true to run this.

	- **@p** `ClassName` is a string with the class name to run.




## class: testRunner

[329:21] `static` (extern: com.aussom.stdlib.ATestRunner) **extends: object** 

#### Methods

- **loadTestFile** (`string TestsScriptFileName`)

	> Loads a test file with the provided file name and path. This function also identifies and saves the test classes that can be ran.

	- **@p** `TestScriptFileName` is a string with the script file to load.
	- **@r** `this` object


- **loadTestString** (`string FileNameStr, string AussomCodeString`)

	> Loads a test file with the provided file name string contents. This function also identifies and saves the test classes that can be ran.

	- **@p** `FileNameStr` is a string with the name to give to the code provided. This will be the file name the code is attached to. This can be a made up name, it just can't be the same as other loaded file names.
	- **@p** `AussomCodeString` is a string with the code to load.
	- **@r** `this` object


- **getTestClasses** ()

	> Gets a list of test classes that have been loaded.

	- **@r** `A` list of test class names that have been loaded.


- **getTestFunctions** (`string TestClassName`)

	> Gets a list of test functions for the provided class name.

	- **@p** `TestClassName` is a string with the test class to use.
	- **@r** `A` list of test function names that have been loaded.


- **hasBefore** (`string TestClassName`)

	> Checks to see if the @Before function is set.

	- **@p** `TestClassName` is a string with the test class to use.
	- **@r** `A` bool with true for set and false for not.


- **hasAfter** (`string TestClassName`)

	> Checks to see if the @After function is set.

	- **@p** `TestClassName` is a string with the test class to use.
	- **@r** `A` bool with true for set and false for not.


- **hasBeforeEach** (`string TestClassName`)

	> Checks to see if the @BeforeEach function is set. @BeforeEach fires before every individual @Test method.

	- **@p** `TestClassName` is a string with the test class to use.
	- **@r** `A` bool with true for set and false for not.


- **hasAfterEach** (`string TestClassName`)

	> Checks to see if the @AfterEach function is set. @AfterEach fires after every individual @Test method, regardless of pass or fail.

	- **@p** `TestClassName` is a string with the test class to use.
	- **@r** `A` bool with true for set and false for not.


- **hasOnTestFail** (`string TestClassName`)

	> Checks to see if the @OnTestFail function is set. @OnTestFail fires after a @Test method that failed or threw, before any

	- **@AfterEach** `hook` runs.
	- **@p** `TestClassName` is a string with the test class to use.
	- **@r** `A` bool with true for set and false for not.


- **getTestTags** (`string TestClassName, string TestFunctionName`)

	> Returns the parsed tag list for a single @Test method. Tags come from the comma-separated tags arg on @Test, e.g.

	- **@Test(name** `=` "...", tags = "slow,db").
	- **@p** `TestClassName` is a string with the test class to use.
	- **@p** `TestFunctionName` is the name of the @Test function.
	- **@r** `A` list of strings with the tags (empty when none).


- **getTestTimeoutMs** (`string TestClassName, string TestFunctionName`)

	> Returns the parsed timeoutMs value for a single @Test method. 0 means "no timeout". The base runner does not enforce this; a downstream runner (e.g. the aussom CLI) reads the value here and applies a watchdog. See design/aunit-upgrade-eval.md (M8b).

	- **@p** `TestClassName` is a string with the test class to use.
	- **@p** `TestFunctionName` is the name of the @Test function.
	- **@r** `An` int with the timeout in milliseconds.


- **setIncludeTags** (`list Tags`)

	> Sets the include-tag filter. When non-empty, only tests whose tag set intersects the provided list are run. Untagged tests are skipped under an include filter.

	- **@p** `Tags` is a list of strings with the tags to include. Pass an empty list to clear the filter.
	- **@r** `this` object.


- **setExcludeTags** (`list Tags`)

	> Sets the exclude-tag filter. Tests whose tag set intersects the provided list are skipped. Exclude wins over include when a test matches both.

	- **@p** `Tags` is a list of strings with the tags to exclude. Pass an empty list to clear the filter.
	- **@r** `this` object.


- **shouldRun** (`string TestClassName, string TestFunctionName`)

	> Returns whether a single @Test method would run under the current include/exclude tag filters. Useful for verifying filter logic without actually running the test.

	- **@p** `TestClassName` is a string with the test class to use.
	- **@p** `TestFunctionName` is the name of the @Test function.
	- **@r** `A` bool with true if the test would run, false if filtered out.


- **runClassTests** (`string TestClassName`)

	> Runs every @Test method in the named test class through the full per-test loop (so @BeforeEach, @AfterEach, @OnTestFail, and tag filters all apply). Returns a result map with keys total, skipped, passed, failed. Useful for tests that need to observe how the runner handles failing tests, hook errors, or filtered tests without polluting the outer test suite's pass/fail counts.

	- **@p** `TestClassName` is a string with the test class to use.
	- **@r** `A` map with int values for keys 'total', 'skipped', 'passed', and 'failed'.


- **runBefore** (`string TestClassName`)

	> Runs the @Before function.

	- **@p** `TestClassName` is a string with the test class to use.
	- **@r** `this` object


- **runAfter** (`string TestClassName`)

	> Runs the @After function.

	- **@p** `TestClassName` is a string with the test class to use.
	- **@r** `this` object


- **runTest** (`string TestClassName, string TestFunctionName`)

	> Runs the function test with the provided function name. Note that the test must be annotated with @Test or it will fail.

	- **@p** `TestClassName` is a string with the test class to use.
	- **@p** `TestFunctionName` is a string with the test function name.
	- **@r** `A` bool with true for success and false for failure.


- **clearClassObjectCache** ()

	> Clears the class object cache. One object per class is stored and reused to maintain consisitency between class function. This function clears that cache.






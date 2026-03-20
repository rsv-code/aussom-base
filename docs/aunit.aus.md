# file: aunit.aus

## class: test

[20:21] `static` (extern: com.aussom.stdlib.ATest) **extends: object** 

Implements various static test functions.

#### Methods

- **expect** (`Item, ToBe`)

	> Expect helper function compares two items for equality.

	- **@p** `Item` is the first item to compare.
	- **@p** `ToBe` is the second item to compare.
	- **@r** `A` bool with true if equal and throws an exception if not.


- **expectNotNull** (`Item`)

	> Expect helper function expects value to not be null.

	- **@p** `Item` is the item to check.
	- **@r** `A` bool with true if not null and throws an exception if not.


- **expectNull** (`Item`)

	> Expect helper function expects value to be null.

	- **@p** `Item` is the item to check.
	- **@r** `A` bool with true if null and throws an exception if not.


- **expectString** (`Item`)

	> Expect helper function expects the provided item to be a string.

	- **@p** `Item` is the item to check.
	- **@r** `A` bool with true if the item is a string and throws an exception if not.


- **expectBool** (`Item`)

	> Expect helper function expects the provided item to be a bool.

	- **@p** `Item` is the item to check.
	- **@r** `A` bool with true if the item is a bool and throws an exception if not.


- **expectInt** (`Item`)

	> Expect helper function expects the provided item to be an int.

	- **@p** `Item` is the item to check.
	- **@r** `A` bool with true if the item is a int and throws an exception if not.


- **expectDouble** (`Item`)

	> Expect helper function expects the provided item to be a double.

	- **@p** `Item` is the item to check.
	- **@r** `A` bool with true if the item is a double and throws an exception if not.


- **expectNumber** (`Item`)

	> Expect helper function expects the provided item to be a type of number.

	- **@p** `Item` is the item to check.
	- **@r** `A` bool with true if the item is a type of number and throws an exception if not.


- **expectList** (`Item`)

	> Expect helper function expects the provided item to be a list.

	- **@p** `Item` is the item to check.
	- **@r** `A` bool with true if the item is a list and throws an exception if not.


- **expectMap** (`Item`)

	> Expect helper function expects the provided item to be a map.

	- **@p** `Item` is the item to check.
	- **@r** `A` bool with true if the item is a map and throws an exception if not.


- **expectObject** (`Item, string ClassName`)

	> Expect helper function expects the provided item to be an object.

	- **@p** `Item` is the item to check.
	- **@r** `A` bool with true if the item is an object and throws an exception if not.


- **expectCallback** (`Item`)

	> Expect helper function expects the provided item to be a callback.

	- **@p** `Item` is the item to check.
	- **@r** `A` bool with true if the item is a callback and throws an exception if not.


- **runTestsForClass** (`string ClassName`)

	> Runs the unit tests for the provided class name and returns a unit test result with the results. Individual messages are logged to standard out. This function requires test.aussom.runner security manager property to be set to true to run this.

	- **@p** `ClassName` is a string with the class name to run.




## class: testRunner

[129:21] `static` (extern: com.aussom.stdlib.ATestRunner) **extends: object** 

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






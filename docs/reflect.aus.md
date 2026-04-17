# file: reflect.aus

## class: reflect

[25:21] `static` (extern: com.aussom.stdlib.AReflect) **extends: object** 

Reflect class provides functions that allow introspection
of the Aussom interpreter. These functions include viewing
loaded modules, classes, and class definitions. It include
invoking functions, evaling code, and loading modules, but
some of these functions required special permissions in
the runtime that may not be granted.

#### Methods

- **evalStr** (`string CodeStr, string Name = "evalStr"`)

	> Evaluates the provided Aussom code string. Using this function requires 'reflect.eval.string' security manager permission.

	- **@p** `CodeStr` is a string with the code to evaluate.
	- **@p** `Name` is an optional name to be used as the file name of the code.
	- **@r** `This` object.


- **evalFile** (`string FileName`)

	> Evaluates the provided Aussom code file. Using this function requires 'reflect.eval.file' security manager permission.

	- **@p** `FileName` is a string with the file name to evaluate.
	- **@r** `This` object.


- **includeModule** (`string ModuleName`)

	> Includes the module with the provided module name. This is similar to evalFile but instead of specifying a file the module name is specified and the module file will be looked for in the normal include directories. Using the function requires 'reflect.include.module' security manager permission.

	- **@p** `ModuleName` is a string with the module name to include.
	- **@r** `This` object.


- **loadedModules** ()

	> Gets a list of the loaded Aussom modules.

	- **@r** `A` list of strings with the module names loaded.


- **loadedClasses** ()

	> Gets a list of the loaded Aussom classes.

	- **@r** `A` list of strings with the class names loaded.


- **isModuleLoaded** (`string ModuleName`)

	> Checks to see if the provided module name has been loaded.

	- **@p** `ModuleName` is a string with the module name to check.
	- **@r** `A` boolean with true if loaded and false if not.


- **classExists** (`string ClassName`)

	> Checks to see if the provided class name exists.

	- **@p** `ClassName` is a string with the name to check.
	- **@r** `A` boolean with true if it exists and false if not.


- **getClassDef** (`string ClassName`)

	> Gets the class definition for the provided class name. The return object is an instance of RClass, see it's definition for details.

	- **@p** `ClassName` is a string with the class to get the definition of.
	- **@r** `An` instance of RClass with the class definition.


- **instantiate** (`string ClassName`)

	> Instantiates a new object of the class type provided.

	- **@p** `ClassName` is a string with the class name to instantiate.
	- **@r** `A` new instance of that class.


- **invoke** (`object Object, string MethodName, ...`)

	> Invokes a function with the provided object, method name, and optional arguments.

	- **@p** `Object` is the object to invoke.
	- **@p** `MethodName` is a name of the method to invoke.
	- **@p** `...` is an optional list of arguments to use as arguments when invoking the method.
	- **@r** `The` value of the result of the invoked function.




## class: RClass

[115:14] (extern: com.aussom.stdlib.AClass) **extends: object** 

The RClass object is an object that is returned
when calling reflect.getClassDef(). It provides a way
to inspect the class definition.

#### Methods

- **getName** ()

	> Gets the class name.

	- **@r** `A` string with the class name.


- **isStatic** ()

	> Gets the flag with if the class is static or not.

	- **@r** `A` boolean with true if it's static and false if not.


- **isExtern** ()

	> Gets the flag with the class is extern or not.

	- **@r** `A` boolean with true if it's extern and false if not.


- **getExternClassName** ()

	> Gets the external class name.

	- **@r** `A` string with the external class name.


- **getMembers** ()

	> Gets a map of members of the class with name and type.

	- **@r** `A` map with name -> type of each member.


- **getMethods** ()

	> Gets a map of methods with the class with method name method details. This function returns a whole structure with details about the method and it's arguments.

	- **@r** `A` map of maps with the method and all it's details.





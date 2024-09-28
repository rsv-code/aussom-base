# file: sys.aus

## class: sys

[21:21] `static` (extern: com.aussom.stdlib.ASys) **extends: object** 

This static class provides a variety of system
functions.

#### Methods

- **getSysInfo** ()

	> Gets all of the system information in a single string.

	- **@r** `A` string with all the system info.


- **getAssembly** ()

	> Gets the name and path to the Aussom assembly.

	- **@r** `A` string with the result.


- **getAssemblyPath** ()

	> Gets the path of the Aussom assembly.

	- **@r** `A` string with the result.


- **getCurrentPath** ()

	> Gets the current path.

	- **@r** `A` string with the result.


- **getHomePath** ()

	> Get the home path.

	- **@r** `A` string with the result.


- **getUserName** ()

	> Gets the current user name.

	- **@r** `A` string with the result.


- **getOsArch** ()

	> Gets the operating system arch.

	- **@r** `A` string with the result.


- **getOsName** ()

	> Gets the operating system name.

	- **@r** `A` string with the result.


- **getOsVersion** ()

	> Gets the operating system version.

	- **@r** `A` string with the result.


- **getJavaVersion** ()

	> Gets the Java version.

	- **@r** `A` string with the result.


- **getJavaVendor** ()

	> Gets the Java vendor.

	- **@r** `A` string with the result.


- **getJavaVendorUrl** ()

	> Get the Java vendor URL.

	- **@r** `A` string with the result.


- **getJavaClassPath** ()

	> Gets the Java class path.

	- **@r** `A` string with the result.


- **getFileSeparator** ()

	> Gets the system file separator.

	- **@r** `A` string with the result.


- **getLineSeparator** ()

	> Gets the system line separator.

	- **@r** `A` string with the result.


- **getAussomVersion** ()

	> Gets the Aussom version.

	- **@r** `A` string with the result.


- **getJavaHome** ()

	> Gets the Java home value.

	- **@r** `A` string with the result.


- **getMills** ()

	> Gets the current time in milliseconds since epoch.

	- **@r** `An` int with the number of milliseconds.


- **sleep** (`int mills`)

	> Causes the current thread to sleep the number of seconds provided.

	- **@p** `mills` is an int with the number of milliseconds to wait.
	- **@r** `this` object





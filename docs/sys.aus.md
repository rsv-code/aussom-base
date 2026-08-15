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

	> Pauses the running program for the number of milliseconds provided. The wait is broken into short slices rather than one long wait, which is what lets the host stay in control of a sleeping program. Three things follow from that: If the host cancels the program while it is sleeping, the sleep ends and an execution cancelled exception is raised. It is not catchable, because stopping is the host's decision. If the host pauses the program while it is sleeping, the sleep is suspended: the time remaining stops counting down until the program is resumed. So the program still gets the full sleep it asked for, and simply finishes later than the clock on the wall would suggest. A blocking call with its own timeout, such as Latch.await(), behaves the other way: its timeout keeps running while the program is paused. How long a slice is comes from the security manager, under aussom.limit.sleep.slice, because it is a control setting and those live with the rest of the policy. The default is 50 milliseconds, which is the longest a cancel or pause waits to take effect. Setting it to 0 turns slicing off and restores a single wait that nothing can interrupt. A negative number of milliseconds raises an exception rather than failing inside the runtime.

	- **@p** `mills` is an int with the number of milliseconds to wait.
	- **@r** `this` object





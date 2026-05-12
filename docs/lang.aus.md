# file: lang.aus

## class: exception

[1345:14] (extern: com.aussom.types.AussomException) **extends: object** 

The exception class is the object that is created or
thrown when an exception occurs. In your catch (e) {}
block this is the object that is provided there.

#### Methods

- **getLineNumber** ()

	> Gets the line number of the exception.

	- **@r** `An` int with the line number.


- **getExceptionType** ()

	> Gets the type of exception. Options are exUndef, exInternal, or exRuntime.

	- **@r** `A` string with the exception type.


- **getId** ()

	> Gets the exception ID string.

	- **@r** `A` string with the exception ID.


- **getText** ()

	> Gets the exception text.

	- **@r** `A` string with the exception text.


- **getDetails** ()

	> Gets the exception details. The details is often the same as the exception text but can provide additional information in some cases.

	- **@r** `A` string with the exception details.


- **getStackTrace** ()

	> Gets the Aussom stack trace as a string.

	- **@r** `A` string with the stack trace.


- **printStackTrace** ()

	> Creates the Aussom stack trace and prints it to standard output.

	- **@r** `this` object


- **toString** ()

	> Converts the entire exception including line number, type, ID, details, and stack trace and returns it as a string with newline characters.

	- **@r** `A` string with the exception information.




## class: Buffer

[1627:14] (extern: com.aussom.stdlib.ABuffer) **extends: object** 

The Buffer object provides an object for handling binary
data. Aussom doesn't natively support something like a
byte array, so this object is used to provide that
functionality.

#### Methods

- **Buffer** (`int Size = 1024`)

	> Creates a new Buffer object with the optional provided number of bytes.

	- **@p** `Size` is an int with the number of bytes to allocate in the Buffer.
	- **@r** `A` new Buffer object.


- **newBuffer** (`int Size = 1024`)


- **size** ()

	> Gets the size of the Buffer in bytes.

	- **@r** `An` int with the number of bytes.


- **clear** ()

	> Clears the Buffer setting all bytes to 0 and resets the read and write cursors.

	- **@r** `this` object


- **writeSeek** (`int Index`)

	> Moves the write cursor to the specified index.

	- **@p** `Index` is an int with the value to set.
	- **@r** `this` object


- **readSeek** (`int Index`)

	> Moves the read cursor to the specified index.

	- **@p** `Index` is an int with the value to set.
	- **@r** `this` object


- **addString** (`string Str, string Charset = "utf_8"`)

	> Adds the provided string to the Buffer at the current write index with the provided optional character set. This also moves the write cursor to the end of the string that was added.

	- **@p** `Str` is the string to add.
	- **@p** `Charset` is an optional string with the character set to use.
	- **@r** `this` object


- **addByte** (`int Byte`)

	> Adds the provided byte to the Buffer at the current write index. Note that an int (long in Java) is provided but that will be cast to an int to the least significant byte will be added.

	- **@p** `Byte` is an int with a value between -127 and 127.
	- **@r** `this` object


- **addUByte** (`int Byte`)

	> Adds the provided byte to the Buffer at the current write index. Note that an int (long in Java) is provided but the least significant byte will be added.

	- **@p** `Byte` is an int with a value between 0 and 255.
	- **@r** `this` object


- **addShort** (`int Short, string ByteOrder = "big"`)

	> Adds the provided short to the Buffer at the current write index. Note that an int (long in Java) is provided but that will be cast to an int to the least significant two bytes will be added. You can also supply the byte order which defaults to big endian. The byte order affects the order in which they bytes are added to the Buffer and not to how the bytes are taken from the provided int value.

	- **@p** `Short` is an int to add.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **addUShort** (`int Short, string ByteOrder = "big"`)

	> Adds the provided unsigned short to the Buffer at the current write index. Note that an int (long in Java) is provided but that will be cast to an int to the least significant two bytes will be added. You can also supply the byte order which defaults to big endian. The byte order affects the order in which they bytes are added to the Buffer and not to how the bytes are taken from the provided int value.

	- **@p** `Short` is an int to add.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **addInt** (`int Int, string ByteOrder = "big"`)

	> Adds the provided int (4 bytes) to the Buffer at the current write index. Note that an int (long in Java) is provided but that will be cast to an int to the least significant four bytes will be added. You can also supply the byte order which defaults to big endian. The byte order affects the order in which they bytes are added to the Buffer and not to how the bytes are taken from the provided int value.

	- **@p** `Int` is an int to add.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **addUInt** (`int Int, string ByteOrder = "big"`)

	> Adds the provided unsigned int (4 bytes) to the Buffer at the current write index. Note that an int (long in Java) is provided but that will be cast to an int to the least significant four bytes will be added. You can also supply the byte order which defaults to big endian. The byte order affects the order in which they bytes are added to the Buffer and not to how the bytes are taken from the provided int value.

	- **@p** `Int` is an int to add.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **addLong** (`int Long, string ByteOrder = "big"`)

	> Adds the provided long (8 bytes) to the Buffer at the current write index. Note that an int (long in Java) is provided so all eight bytes will be added. You can also supply the byte order which defaults to big endian. The byte order affects the order in which they bytes are added to the Buffer and not to how the bytes are taken from the provided int value.

	- **@p** `Long` is an int to add.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **addFloat** (`double Float, string ByteOrder = "big"`)

	> Adds a float (4 bytes) to the current Buffer. Note that a Aussom double (8 bytes) is provided so only the first 4 bytes will be added. You can also supply the byte order which defaults to big endian. The byte order affects the order in which they bytes are added to the Buffer and not to how the bytes are taken from the provided double value.

	- **@p** `Float` is a double to add.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **addDouble** (`double Double, string ByteOrder = "big"`)

	> Adds a double (8 bytes) to the current Buffer. You can also supply the byte order which defaults to big endian. The byte order affects the order in which they bytes are added to the Buffer and not to how the bytes are taken from the provided double value.

	- **@p** `Double` is a double to add.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **getWriteCursor** ()

	> Gets the current position of the write cursor.

	- **@r** `An` int with the write cursor.


- **getReadCursor** ()

	> Gets the current position of the read cursor.

	- **@r** `An` int with the read cursor.


- **getString** (`string Charset = "utf_8"`)

	> Gets a string from the current read cursor to the end of the Buffer with the optional character set.

	- **@p** `Charset` is a string with the charcter set to use.
	- **@r** `A` string with the content.


- **getStringAt** (`int Length, int Index = -1, string Charset = "utf_8"`)

	> Gets a string with the supplied length at the provided optional index with the optional provided character set. If not specified the index will be the read cursor position.

	- **@p** `Length` is an int with the number of bytes to get.
	- **@p** `Index` is an int with the start index.
	- **@p** `Charset` is a string with the character set to use.
	- **@r** `A` string with the content.


- **getByte** (`int Index = -1`)

	> Gets a byte at the specified index or if not specified at the current read cursor.

	- **@p** `Index` is an optional int with the index to read the byte from.
	- **@r** `An` int with the byte value.


- **getUByte** (`int Index = -1`)

	> Gets an unsigned byte at the specified index or if not specified at the current read cursor.

	- **@p** `Index` is an optional int with the index to read the unsigned byte from.
	- **@r** `An` int with the byte value.


- **getShort** (`int Index = -1, string ByteOrder = "big"`)

	> Gets a short (2 bytes) at the specified index or if not specified at the current read cursor.

	- **@p** `Index` is an optional int with the index to read the short from.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `An` int with the short value.


- **getUShort** (`int Inex = -1, string ByteOrder = "big"`)

	> Gets an unsigned short (2 bytes) at the specified index or if not specified at the current read cursor.

	- **@p** `Index` is an optional int with the index to read the unsigned short from.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `An` int with the short value.


- **getInt** (`int Index = -1, string ByteOrder = "big"`)

	> Gets an int (4 bytes) at the specified index or if not specified at the current read cursor.

	- **@p** `Index` is an optional int with the index to read the int from.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `An` int with the int value.


- **getUInt** (`int Index = -1, string ByteOrder = "big"`)

	> Gets an unsigned int (4 bytes) at the specified index or if not specified at the current read cursor.

	- **@p** `Index` is an optional int with the index to read the unsigned int from.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `An` int with the int value.


- **getLong** (`int Index = -1, string ByteOrder = "big"`)

	> Gets a long (8 bytes) at the specified index or if not specified at the current read cursor.

	- **@p** `Index` is an optional int with the index to read the long from.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `An` int with the long value.


- **getFloat** (`int Index = -1, string ByteOrder = "big"`)

	> Gets a float (4 bytes) at the specified index or if not specified at the current read cursor.

	- **@p** `Index` is an optional int with the index to read the float from.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `A` double with the float value.


- **getDouble** (`int Index = -1, string ByteOrder = "big"`)

	> Gets a double (8 bytes) at the specified index or if not specified at the current read cursor.

	- **@p** `Index` is an optional int with the index to read the double from.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `A` double with the double value.


- **setString** (`string Str, string Charset = "utf_8"`)

	> Sets the Buffer to the current provided string. The Buffer will be resized from what it was to the size of the string and will return the number of bytes as an int.

	- **@p** `Str` is the string to set.
	- **@p** `Charset` is an optional string with the character set to use.
	- **@r** `An` int with the size of the new Buffer.


- **setStringAt** (`int Index, string Str, string Charset = "utf_8"`)

	> Sets the provided string at the provided index with the optional character set and returns the number of bytes as an int.

	- **@p** `Index` is an int with the index to set the string at.
	- **@p** `Str` is the string to set.
	- **@p** `Charset` is an optional string with the character set to use.
	- **@r** `An` int with the number of bytes set.


- **setByte** (`int Index, int Byte, string ByteOrder = "big"`)

	> Sets the provided byte in the Buffer at the provided index. Note that an int (long in Java) is provided but that will be cast to an int to the least significant byte will be added.

	- **@p** `Index` in an int with the index to set the byte at.
	- **@p** `Byte` is an int with a value between -127 and 127.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **setUByte** (`int Index, int Byte, string ByteOrder = "big"`)

	> Sets the provided unsigned byte in the Buffer at the provided index. Note that an int (long in Java) is provided but that will be cast to a byte to the least significant byte will be added.

	- **@p** `Index` in an int with the index to set the byte at.
	- **@p** `Byte` is an int with a value between 0 and 256.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **setShort** (`int Index, int Short, string ByteOrder = "big"`)

	> Sets the provided short (2 bytes) in the Buffer at the provided index. Note that an int (long in Java) is provided but that will be cast to a short and the least significant bytes will be added.

	- **@p** `Index` in an int with the index to set the short at.
	- **@p** `Short` is an int with a value to set.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **setUShort** (`int Index, int Short, string ByteOrder = "big"`)

	> Sets the provided unsigned short (2 bytes) in the Buffer at the provided index. Note that an int (long in Java) is provided but that will be cast to an unsigned short and the least significant bytes will be added.

	- **@p** `Index` in an int with the index to set the short at.
	- **@p** `Short` is an int with a value to set.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **setInt** (`int Index, int Int, string ByteOrder = "big"`)

	> Sets the provided int (4 bytes) in the Buffer at the provided index. Note that an int (long in Java) is provided but that will be cast to an int and the least significant bytes will be added.

	- **@p** `Index` in an int with the index to set the int at.
	- **@p** `Int` is an int with a value to set.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **setUInt** (`int Index, int Int, string ByteOrder = "big"`)

	> Sets the provided unsigned int (4 bytes) in the Buffer at the provided index. Note that an int (long in Java) is provided but that will be cast to an unsigned int and the least significant bytes will be added.

	- **@p** `Index` in an int with the index to set the int at.
	- **@p** `Int` is an int with a value to set.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **setLong** (`int Index, int Long, string ByteOrder = "big"`)

	> Sets the provided long (8 bytes) in the Buffer at the provided index.

	- **@p** `Index` in an int with the index to set the long at.
	- **@p** `Long` is an int with a value to set.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **setFloat** (`int Index, double Float, string ByteOrder = "big"`)

	> Adds a float (4 bytes) to the current Buffer. Note that a Aussom double (8 bytes) is provided so only the first 4 bytes will be added. You can also supply the byte order which defaults to big endian. The byte order affects the order in which they bytes are added to the Buffer and not to how the bytes are taken from the provided double value.

	- **@p** `Index` in an int with the index to set the float at.
	- **@p** `Float` is a double to add.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **setDouble** (`int Index, double Double, string ByteOrder = "big"`)

	> Adds a double (8 bytes) to the current Buffer. You can also supply the byte order which defaults to big endian. The byte order affects the order in which they bytes are added to the Buffer and not to how the bytes are taken from the provided double value.

	- **@p** `Index` in an int with the index to set the double at.
	- **@p** `Double` is a double to add.
	- **@p** `ByteOrder` is an optional string with the byte order (big or little).
	- **@r** `this` object


- **copyFrom** (`int DestIndex, object Buffer, int SrcIndex = -1, int Length = -1`)

	> Coppies the bytes from a source Buffer to this Buffer. You must specify the index in the current (destination) Buffer and the Buffer object to copy from. You may optionally set the index in the source Buffer, otherwise it will just copy from the beginning. You may optionally set the length to copy.

	- **@p** `DestIndex` is an int with the index in the current (destination) Buffer to start copying to.
	- **@p** `Buffer` is a Buffer object to copy bytes from.
	- **@p** `SrcIndex` is an optional index to start copying bytes from in the source Buffer.
	- **@p** `Length` is an optional int with the number of bytes to copy.
	- **@r** `this` object


- **copyTo** (`int SrcIndex, object Buffer, int DestIndex = -1, int Length = -1`)

	> Coppies the bytes from this Buffer to the provided Buffer. You must specify the index in the current (source) Buffer and the Buffer object to copy to. You may optionally set the index in the dest Buffer, otherwise it will just copy from the beginning. You may optionally set the length to copy.

	- **@p** `SrcIndex` is an int with the index in the current (source) Buffer to start copying from.
	- **@p** `Buffer` is a Buffer object to copy the bytes to.
	- **@p** `DestIndex` is an optional int with the destination Buffer to start copying to.
	- **@p** `Length` is an optional int with the number of bytes to copy.


- **byteToBinary** (`int Index`)

	> Gets the binary string representation of the byte at the provided index.

	- **@p** `Index` is an int with the index of the byte to get.
	- **@r** `A` binary string with the representation of the byte.


- **shortToBinary** (`int Index, string ByteOrder = "big"`)

	> Gets the binary string representation of the short at the provided index.

	- **@p** `Index` is an int with the index of the short to get.
	- **@r** `A` binary string with the representation of the short.


- **intToBinary** (`int Index, string ByteOrder = "big"`)

	> Gets the binary string representation of the int at the provided index.

	- **@p** `Index` is an int with the index of the int to get.
	- **@r** `A` binary string with the representation of the int.


- **longToBinary** (`int Index, string ByteOrder = "big"`)

	> Gets the binary string representation of the long at the provided index.

	- **@p** `Index` is an int with the index of the long to get.
	- **@r** `A` binary string with the representation of the long.


- **floatToBinary** (`int Index, string ByteOrder = "big"`)

	> Gets the binary string representation of the float at the provided index.

	- **@p** `Index` is an int with the index of the float to get.
	- **@r** `A` binary string with the representation of the float.


- **doubleToBinary** (`int Index, string ByteOrder = "big"`)

	> Gets the binary string representation of the double at the provided index.

	- **@p** `Index` is an int with the index of the double to get.
	- **@r** `A` binary string with the representation of the double.




## class: charset

[1602:6] `static` **extends: object** 

Defines available character set values.

#### Members
- **us\_ascii**
- **iso\_8859\_1**
- **utf\_8**
- **utf\_16be**
- **utf\_16le**
- **utf\_16**



## class: c

[1414:21] `static` (extern: com.aussom.stdlib.console) **extends: object** 

The static 'c' class also known as console is
the standard object for writing to standard output.
Console provides standard log, print, and println
functions as well as logging ones such as trc,
dbg, info, warn, and err. For log, print, and println
then write to standard out at the info logging level.

#### Methods

- **trc** (`Content`)

	> Logs the provided content at the trace level.

	- **@p** `Content` is any content to log.
	- **@r** `this` object.


- **dbg** (`Content`)

	> Logs the provided content at the debug level.

	- **@p** `Content` is any content to log.
	- **@r** `this` object.


- **log** (`Content`)

	> Logs the provided content at the info level, but probably without the [info] prefix. This is essentially the same as println() or info().

	- **@p** `Content` is any content to log.
	- **@r** `this` object.


- **info** (`Content`)

	> Logs the provided content at the info level.

	- **@p** `Content` is any content to log.
	- **@r** `this` object.


- **warn** (`Content`)

	> Logs the provided content at the warning level.

	- **@p** `Content` is any content to log.
	- **@r** `this` object.


- **err** (`Content`)

	> Logs the provided content at the error level.

	- **@p** `Content` is any content to log.
	- **@r** `this` object.


- **print** (`Content`)

	> Writes the provided content to standard output but without a newline character.

	- **@p** `Content` is any content write.
	- **@r** `this` object.


- **println** (`Content`)

	> Writes the provided content to standard output with a trailing newline character.

	- **@p** `Content` is any content to write.
	- **@r** `this` object.




## class: bool

[21:14] (extern: com.aussom.types.AussomBool) **extends: object** 

The bool class implements bool datatype methods. These
functions can be used on any bool value.

#### Methods

- **toInt** ()

	> Converts the bool value to int.

	- **@r** `An` int with 1 if true and 0 if false.


- **toDouble** ()

	> Converts the bool value to double.

	- **@r** `A` double with 1.0 if true and 0.0 if false.


- **toString** ()

	> Converts the boolean value to string.

	- **@r** `A` string with 'true' or 'false'.


- **compare** (`bool Val`)

	> Compares the value to the provided value.

	- **@r** `An` int value with the value 0 if this == Val, a value less than 0 if !this && Val, and a value greater than 0 if this && !Val.


- **parse** (`string Val`)

	> Parses the provided string value and sets the bool value.

	- **@p** `Val` is a string with 'true' or 'false'.
	- **@r** `This` object.


- **toJson** ()

	> Converts the value to a JSON encoded string.

	- **@r** `A` JSON encoded string.


- **pack** ()

	> Serializes the data into a structure.

	- **@r** `A` packed string.


- **mock** (`string FunctionName, ReturnVal`)

	> Sets a mock on the object for the specified function name. When the function is invoked the provided return value is returned instead of calling the original function.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@r** `this` object


- **mockWhen** (`string FunctionName, callback Condition, ReturnVal`)

	> Sets a mock on the object for the specified function name with the provided callback to provide the condition. When the function is invoked the provided return value is returned instead of calling the original function. This only occurs when the condition provided in Condition is executed and a 1 or true is returned.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@p** `Condition` is a callback with the condition code.
	- **@r** `this` object


- **setSpy** (`string FunctionName`)

	> Sets the spy flag on the object. This will record the arguements provided and the return value of each function call for the provided function name.

	- **@p** `FunctionName` is a string with the function to spy.
	- **@r** `this` object


- **getSpy** (`string FunctionName`)

	> Retuns a list of maps with each map contining the following: - timestamp: The seconds since epoch that the function was called. - arguments: A list of the arguments provided to the function. - returnValue: The value returned from the function.

	- **@p** `FunctionName` is a string with the spy records to get.
	- **@r** `A` list of spy records.




## class: string

[505:14] (extern: com.aussom.types.AussomString) **extends: object** 

Implements string datatype methods.

#### Methods

- **charAt** (`int Index`)

	> Gets the character at the provided index. It returns the character at the provided index as a new string and can throw an index out of bounds exception.

	- **@p** `Index` is an int with the index to get the character at.
	- **@r** `A` string with the character at that index.


- **compare** (`string Str`)

	> This compare function returns an int with the comparison value. It returns a 0 if the strings are equal. It returns a negative value if the current value lexicographically precedes the provided Str value and is posititive if it lexicographically follows the provided Str value.

	- **@p** `Str` is the string to compare to.
	- **@r** `An` int with the return value.


- **compareICase** (`string Str`)

	> Compares the provided string to the current one ignoring case. The comparison is the same as string.compare() but just ignoring case.

	- **@p** `Str` is the string to compare to.
	- **@r** `An` int with the return value.


- **concat** (`string Str`)

	> Concatenates the provided string value to the current string value and storing it as the current string value. This function appends the provided string.

	- **@p** `Str` is the string to concatenate.
	- **@r** `this` string object


- **contains** (`string Needle`)

	> Checks to see if the current string value contains the provided string value.

	- **@p** `Needle` is a string with the value to check.
	- **@r** `A` bool with true if it contains it and false if not.


- **endsWith** (`string Suffix`)

	> Checks to see if the string ends with the provided string.

	- **@p** `Suffix` is string to check.
	- **@r** `A` bool with true if it ends with the provided string and false if not.


- **equals** (`string Str`)

	> Checks to see if the provided string equals the current string.

	- **@p** `Str` is a string to check.
	- **@r** `A` bool with true if it equal and false if not.


- **equalsICase** (`string Str`)

	> Similar to string.equals() this compares the current string to the provided one but it ignores case.

	- **@p** `Str` is a string to check.
	- **@r** `A` bool with true if it equal and false if not.


- **indexOf** (`string Needle`)

	> Returns the index within this string of the first occurrence of the specified substring. If not found it returns -1.

	- **@p** `Needle` is a string to look for.
	- **@r** `An` int with the begining index or -1 if not found.


- **indexOfStart** (`string Needle, int StartIndex`)

	> Similar to string.indexOf() this function returns the index within this string of the first occurrence of the specified found it returns -1. It starts checking in the provided start index.

	- **@p** `Needle` is a string to look for.
	- **@p** `StartIndex` is a string with the starting index to check from.
	- **@r** `An` int with the begining index or -1 if not found.


- **isEmpty** ()

	> Checks to see if the string is empty. It only returns true if the string length is 0.

	- **@r** `A` bool with true if empty and false if not.


- **isBlank** ()

	> Checks to see if the blank is empty after performing a string.trim(). So this will return true if the string has no characters, or is only white space.

	- **@r** `A` bool with true if is blank and false if not.


- **lastIndexOf** (`string Needle`)

	> Finds the last index of the provided string and returns -1 if not found.

	- **@p** `Needle` is the string to search for.
	- **@r** `The` last index of the string being searched for or -1 if not found.


- **lastIndexOfStart** (`string Needle, int StartIndex`)

	> Finds the last index of the provided string and returns -1 if not found. Note that the function starts search at the last index and searches forward so when you provide a start index it will search backward from that position in the curret string.

	- **@p** `Needle` is the string to search for.
	- **@p** `StartIndex` is an int with the begining index to start searching backwards from.
	- **@r** `The` last index of the string being searched for or -1 if not found.


- **length** ()

	> Gets the string length.

	- **@r** `An` int with the string length.


- **matches** (`string Regex`)

	> Checks to see if the provided Java regex expression matches the current string. It returns true if it matches and false if not.

	- **@p** `Regex` is a string with a Java regular expression to match.
	- **@r** `A` boolean with true if it matches and false if not.


- **replace** (`string Find, string Replace`)

	> Replaces the string to find with the provided replacement and returns the new string.

	- **@p** `Find` is a string to find.
	- **@p** `Replace` is a string to replace with.
	- **@r** `A` new string with the replaced string.


- **replaceFirstRegex** (`string Regex, string Replace`)

	> Replace the first occurance of the search regular expression with the provided replacement string.

	- **@p** `Regex` is a string with the regular expression to look for.
	- **@p** `Replace` is the string to replace with.
	- **@r** `A` new string with the replaced value.


- **replaceRegex** (`string Regex, string Replace`)

	> Replaces all instance the provied regular expression matches with the provided replacement string.

	- **@p** `Regex` is a string with the regex pattern.
	- **@p** `Replace` is a string with the replacement string.
	- **@r** `A` new string with the replaced parts.


- **split** (`string Delim, bool AllowBlanks = false`)

	> Splits the current string by the provided delimiter. If allow blanks is set to true, it will also return blank parts between delimiters, otherwise trimmed sections that are empty won't be included in the results.

	- **@p** `Delim` is a string with the delimier to split on.
	- **@p** `AllowBlanks` is an optional bool with true to return blank sections and false for not. The default is false.
	- **@r** `A` list with the split values.


- **startsWith** (`string Prefix`)

	> Checks if the current string starts with the provided string.

	- **@p** `Prefix` is the string to check if it starts with.
	- **@r** `A` bool with true if it starts with and false if not.


- **substr** (`int Index, int EndIndex = null`)

	> Substring returns a sub string of the current string value with the provided index and optional end index.

	- **@p** `Index` is the start index to get the substring from.
	- **@p** `EndIndex` is the optional end index to get the substring from. Default is null.
	- **@r** `A` string with the sub string value.


- **toLower** ()

	> Converts the string to lower case.

	- **@r** `A` string with the current string value but all lower case.


- **toUpper** ()

	> Converts the string to upper case.

	- **@r** `A` string with the current string value but all upper case.


- **trim** ()

	> Removes all of the leading and trainig blank characters from the current string. The blank characters are defined as any character whose codepoint is less than or equal to 'U+0020' (the space character).

	- **@r** `A` string with the leading and training whitespace removed.


- **toJson** ()

	> Converts the value to a JSON encoded string.

	- **@r** `A` JSON encoded string.


- **parseInt** (`int Radix = null`)

	> Parses this string as an int. Convenience method that delegates to Int.parse. Throws on parse failure with the same contract as Int.parse — wrap the call in try/catch when the input may be invalid.

	- **@p** `Radix` is an optional int with the numeric base to parse in (e.g. 16 for hex). When omitted, base 10 is used.
	- **@r** `An` int with the parsed value.


- **parseDouble** ()

	> Parses this string as a double. Convenience method that delegates to Double.parse. Throws on parse failure — wrap the call in try/catch when the input may be invalid.

	- **@r** `A` double with the parsed value.


- **parseBool** ()

	> Parses this string as a bool. Convenience method that delegates to Bool.parse. Throws on parse failure — wrap the call in try/catch when the input may be invalid.

	- **@r** `A` bool with the parsed value.


- **pack** ()

	> Serializes the data into a structure.

	- **@r** `A` packed string.


- **mock** (`string FunctionName, ReturnVal`)

	> Sets a mock on the object for the specified function name. When the function is invoked the provided return value is returned instead of calling the original function.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@r** `this` object


- **mockWhen** (`string FunctionName, callback Condition, ReturnVal`)

	> Sets a mock on the object for the specified function name with the provided callback to provide the condition. When the function is invoked the provided return value is returned instead of calling the original function. This only occurs when the condition provided in Condition is executed and a 1 or true is returned.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@p** `Condition` is a callback with the condition code.
	- **@r** `this` object


- **setSpy** (`string FunctionName`)

	> Sets the spy flag on the object. This will record the arguements provided and the return value of each function call for the provided function name.

	- **@p** `FunctionName` is a string with the function to spy.
	- **@r** `this` object


- **getSpy** (`string FunctionName`)

	> Retuns a list of maps with each map contining the following: - timestamp: The seconds since epoch that the function was called. - arguments: A list of the arguments provided to the function. - returnValue: The value returned from the function.

	- **@p** `FunctionName` is a string with the spy records to get.
	- **@r** `A` list of spy records.




## class: double

[316:14] (extern: com.aussom.types.AussomDouble) **extends: object** 

The int class implements int datatype methods. These
functions can be used on any int value.

#### Methods

- **toInt** ()

	> Casts the double value to int. This will truncate any fractional part of the double and converts internall to a Long int value.

	- **@r** `An` int with the converted value.


- **toBool** ()

	> Converts the double value to a boolean value. If the double value is 0.0, it's converted to false and otherwise it's converted to true.

	- **@r** `A` bool with the converted value.


- **toString** ()

	> Converts the double value to a string.

	- **@r** `A` string with the converted value.


- **compare** (`double Val2`)

	> Compares the provided double value to the current value and returns an int with the result. The value returned is 0 if the current value is numerically equal to Val2. A value less than 0 is returned if the current value is less than Val2, and a value greater than 0 if the current value is greater than Val2.

	- **@p** `Val2` is a double with the value to compare.
	- **@r** `An` in with the comparison result.


- **isInfinite** ()

	> Returns a bool with true if infinite and false if not.

	- **@r** `A` bool with true for infinite and false for not.


- **isNan** ()

	> Returns the is not a number flag.

	- **@r** `A` bool with true if NaN, or false if not.


- **parse** (`string Val`)

	> Parses the provided string value.

	- **@r** `A` double value that is the parsed value from the provided string.


- **toHex** ()

	> Returns a hexadecimal string representation of the double value.

	- **@r** `A` string with the hex value.


- **toJson** ()

	> Converts the value to a JSON encoded string.

	- **@r** `A` JSON encoded string.


- **pack** ()

	> Serializes the data into a structure.

	- **@r** `A` packed string.


- **mock** (`string FunctionName, ReturnVal`)

	> Sets a mock on the object for the specified function name. When the function is invoked the provided return value is returned instead of calling the original function.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@r** `this` object


- **mockWhen** (`string FunctionName, callback Condition, ReturnVal`)

	> Sets a mock on the object for the specified function name with the provided callback to provide the condition. When the function is invoked the provided return value is returned instead of calling the original function. This only occurs when the condition provided in Condition is executed and a 1 or true is returned.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@p** `Condition` is a callback with the condition code.
	- **@r** `this` object


- **setSpy** (`string FunctionName`)

	> Sets the spy flag on the object. This will record the arguements provided and the return value of each function call for the provided function name.

	- **@p** `FunctionName` is a string with the function to spy.
	- **@r** `this` object


- **getSpy** (`string FunctionName`)

	> Retuns a list of maps with each map contining the following: - timestamp: The seconds since epoch that the function was called. - arguments: A list of the arguments provided to the function. - returnValue: The value returned from the function.

	- **@p** `FunctionName` is a string with the spy records to get.
	- **@r** `A` list of spy records.




## class: list

[816:14] (extern: com.aussom.types.AussomList) **extends: object** 

Implements list datatype methods.

#### Methods

- **add** (`ItemToAdd`)

	> Adds the provided item to the list.

	- **@p** `ItemToAdd` is any item to add.
	- **@r** `this` object


- **addAll** (`list ListToAdd`)

	> Adds all the items in the provided list to the current list.

	- **@p** `ListToAdd` is a list with the items to add.
	- **@r** `this` object


- **addAllAt** (`list ListToAdd, int Index`)

	> Adds all items in the provided list to the current list at the provided index.

	- **@p** `ListToAdd` is a list of items to add.
	- **@p** `Index` is an int with the index to start adding items at.
	- **@r** `this` object


- **clear** ()

	> Removes all items from the list.

	- **@r** `this` object


- **clone** ()

	> Clones the current list and returns a shallow copy of the current list.

	- **@r** `A` new list with the cloned values.


- **contains** (`Item`)

	> Checks to see if the provided item exists in the current list. This function only works for primitive types such as null, bool, int, double, and string.

	- **@p** `Item` is the item to check for.
	- **@r** `A` bool with true if found and false if not.


- **containsObjRef** (`Item`)

	> Checks to see if the current list contains the provided object reference.

	- **@p** `Item` is any item to check for the reference in the list.
	- **@r** `A` bool with true if found and false if not.


- **get** (`int Index`)

	> Gets the item at the provided index.

	- **@p** `Index` is an int with the index to get the item at.
	- **@r** `The` item found at the provided index.


- **indexOf** (`Item`)

	> Returns the index of the provided item in the list and -1 if not found.

	- **@p** `Item` is the item to find in the list.
	- **@r** `An` int with the index of the item or -1 if not found.


- **isEmpty** ()

	> Checks to see if the list is empty.

	- **@r** `A` bool with true if empty and false if not.


- **remove** (`Item`)

	> Removes the provided item from the list.

	- **@p** `Item` is the item to remove from the list.
	- **@r** `this` object


- **removeAt** (`int Index`)

	> Remove the item at the provided index.

	- **@p** `Index` is an int with the index to remove the item at.
	- **@r** `The` item that was removed from the list.


- **removeAll** (`list ListToRemove`)

	> Removes all items from the current list with the provided list.

	- **@p** `ListToRemove` is the list of items to remove from the current list.
	- **@r** `A` bool with true if the list has changed and false if not.


- **retainAll** (`list ListToRetain`)

	> Retains only those items provided in the list.

	- **@p** `ListToRetain` is a list of items to retain in the current list.
	- **@r** `A` bool with true if the list has changed and false if not.


- **set** (`int Index, Item`)

	> Sets the provided item at the provided index.

	- **@p** `Index` is an int with the index to set the item at.
	- **@p** `Item` is the item to set.
	- **@r** `The` previous item that was at the location.


- **size** ()

	> Gets the size of the list.

	- **@r** `An` int with the size of the list.


- **subList** (`int StartIndex, int EndIndex`)

	> Produces a sub list with the provided start and end indexes from the current list.

	- **@p** `StartIndex` is an int with the starting index.
	- **@p** `EndIndex` is an int with the ending index.
	- **@r** `A` new list with the items between start and end indexes.


- **sort** ()

	> Sorts the list descending. This function should work for any type and uses the compare function to sort the items.

	- **@r** `A` sorted list.


- **sortAsc** ()

	> Sorts the list ascending. This function should work for any type and uses the compare function to sort the items.

	- **@r** `A` sorted list.


- **join** (`string Glue`)

	> Joins the items in the current list with the provided string.

	- **@p** `Glue` is a string to use to join the items together.
	- **@r** `A` string with the joined parts.


- **sortCustom** (`callback OnCompare`)

	> Sorts the list with the provided custom compare callback. The function definition must take the two items that are being compared and return an int with either a negative value, 0, or a positive value that's used to sort the list.

	- **@p** `OnCompare` is a callback that does the comparison for sorting.


- **toJson** ()

	> Converts the value to a JSON encoded string.

	- **@r** `A` JSON encoded string.


- **pack** ()

	> Serializes the data into a structure.

	- **@r** `A` packed string.


- **mock** (`string FunctionName, ReturnVal`)

	> Sets a mock on the object for the specified function name. When the function is invoked the provided return value is returned instead of calling the original function.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@r** `this` object


- **mockWhen** (`string FunctionName, callback Condition, ReturnVal`)

	> Sets a mock on the object for the specified function name with the provided callback to provide the condition. When the function is invoked the provided return value is returned instead of calling the original function. This only occurs when the condition provided in Condition is executed and a 1 or true is returned.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@p** `Condition` is a callback with the condition code.
	- **@r** `this` object


- **setSpy** (`string FunctionName`)

	> Sets the spy flag on the object. This will record the arguements provided and the return value of each function call for the provided function name.

	- **@p** `FunctionName` is a string with the function to spy.
	- **@r** `this` object


- **getSpy** (`string FunctionName`)

	> Retuns a list of maps with each map contining the following: - timestamp: The seconds since epoch that the function was called. - arguments: A list of the arguments provided to the function. - returnValue: The value returned from the function.

	- **@p** `FunctionName` is a string with the spy records to get.
	- **@r** `A` list of spy records.




## class: cnull

[1317:14] (extern: com.aussom.types.AussomNull) **extends: object** 

Implements null datatype methods.

#### Methods

- **toJson** ()

	> Converts the value to a JSON encoded string.

	- **@r** `A` JSON encoded string.


- **pack** ()

	> Serializes the data into a structure.

	- **@r** `A` packed string.


- **isBlank** ()

	> This function is implemented in case a variable that is expected to have a string is actually set to null. In this case this function will return true.

	- **@r** `A` boolean with true.




## class: Double

[439:21] `static` (extern: com.aussom.stdlib.SDouble) **extends: object** 

Static class Double implements functions the operate on
double data types. For instance you can call
Double.maxVal() to get the maximum double value.

#### Methods

- **maxExp** ()

	> Gets the maximum exponent value. (Java Double.MAX_EXPONENT)

	- **@r** `An` int with the max exponent size.


- **maxVal** ()

	> Gets the double maximum value. (Java Double.MAX_VALUE)

	- **@r** `A` double with the maximum value.


- **minExp** ()

	> Gets the minimum exponent value. (Java Double.MIN_EXPONENT)

	- **@r** `An` int with the minimum exponent value.


- **minNormal** ()

	> Gets the double minimum normal value. (Java Double.MIN_NORMAL)

	- **@r** `A` double with the minimum normal value.


- **minVal** ()

	> Gets the double minimum value. (Java Double.MIN_VALUE)

	- **@r** `A` double with the minumum value.


- **nanVal** ()

	> Returns the not a number (NaN) constant value.

	- **@r** `A` double with the NaN value.


- **negInfinity** ()

	> Gets the negative infinity value.

	- **@r** `A` double with the negative indinity value.


- **posInfinity** ()

	> Gets the positivie infinity value.

	- **@r** `A` double with the positive indinity value.


- **size** ()

	> Gets the number of bits used to represent a double value.

	- **@r** `An` int with the number of bits.


- **parse** (`string Val`)

	> Parses the provided string value.

	- **@r** `A` double value that is the parsed value from the provided string.




## class: int

[128:14] (extern: com.aussom.types.AussomInt) **extends: object** 

The int class implements int datatype methods. These
functions can be used on any int value.

#### Methods

- **toDouble** ()

	> Converts the int to double.

	- **@r** `A` double with the value.


- **toBool** ()

	> Converts the int to a bool.

	- **@r** `A` bool with true if non-zero and false if zero.


- **toString** ()

	> Converts the int to it's string representation.

	- **@r** `A` string with the value.


- **compare** (`int Val`)

	> Compares the value to the provided value.

	- **@r** `An` int with the value 0 if this == Val. A value less than 0 if this < Val, and a value greater than 0 if this > Val.


- **numLeadingZeros** ()

	> Returns the number of zero bits preceding the highest-order one-bit in the two's complement binary representation of the value.

	- **@r** `An` integer with number of zeros.


- **numTrailingZeros** ()

	> Returns the number of zero bits following the lowest-order one-bit in the two's complement binary representation of the value.

	- **@r** `An` integer with the number of zeros.


- **reverse** ()

	> Returns the value by reversing the bits of the value.

	- **@r** `An` int with the reversed bit order.


- **reverseBytes** ()

	> Returns the value by reversing the bytes of the value.

	- **@r** `An` int with the reversed byte order.


- **rotateLeft** (`int Distance`)

	> Rotates the value the number of bits provided to the left.

	- **@r** `An` int with the rotated bits.


- **rotateRight** (`int Distance`)

	> Rotates the value the number of bits provided to the right.

	- **@r** `An` int with the rotated bits.


- **signum** ()

	> Returns the signum function.

	- **@r** `An` int with the signum function.


- **toBinary** ()

	> Returns a string with this value as an unsigned integer in base 2.

	- **@r** `A` string with the binary value.


- **toHex** ()

	> Returns a string with this value as an unsigned integer in base 16.

	- **@r** `A` string with the base 16 value.


- **toOctal** ()

	> Returns a string with the value as an unsigned integer in base 8.

	- **@r** `A` string with the base 8 value.


- **parse** (`string Str, int Radix = null`)

	> Parses the provided string with the provided radix.

	- **@p** `Str` is a string to parse.
	- **@p** `Radix` is the optional radix value to use.
	- **@r** `An` int with the parsed value.


- **toJson** ()

	> Converts the value to a JSON encoded string.

	- **@r** `A` JSON encoded string.


- **pack** ()

	> Serializes the data into a structure.

	- **@r** `A` packed string.


- **mock** (`string FunctionName, ReturnVal`)

	> Sets a mock on the object for the specified function name. When the function is invoked the provided return value is returned instead of calling the original function.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@r** `this` object


- **mockWhen** (`string FunctionName, callback Condition, ReturnVal`)

	> Sets a mock on the object for the specified function name with the provided callback to provide the condition. When the function is invoked the provided return value is returned instead of calling the original function. This only occurs when the condition provided in Condition is executed and a 1 or true is returned.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@p** `Condition` is a callback with the condition code.
	- **@r** `this` object


- **setSpy** (`string FunctionName`)

	> Sets the spy flag on the object. This will record the arguements provided and the return value of each function call for the provided function name.

	- **@p** `FunctionName` is a string with the function to spy.
	- **@r** `this` object


- **getSpy** (`string FunctionName`)

	> Retuns a list of maps with each map contining the following: - timestamp: The seconds since epoch that the function was called. - arguments: A list of the arguments provided to the function. - returnValue: The value returned from the function.

	- **@p** `FunctionName` is a string with the spy records to get.
	- **@r** `A` list of spy records.




## class: Int

[289:21] `static` (extern: com.aussom.stdlib.SInt) **extends: object** 

Static class Int implements functions the operate on
int data types. For instance you can call
Int.maxVal() to get the maximum integer value.

#### Methods

- **maxVal** ()

	> Returns the max int value available. This translates to Javas Long.MAX_VALUE.



- **minVal** ()

	> Returns the min int value available. This translates to Javas Long.IN_VALUE.



- **parse** (`string Str, int Radix = null`)

	> Parses the provided string with the provided radix.

	- **@p** `Str` is a string to parse.
	- **@p** `Radix` is the optional radix value to use.
	- **@r** `An` int with the parsed value.




## class: Date

[1482:14] (extern: com.aussom.stdlib.ADate) **extends: object** 

The Date class holds date and time information. Internally
it stores a java.time.Instant. Hour, minute, and second
accessors are evaluated at UTC.

#### Methods

- **Date** (`int Mills = null`)

	> Default constructor takes an optional argument of milliseconds since epoch and returns the new Date.

	- **@p** `Mills` is an optional int with the milliseconds since epoch.
	- **@r** `A` new Date object.


- **newDate** (`int Mills = null`)


- **getHours** ()

	> Gets the hour of the day evaluated at UTC, represented by a value between 0-23.

	- **@r** `An` int with the hour of the day.


- **getMinutes** ()

	> Returns the number of minutes past the hour evaluated at UTC, represented by an integer value between 0-59.

	- **@r** `An` int with the minutes.


- **getSeconds** ()

	> Gets the number of seconds past the minute evaluated at UTC, represented by an integer value between 0-59.

	- **@r** `An` int with the seconds.


- **getTime** ()

	> Gets the number of milliseconds since epoch (January 1, 1970, 00:00:00 GMT) represented by this Date object.

	- **@r** `An` int with the number of milliseconds since epoch.


- **setHours** (`int Hours`)

	> Sets the hour of this object to the provided value, interpreted at UTC. The underlying Instant is adjusted to match.

	- **@p** `Hours` is an int with the hours to set. (0-23)


- **setMinutes** (`int Minutes`)

	> Sets the minutes of this object to the provided value, interpreted at UTC. The underlying Instant is adjusted to match.

	- **@p** `Minutes` is an int with the minutes to set. (0-59)


- **setSeconds** (`int Seconds`)

	> Sets the seconds of this object to the provided value, interpreted at UTC. The underlying Instant is adjusted to match.

	- **@p** `Seconds` is an int with the seconds to set. (0-59)


- **setTime** (`int TimeMills`)

	> Sets the time of the object with the provided number of milliseconds since epoch (January 1, 1970, 00:00:00 GMT).

	- **@p** `TimeMills` is the number of milliseconds since epoch.


- **toString** ()

	> Returns an ISO 8601 representation of this Date in UTC, formatted as yyyy-MM-dd'T'HH:mm:ss.SSSXXX. Example: 2024-02-15T08:30:00.123+00:00.

	- **@r** `A` string with the formatted date value.


- **parse** (`string DateString, string DateFormat`)

	> Parses the provided date string with the provided format. The pattern syntax is Java DateTimeFormatter syntax, which is mostly compatible with the older SimpleDateFormat syntax but stricter on out-of-range values (e.g. 2024-02-30 will throw rather than rolling over to March 1) and on year widths (use uuuu instead of yyyy for years outside 1..9999). Strings that omit a zone are interpreted at UTC. For the full pattern reference see https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html This function may throw a date parse exception.

	- **@p** `DateString` is the string with the date value to parse.
	- **@p** `DateFormat` is a string with the format to use.
	- **@r** `this` object


- **format** (`string DateFormat = "yyyy-MM-dd HH:mm:ss.SSS Z"`)

	> Converts the current date to string using the provided optional format. The pattern syntax is Java DateTimeFormatter syntax. The output is rendered at UTC. For the full pattern reference see https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html

	- **@p** `DateFormat` is a string with the format to use.
	- **@r** `A` string with the formatted date result.


- **isEpoch** ()

	> Checks to see if the current date is the epoch date of (January 1, 1970, 00:00:00 GMT) represented by 0 milliseconds.

	- **@r** `A` bool with true if it's epoch and false if not.




## class: securitymanager

[2255:21] `static` (extern: com.aussom.stdlib.ASecurityManager) **extends: object** 

The securitymanager class provides an object that you can
instantiate and provide to the Aussom engine to use.

#### Methods

- **getProp** (`string PropName`)

	> Gets the security manager property value with the provided property name. This function requires Security Manager permission securitymanager.property.get.

	- **@p** `PropName` is a string with the property name to get.
	- **@r** `A` simple type with the value of the property.


- **keySet** ()

	> Gets the key set of the properties as a list of strings. This function requires Security Manager permission securitymanager.property.list.

	- **@r** `A` list of strings of the property keys.


- **getMap** ()

	> Gets a map of the security manager properties and their values. This function requires Security Manager permission securitymanager.property.list.

	- **@r** `A` map with the security manager key values.


- **setProp** (`string PropName, Value`)

	> This method provides the ability to set the property of a security manager property pair. This function requires Security Manager permission securitymanager.property.set.

	- **@p** `PropName` is a string with the property key to set.
	- **@p** `Value` is a simple type value to set.
	- **@r** `this` object


- **setMap** (`map PropsToSet`)

	> This method provides the ability to set a whole map of key-val pairs. This function requires Security Manager permission securitymanager.property.set.

	- **@p** `PropsToSet` is a map with the key value pairs to set.
	- **@r** `this` object




## class: secman

[2201:21] `static` (extern: com.aussom.stdlib.ASecMan) **extends: object** 

The static secman class implements function for working with
the security manager of the currently executing engine.

#### Methods

- **getProp** (`string PropName`)

	> Gets the security manager property value with the provided property name. This function requires Security Manager permission securitymanager.property.get.

	- **@p** `PropName` is a string with the property name to get.
	- **@r** `A` simple type with the value of the property.


- **keySet** ()

	> Gets the key set of the properties as a list of strings. This function requires Security Manager permission securitymanager.property.list.

	- **@r** `A` list of strings of the property keys.


- **getMap** ()

	> Gets a map of the security manager properties and their values. This function requires Security Manager permission securitymanager.property.list.

	- **@r** `A` map with the security manager key values.


- **setProp** (`string PropName, Value`)

	> This method provides the ability to set the property of a security manager property pair. This function requires Security Manager permission securitymanager.property.set.

	- **@p** `PropName` is a string with the property key to set.
	- **@p** `Value` is a simple type value to set.
	- **@r** `this` object


- **setMap** (`map PropsToSet`)

	> This method provides the ability to set a whole map of key-val pairs. This function requires Security Manager permission securitymanager.property.set.

	- **@p** `PropsToSet` is a map with the key value pairs to set.
	- **@r** `this` object




## class: Bool

[115:21] `static` (extern: com.aussom.stdlib.SBool) **extends: object** 

Static class Bool implements functions that operate on
bool data types. For example you can use Bool.parse()
to parse a string value.

#### Methods

- **parse** (`string Val`)

	> Parses the provided string and returns the bool value.

	- **@p** `Val` is a string with the bool value.
	- **@r** `A` bool value.




## class: callback

[1260:14] (extern: com.aussom.types.AussomCallback) **extends: object** 

Implements callback datatype methods. The callback is
a function reference that can be passed around. This is
useful when needing to pass a function to call later.

#### Methods

- **call** (`...`)

	> The call method invokes the current callback with the provided list of arguments.

	- **@p** `etc` are the arguments to pass to the function.
	- **@r** `Any` object that the called function returns.


- **\_call** (`list args`)


- **mock** (`string FunctionName, ReturnVal`)

	> Sets a mock on the object for the specified function name. When the function is invoked the provided return value is returned instead of calling the original function.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@r** `this` object


- **mockWhen** (`string FunctionName, callback Condition, ReturnVal`)

	> Sets a mock on the object for the specified function name with the provided callback to provide the condition. When the function is invoked the provided return value is returned instead of calling the original function. This only occurs when the condition provided in Condition is executed and a 1 or true is returned.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@p** `Condition` is a callback with the condition code.
	- **@r** `this` object


- **setSpy** (`string FunctionName`)

	> Sets the spy flag on the object. This will record the arguements provided and the return value of each function call for the provided function name.

	- **@p** `FunctionName` is a string with the function to spy.
	- **@r** `this` object


- **getSpy** (`string FunctionName`)

	> Retuns a list of maps with each map contining the following: - timestamp: The seconds since epoch that the function was called. - arguments: A list of the arguments provided to the function. - returnValue: The value returned from the function.

	- **@p** `FunctionName` is a string with the spy records to get.
	- **@r** `A` list of spy records.




## class: json

[2164:21] `static` (extern: com.aussom.stdlib.AJson) **extends: object** 

The static json class implements some functions for
working with JSON data.

#### Methods

- **parse** (`string JsonString`)

	> Parses the provided JSON string and returns it as a list or map with the values.

	- **@p** `JsonString` is a string with the JSON content to parse.
	- **@r** `A` list or map of items that are the parsed JSON.


- **unpack** (`string JsonString`)

	> The unpack function unmarshalls objects that are defined in the provided JSON string. This is the reverse process of object.pack(). The structure that is used to pack an object is specific to this unpack function and includes things such as members and type information. It's a way of deserializing data that as been serialized with object.pack().

	- **@p** `JsonString` is a string with the packed data.
	- **@r** `The` unpacked data.


- **escape** (`string JsonString`)

	> Utility function which takes a JSON encoded string and escapes it.

	- **@p** `JsonString` is the JSON string to escape.
	- **@r** `A` new string with the escaped JSON.




## class: lang

[2134:21] `static` (extern: com.aussom.stdlib.ALang) **extends: object** 

The staic lang object provides some standard
functionality for the Aussom language.

#### Methods

- **type** (`DataType`)

	> Gets the type of the data provided. If a simple type is provided such as bool, int, double, string, null, or list, then a string with that name is returned. If an object is provided it's class name is returned as the type.

	- **@p** `DataType` is a string with the type to check.
	- **@r** `A` string with the type.


- **getClassAussomdoc** (`string ClassName`)

	> Gets the Aussom doc structure of the object of the class name provided. The class must be already read (included) in the current runtime engine. It then produces a doc structure and returns it. This function requires the Aussom SecurityManager permission aussomdoc.class.getJson.

	- **@p** `ClassName` is a string with the name of the class to get the Aussom doc for.
	- **@r** `A` structur of maps, lists, and fields that has the Aussom doc.




## class: map

[1040:14] (extern: com.aussom.types.AussomMap) **extends: object** 

Implements map datatype methods.

#### Methods

- **clear** ()

	> Clears the current map contents.

	- **@r** `this` object


- **containsKey** (`string Key`)

	> Checks to see if the current map contains the provided key.

	- **@p** `Key` is a strig to check for.
	- **@r** `A` bool with true if found and false if not.


- **containsVal** (`Val`)

	> Checks to see if the map contains the provided value. This checks for the object reference and doesn't do any comparison of values.

	- **@p** `Val` is the value to check for.
	- **@r** `A` bool with true if found and false if not.


- **get** (`string Key`)

	> Gets the value with the provided key.

	- **@p** `Key` is a string with the key for the value to get.
	- **@r** `A` value or null if not found.


- **getd** (`string Key, defVal`)

	> Gets the value with the provided key and returns it. If not found it returns the provided default value.

	- **@p** `Key` is a string with the key for the value to get.
	- **@p** `defVal` is the default value to return if the key isn't found.
	- **@r** `The` value of for the provided key or the default value if not found.


- **isEmpty** ()

	> Checks to see if the current map is empty.

	- **@r** `A` bool with true if empty or false if not.


- **keySet** ()

	> Gets a list of the available keys.

	- **@r** `A` list of strings with the keys.


- **put** (`string Key, Val`)

	> Puts the provided value with the provided key.

	- **@p** `Key` is a string with the key to set.
	- **@p** `Val` is the value to set.
	- **@r** `this` object


- **putAll** (`map ToAdd`)

	> Puts all the items from the provided map into the current map.

	- **@p** `ToAdd` is a map with the keys and values to add.
	- **@r** `this` object


- **putIfAbsent** (`string Key, Val`)

	> Puts the key and value pair if they key doesn't already exist.

	- **@p** `Key` is the key to set.
	- **@p** `Val` is the value to set.
	- **@r** `this` object


- **remove** (`string Key`)

	> Removes the key value pair with the provided key.

	- **@p** `Key` is a string with the key to remove.
	- **@r** `The` value of the item that was removed or null if not found.


- **size** ()

	> Gets the size of the map.

	- **@r** `An` int with the map size.


- **values** ()

	> Gets a list of the values in the map.

	- **@r** `A` list with the map values.


- **toJson** ()

	> Converts the value to a JSON encoded string.

	- **@r** `A` JSON encoded string.


- **pack** ()

	> Serializes the data into a structure.

	- **@r** `A` packed string.


- **mock** (`string FunctionName, ReturnVal`)

	> Sets a mock on the object for the specified function name. When the function is invoked the provided return value is returned instead of calling the original function.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@r** `this` object


- **mockWhen** (`string FunctionName, callback Condition, ReturnVal`)

	> Sets a mock on the object for the specified function name with the provided callback to provide the condition. When the function is invoked the provided return value is returned instead of calling the original function. This only occurs when the condition provided in Condition is executed and a 1 or true is returned.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@p** `Condition` is a callback with the condition code.
	- **@r** `this` object


- **setSpy** (`string FunctionName`)

	> Sets the spy flag on the object. This will record the arguements provided and the return value of each function call for the provided function name.

	- **@p** `FunctionName` is a string with the function to spy.
	- **@r** `this` object


- **getSpy** (`string FunctionName`)

	> Retuns a list of maps with each map contining the following: - timestamp: The seconds since epoch that the function was called. - arguments: A list of the arguments provided to the function. - returnValue: The value returned from the function.

	- **@p** `FunctionName` is a string with the spy records to get.
	- **@r** `A` list of spy records.




## class: byteOrder

[1615:6] `static` **extends: object** 

Defines the byte order types.

#### Members
- **big**
- **little**



## class: object

[1198:14] (extern: com.aussom.types.AussomObject) 

Implements object datatype methods.

#### Methods

- **toJson** ()

	> Converts the value to a JSON encoded string.

	- **@r** `A` JSON encoded string.


- **pack** ()

	> Serializes the data into a structure.

	- **@r** `A` packed string.


- **mock** (`string FunctionName, ReturnVal`)

	> Sets a mock on the object for the specified function name. When the function is invoked the provided return value is returned instead of calling the original function.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@r** `this` object


- **mockWhen** (`string FunctionName, callback Condition, ReturnVal`)

	> Sets a mock on the object for the specified function name with the provided callback to provide the condition. When the function is invoked the provided return value is returned instead of calling the original function. This only occurs when the condition provided in Condition is executed and a 1 or true is returned.

	- **@p** `FunctionName` is a string with the function to mock.
	- **@p** `ReturnVal` is the return value to return.
	- **@p** `Condition` is a callback with the condition code.
	- **@r** `this` object


- **setSpy** (`string FunctionName`)

	> Sets the spy flag on the object. This will record the arguements provided and the return value of each function call for the provided function name.

	- **@p** `FunctionName` is a string with the function to spy.
	- **@r** `this` object


- **getSpy** (`string FunctionName`)

	> Retuns a list of maps with each map contining the following: - timestamp: The seconds since epoch that the function was called. - arguments: A list of the arguments provided to the function. - returnValue: The value returned from the function.

	- **@p** `FunctionName` is a string with the spy records to get.
	- **@r** `A` list of spy records.





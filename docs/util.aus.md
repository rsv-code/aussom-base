# file: util.aus

## class: base64

[21:21] `static` (extern: com.aussom.stdlib.ABase64) **extends: object** 

The static base64 class provides functions for base64
encoding and decoding.

#### Methods

- **encode** (`object BufferObj`)

	> Converts binary Buffer object to base64 encoded hex string.

	- **@p** `BufferObj` is the binary Buffer object to convert.
	- **@r** `An` encoded string.


- **encodeRaw** (`object BufferObj`)

	> Converts binary Buffer object to raw base64 encoded string.

	- **@p** `BufferObj` is the binary Buffer object to convert.
	- **@r** `An` encoded string.


- **decode** (`string B64EncodedString`)

	> Converts base64 encoded hex string to binary Buffer object.

	- **@p** `B64EncodedString` is a base64 encoded string.
	- **@r** `A` binary Buffer object with the result.


- **decodeRaw** (`string B64EncodedString`)

	> Converts base64 encoded raw string to binary Buffer object.

	- **@p** `B64EncodedString` is a base64 encoded string.
	- **@r** `A` binary Buffer object with the result.




## class: uuid

[76:21] `static` (extern: com.aussom.stdlib.AUuid) **extends: object** 

The static uuid class provides universal ID
creation functionality.

#### Methods

- **get** ()

	> Standard globally unique id.

	- **@r** `A` string with the generated UUID.


- **getSecure** ()

	> Generates a globally unique id. Uses SHA-1 to reduce predictability.

	- **@r** `A` string with the generated UUID.




## class: hex

[55:21] `static` (extern: com.aussom.stdlib.AHex) **extends: object** 

The static hex class provides functions for hex
encoding and decoding.

#### Methods

- **encode** (`object BufferObj`)

	> Converts binary Buffer object to hex string.

	- **@p** `BufferObj` is a binary Buffer object to convert.
	- **@r** `A` hex encoded string.


- **decode** (`string HexEncodedString`)

	> Converts hex string to binary Buffer object.

	- **@p** `HexEncodedString` is a string to encode.
	- **@r** `A` binary Buffer object with the decoded value.




## class: regex

[97:21] `static` (extern: com.aussom.stdlib.ARegex) **extends: object** 

The static regex class provides various regular
expression functionality. Aussom uses Java
regular expressions.

#### Methods

- **match** (`string RegexStr, string Haystack`)

	> Returns a list of string matches.

	- **@p** `RegexStr` is a string with the regular expression.
	- **@p** `Haystack` is a string to search.
	- **@r** `A` list of strings with the match results.


- **matchFirst** (`string RegexStr, string Haystack`)

	> Returns a string with the match, or null if no matches found.

	- **@p** `RegexStr` is a string with the regular expression.
	- **@p** `Haysack` is a string to search.
	- **@r** `A` string with the first match if found or null if not.


- **matchLast** (`string RegexStr, string Haystack`)

	> Returns a string with the match, or null if no matches found.

	- **@p** `RegexStr` is a string with the regular expression.
	- **@p** `Haysack` is a string to search.
	- **@r** `A` string with the last match if found or null if not.


- **replace** (`string RegexStr, string ReplaceStr, string Haystack`)

	> Replaces all occurrences with replacement string.

	- **@p** `RegexStr` is a string with the regular expression.
	- **@p** `ReplaceStr` is a string with the value to replace.
	- **@p** `Haysack` is a string to search.
	- **@r** `A` string that's been replaced.


- **replaceFirst** (`string RegexStr, string ReplaceStr, string Haystack`)

	> Replaces first occurrence with replacement string.

	- **@p** `RegexStr` is a string with the regular expression.
	- **@p** `ReplaceStr` is a string with the value to replace.
	- **@p** `Haysack` is a string to search.
	- **@r** `A` string that's been replaced.





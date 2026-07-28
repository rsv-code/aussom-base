# file: util.aus

## class: base64

[21:21] `static` (extern: com.aussom.stdlib.ABase64) **extends: object** 

The static base64 class provides functions for base64
encoding and decoding.

#### Methods

- **encode** (`string Str`)

	> Encodes a string to a standard base64 string. The string is read as UTF-8 bytes. This is the everyday string path.

	- **@p** `Str` is the string to encode.
	- **@r** `A` base64 encoded string.


- **decode** (`string B64EncodedString`)

	> Decodes a standard base64 string back to a string, reading the decoded bytes as UTF-8. Use this when the base64 represents text; use decodeBinary for arbitrary bytes.

	- **@p** `B64EncodedString` is a base64 encoded string.
	- **@r** `The` decoded string.


- **encodeBinary** (`object BufferObj`)

	> Encodes a binary Buffer object to a standard base64 string.

	- **@p** `BufferObj` is the binary Buffer object to encode.
	- **@r** `A` base64 encoded string.


- **decodeBinary** (`string B64EncodedString`)

	> Decodes a standard base64 string to a binary Buffer object.

	- **@p** `B64EncodedString` is a base64 encoded string.
	- **@r** `A` binary Buffer object with the decoded bytes.


- **encodeSafe** (`string Str`)

	> Encodes a string to a safe encoded string. The output uses only the characters 0-9 and a-f (hex of base64), so it needs no escaping in URLs, filenames, headers, or other text. The string is read as UTF-8 bytes.

	- **@p** `Str` is the string to encode.
	- **@r** `A` safe encoded string.


- **decodeSafe** (`string SafeEncodedString`)

	> Decodes a safe encoded string (see encodeSafe) back to a string, reading the decoded bytes as UTF-8.

	- **@p** `SafeEncodedString` is a safe encoded string.
	- **@r** `The` decoded string.




## class: uuid

[97:21] `static` (extern: com.aussom.stdlib.AUuid) **extends: object** 

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

[76:21] `static` (extern: com.aussom.stdlib.AHex) **extends: object** 

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

[118:21] `static` (extern: com.aussom.stdlib.ARegex) **extends: object** 

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





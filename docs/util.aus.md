# file: util.aus

## class: base64

[20:21] `static` (extern: com.aussom.stdlib.ABase64) **extends: object** 

#### Methods

- **encode** (`object BufferObj`)

	> Converts binary buffer object to base64 encoded hex string.

	- **@p** `BufferObj` is the binary buffer object to convert.
	- **@r** `An` encoded string.


- **encodeRaw** (`object BufferObj`)

	> Converts binary buffer object to raw base64 encoded string.

	- **@p** `BufferObj` is the binary buffer object to convert.
	- **@r** `An` encoded string.


- **decode** (`string B64EncodedString`)

	> Converts base64 encoded hex string to binary buffer object.

	- **@p** `B64EncodedString` is a base64 encoded string.
	- **@r** `A` binary buffer object with the result.


- **decodeRaw** (`string B64EncodedString`)

	> Converts base64 encoded raw string to binary buffer object.

	- **@p** `B64EncodedString` is a base64 encoded string.
	- **@r** `A` binary buffer object with the result.




## class: uuid

[73:21] `static` (extern: com.aussom.stdlib.AUuid) **extends: object** 

#### Methods

- **get** ()

	> Standard globally unique id.

	- **@r** `A` string with the generated UUID.


- **getSecure** ()

	> Generates a globally unique id. Uses SHA-1 to reduce predictability.

	- **@r** `A` string with the generated UUID.




## class: hex

[53:21] `static` (extern: com.aussom.stdlib.AHex) **extends: object** 

#### Methods

- **encode** (`object BufferObj`)

	> Converts binary buffer object to hex string.

	- **@p** `BufferObj` is a binary buffer object to convert.
	- **@r** `A` hex encoded string.


- **decode** (`string HexEncodedString`)

	> Converts hex string to binary buffer object.

	- **@p** `HexEncodedString` is a string to encode.
	- **@r** `A` binary buffer object with the decoded value.




## class: regex

[92:21] `static` (extern: com.aussom.stdlib.ARegex) **extends: object** 

#### Methods

- **match** (`string RegexStr, string Haystack`)

	> Returns a list of string matches.



- **matchFirst** (`string RegexStr, string Haystack`)

	> Returns a string with the match, or null if no matches found.



- **matchLast** (`string RegexStr, string Haystack`)

	> Returns a string with the match, or null if no matches found.



- **replace** (`string RegexStr, string ReplaceStr, string Haystack`)

	> Replaces all occurrences with replacement string.



- **replaceFirst** (`string RegexStr, string ReplaceStr, string Haystack`)

	> Replaces first occurrence with replacement string.






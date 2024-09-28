# file: math.aus

## class: math

[21:21] `static` (extern: com.aussom.stdlib.AMath) **extends: object** 

The static math class provides all sorts of mathematical
functions.

#### Methods

- **e** ()

	> Returns e, the base of natural logarithms.

	- **@r** `A` double with the e value.


- **pi** ()

	> Gets the value of pi.

	- **@r** `A` double with the value of pi.


- **abs** (`Val`)

	> Gets the absolute value of the provided value. Acceptable types are bool, int, and double.

	- **@p** `Val` is a value to take the absolute value of.
	- **@r** `An` int with the absolute value.


- **acos** (`double AdjOverHypot`)

	> Arc cosine value for the provided value.

	- **@p** `AdjOverHypot` is a double with the values.
	- **@r** `A` double with the angle value.


- **asin** (`double OppOverHypot`)

	> Arc sine value for the provided value.

	- **@p** `OppOverHypot` is a double with the values.
	- **@r** `A` double with the angle value.


- **atan** (`double OppOverAdj`)

	> Arc tan value for the provided value.

	- **@p** `OppOverAdj` is a double with the values.
	- **@r** `A` double with the angle value.


- **cbrt** (`double Val`)

	> Cubed root function.

	- **@p** `Val` is a double with the value to take the cubed root of.
	- **@r** `A` double with the result.


- **ceil** (`double Val`)

	> Ceiling function returns the smallest double value greater than or equal to the provided value that is a mathematical integer.



- **copySign** (`double Magnitude, double Sign`)

	> Returns the first double argument with the sign of the second double argument.

	- **@p** `Magnitude` is a double with the value.
	- **@p** `Sign` is a double with the sign.
	- **@r** `A` double with the value.


- **cos** (`double AngleRad`)

	> The cosine function.

	- **@p** `AngleRad` is a double with the angle.
	- **@r** `The` cosine of the argument.


- **cosh** (`double AngleRad`)

	> The hyperbolic cosine of a double value.

	- **@p** `AngleRad` is a double with the angle in radians.
	- **@r** `A` double with hyperbolic cosine of the angle.


- **exp** (`double Val`)

	> Returns Euler's number e raised to the power of a double value.

	- **@p** `Val` a double is the exponent to be raised to.
	- **@r** `A` double with the result.


- **expm1** (`double Val`)

	> Returns (e^x)-1.

	- **@p** `Val` is a double with the value of x.
	- **@r** `A` double with the result.


- **floor** (`double Val`)

	> Returns the largest double value that is less than or equal to the provided value that is an integer.

	- **@p** `Val` is a double with the value to check.
	- **@r** `A` double with the floor value.


- **getExponent** (`double Val`)

	> Returns the unbiased exponent used in the representation of a double.

	- **@p** `Val` is a double to get the exponent of.
	- **@r** `A` double with the result.


- **hypot** (`double X, double Y`)

	> Returns sqrt(x^2 + y^2) without intermediate overflow or underflow.

	- **@p** `X` is a double with the x value.
	- **@p** `Y` is a double with the y value.
	- **@r** `A` double with the result.


- **IEEEremainder** (`double Double1, double Double2`)

	> Computes the remainder operation on two arguments by the IEEE 754 standard.

	- **@p** `Double1` is a double with the dividend.
	- **@p** `Double2` is a double with the divisor.
	- **@r** `A` double with the remainder.


- **log** (`double Val`)

	> Returns the natural logarithm (base e) of the provided double value.

	- **@p** `Val` is a double to take the natural log of.
	- **@r** `A` double with the result.


- **log10** (`double Val`)

	> Returns the log base 10 of the provided value.

	- **@p** `Val` is a double with the value to take the log value.
	- **@r** `A` double with the result.


- **log1p** (`double Val`)

	> Returns the natural logarithm of the sum of the argument and 1.

	- **@p** `Val` is a double with the argument.
	- **@r** `A` double with the result.


- **max** (`Val1, Val2`)

	> Returns the greater of the two provided values.

	- **@p** `Val1` is a double with a value to compare.
	- **@p** `Val2` is a double with a value to compare.
	- **@r** `A` double with the greater of the two.


- **min** (`Val1, Val2`)

	> Returns the smaller of the two provided values.

	- **@p** `Val1` is a double with a value to compare.
	- **@p** `Val2` is a double with a value to compare.
	- **@r** `A` double with the smaller of the two.


- **nextAfter** (`double Double1, double Double2`)

	> Returns the double number adjacent to the first number in the direction of the second double argument.

	- **@p** `Double1` is a double with the first value.
	- **@p** `Double2` is a double with the second value.
	- **@r** `A` double with the result.


- **nextUp** (`double Val`)

	> Returns the double value adjacent to the value in the direction of positive infinity.

	- **@p** `Val` is a double with the value to get.
	- **@r** `A` double with the adjacent value.


- **pow** (`double Double1, double Double2`)

	> Returns the value of the first argument to the power of the second argument.

	- **@p** `Double1` is a double with the base number.
	- **@p** `Double2` is a double with the exponent.
	- **@r** `A` double with the value.


- **rand** ()

	> Returns a random double between 0 and 1.0.

	- **@r** `A` double with the random value.


- **rint** (`double Val`)

	> Returns the double value that is closest in value to the provided value and is equal to an integer.

	- **@p** `Val` is a value to check.
	- **@r** `A` doubel with the return value.


- **round** (`double Val`)

	> Rounds to the closest long.

	- **@p** `Val` is a double with the value to round.
	- **@r** `A` double with the result.


- **scalb** (`double DoubleVal, int IntVal`)

	> Returns d × 2^scaleFactor rounded as if performed by a single correctly rounded floating-point multiply to a member of the double value set.

	- **@p** `DoubleVal` is a double with the d value.
	- **@p** `IntVal` is an int with the power.
	- **@r** `An` double with the value.


- **signum** (`double Val`)

	> Returns the signum function of the argument. Returns 0 if the argument is 0, 1.0 if the argument is greater than 0, and -1.0 if the argument is less than 0.

	- **@p** `Val` is a double with the argument.
	- **@r** `A` double with the result.


- **sin** (`double Val`)

	> Gets the trig sine function.

	- **@p** `Val` is the value of the function.
	- **@r** `A` double with the result.


- **sinh** (`double Val`)

	> Returns the hyperbolic sine of the value.

	- **@p** `Val` is the value of the function.
	- **@r** `A` double with the result.


- **sqrt** (`double Val`)

	> Returns the square root of the value.

	- **@p** `Val` is the value of the function.
	- **@r** `A` double with the result.


- **tan** (`double Val`)

	> Gets the trig tangent function.

	- **@p** `Val` is the value of the function.
	- **@r** `A` double with the result.


- **tanh** (`double Val`)

	> Returns the hyperbolic tangent of the value.

	- **@p** `Val` is the value of the function.
	- **@r** `A` double with the result.


- **toDeg** (`double Val`)

	> Converts radians to degrees.

	- **@p** `Val` is a double with the radians.
	- **@r** `A` double with the degrees.


- **toRad** (`double Val`)

	> Converts degrees to radians.

	- **@p** `Val` is a double with the degrees.
	- **@r** `A` double with the radians.


- **ulp** (`double Val`)

	> Returns the size of an ulp of the argument.

	- **@p** `Val` is the argument.
	- **@r** `A` double with the result.





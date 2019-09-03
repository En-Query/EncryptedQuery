BACKGROUND
==========

When creating a Query, a filter expression can be provided in order to skip records from the source.
The filter expression must be a predicate, i.e. a boolean expression.
During Query execution, this expression is evaluated against each input record, if the evaluation
returns true, the record is processed, otherwise the record is skipped (ignored).

This file describes the Syntax of the filter expressions.

SYNTAX 
=======================

Field names (from the corresponding Data Schema): Valid names must match: `[a-zA-Z_] [a-zA-Z_0-9]*`, otherwise use double quotes, e.g. "Phone #"  

Arithmetic Operators: '+', '-', '*', '/', '%'

String concatenation: '||'

Relational:	'>', '>=', '<', '<=' ,'=' , '<>'

Logical operators: And, Or, Not
 
"Is Empty" and "Is Not Empty" (for lists and strings)

"Is Null" and "Is not null" to test null values

"Is" and "Is not" for equality check

"MATCHES" to test regular expressions

Operator "IN" for Strings and Lists

Temporal values: CURRENT_TIMESTAMP, CURRENT_DATE, TIMESTAMP ‘2001-07-04T14:23Z’, DATE ‘2001-07-04’

Lists (Row Value Constructors): (1,2,4), ('a', 'b', 'c') 

Aggregate functions: AVG, SUM, MIN, MAX, COUNT

Math functions: SQRT

String functions: CHAR_LENGTH, CHARACTER_LENGTH

Examples:
--------
	quantity is 0
	age > 24
	(price > 24) And (count < 100)
	"last name" = 'Smith'
	CURRENT_TIMESTAMP > dob
	CURRENT_TIMESTAMP > '2001-07-04T14:23Z'
	'sarah' IN children
	Not children Is Empty
	name MATCHES 'chaper[ ]+[0-9]+'
	(1, 3, 5) IS NOT EMPTY
	1 IN (1, 2, 3)
	COUNT(3, 5.0, 18) = 3
	


RESERVED KEYWORDS FOR FUTURE USE:
==================================

	POSITION
	OCTET_LENGTH
	BIT_LENGTH
	BIT_LENGTH
	EXTRACT
	SUBSTRING
	UPPER
	LOWER
	CONVERT
	TRANSLATE
	TRIM
	CURRENT_TIME
	NULLIF
	COALESCE
	CASE
	CAST
	BETWEEN
	LIKE
	OVERLAPS

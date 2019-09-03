parser grammar PredicateParser;

options {
	tokenVocab = PredicateLexer;
}

predicate: expression ;

expression:
	NOT inner = expression # notExpr
	| left = expression AND right = expression # andExpr
	| left = expression OR right = expression # orExpr
	| term # termExpr
;

term:
	left = factor op = GREATER_THAN right = factor # compareExpr
	| left = factor op = GREATER_THAN_EQUALS right = factor # compareExpr
	| left = factor op = LESS_THAN right = factor # compareExpr
	| left = factor op = LESS_THAN_EQUALS right = factor # compareExpr
	| left = factor op = EQUALS right = factor # compareExpr
	| left = factor op = NOT_EQUALS right = factor # compareExpr
	| left = factor op = IS NOT right = factor # notEqualExpr
	| left = factor op = IS right = factor # compareExpr
	| left = factor IS EMPTY # emptyExpr
	| left = factor IS NOT EMPTY # notEmptyExpr
	| left = factor op = MATCHES right = factor # matchesExpr
	| factor # factorExpr
	| left = factor op = IN right = factor # inExpr
;

factor:
	NUMERIC_LITERAL # numberExpr
	| CURRENT_TIMESTAMP # currentTimestampExpr
	| CURRENT_DATE # currentDateExpr
	| DATE value = STRING # dateTimeExpr
	| TIMESTAMP value = STRING # dateTimeExpr
	| STRING      # stringExpr
	| TRUE # booleanExpr
	| FALSE # booleanExpr
	| NULL # nullExpr
	| SUM LPAREN inner = inner_list RPAREN #sumExpr
	| AVG LPAREN inner = inner_list RPAREN #avgExpr
	| MIN LPAREN inner = inner_list RPAREN #minExpr
	| MAX LPAREN inner = inner_list RPAREN #maxExpr
	| COUNT LPAREN inner = inner_list RPAREN #countExpr
	| LEN LPAREN inner = factor RPAREN #lenExpr
	| SQRT LPAREN inner = factor RPAREN #sqrtExpr
	| VARIABLE # variableExpr
	| LPAREN expression RPAREN # parenExpr
	| left = factor op = TIMES right = factor # mathExpr
	| left = factor op = DIVIDE right = factor # mathExpr
	| left = factor op = PLUS right = factor # mathExpr
	| left = factor op = MINUS right = factor # mathExpr
	| left = factor op = MODULO right = factor # mathExpr
	| LPAREN item = inner_list RPAREN # wrapListExpr
	| LPAREN RPAREN # wrapListExpr
	| left = factor CONCAT right = factor # strConcatExpr
;



inner_list:
	factor # listFactorExpr
	| left = inner_list COMMA right = inner_list # listCommaExpr
;


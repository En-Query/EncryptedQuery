lexer grammar PredicateLexer;

AND:					A N D;
OR:						O R;
NOT:					N O T;

IS:						I S ;
EMPTY: 					E M P T Y;
IN:						I N ;
MATCHES:				M A T C H E S ;
TRUE:					T R U E ;
FALSE:					F A L S E ;
NULL:					N U L L;

// aggregators
SUM: S U M ;
AVG: A V G ;
MIN: M I N ;
MAX: M A X ;
COUNT: C O U N T;
LEN:  C H A R '_' L E N G T H | C H A R A C T E R '_' L E N G T H ;
SQRT: S Q R T ;
DATE: D A T E ;
TIMESTAMP: T I M E S T A M P ; 

GREATER_THAN:			'>' ;
GREATER_THAN_EQUALS:	'>=' ;
LESS_THAN:				'<' ;
LESS_THAN_EQUALS:		'<=' ;
EQUALS:					'=' ;
NOT_EQUALS:				'<>' ;

// Arithmethic operators SQL92
PLUS:					'+' ;
MINUS:					'-' ;
TIMES:					'*' ;
DIVIDE:					'/' ;
MODULO:					'%' ;

CONCAT:					'||' ;
LPAREN:					'(' ;
RPAREN:					')' ;
COMMA:					',' ;
STRING:					'\'' ~('\r' | '\n' | '\'')* '\'' ;
CURRENT_TIMESTAMP:	C U R R E N T '_' T I M E S T A M P ;
CURRENT_DATE: C U R R E N T '_' D A T E ;


NUMERIC_LITERAL: [-+]? Digit+ ( '.' Digit* )? ( E [-+]? Digit+ )?
 | [-+]? '.' Digit+ ( E [-+]? Digit+ )?
 ;
 
VARIABLE:	//Alpha+ (Alpha | Digit)* 
			'"' (~'"' | '""')* '"'
 			| [a-zA-Z_] [a-zA-Z_0-9]* 
;

fragment Digit: 		[0-9] ;
//fragment Alpha: 		'_' | 'A'..'Z' | 'a'..'z' ;
fragment A : [aA];
fragment B : [bB];
fragment C : [cC];
fragment D : [dD];
fragment E : [eE];
fragment F : [fF];
fragment G : [gG];
fragment H : [hH];
fragment I : [iI];
fragment J : [jJ];
fragment K : [kK];
fragment L : [lL];
fragment M : [mM];
fragment N : [nN];
fragment O : [oO];
fragment P : [pP];
fragment Q : [qQ];
fragment R : [rR];
fragment S : [sS];
fragment T : [tT];
fragment U : [uU];
fragment V : [vV];
fragment W : [wW];
fragment X : [xX];
fragment Y : [yY];
fragment Z : [zZ];

WS: [ \u000B\t\r\n] -> channel(HIDDEN) ;

 
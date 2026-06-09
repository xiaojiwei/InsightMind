grammar IFElse;
gra: stat+;

/**
声明一个语法规则，由 表达式、赋值语句 或者 空行组成
*/
stat
 : if_stat
 | expr
 | OTHER {System.err.println("unknown char: " + $OTHER.text);}
 ;

if_stat
 : IF '('condition_block ',' stat_block ',' stat_block ')'
 ;

condition_block
 : expr
 ;

stat_block
 : expr
 ;

// 表达式定义：表达式由表达式乘除运算、表达式加减运算、数、变量、带括号的变量组成

expr
 : '(' expr ')'                         #brExpr
 | expr POW<assoc=right> expr           #powExpr
 | MINUS expr                           #unaryMinusExpr
 | NOT expr                             #notExpr
 | expr op=(MULT | DIV | MOD) expr      #multiplicationExpr
 | expr op=(PLUS | MINUS) expr          #additiveExpr
 | expr op=(LTEQ | GTEQ | LT | GT) expr #relationalExpr
 | expr op=(EQ | NEQ) expr              #equalityExpr
 | expr AND expr                        #andExpr
 | expr OR expr                         #orExpr
 | atom                                 #atomExpr
 ;

atom
 : OPAR expr CPAR #parExpr
 | (INT | FLOAT)  #numberAtom
 | (TRUE | FALSE) #booleanAtom
 | ID             #idAtom
 | MEASTEXT #meas
 | STRING         #stringAtom
 | NIL            #nilAtom
 ;

// 词的定义
MEASTEXT
 :'[MEAS_'('a' .. 'z')+']'
 ;
OR : '||';
AND : '&&';
EQ : '==';
NEQ : '!=';
GT : '>';
LT : '<';
GTEQ : '>=';
LTEQ : '<=';
PLUS : '+';
MINUS : '-';
MULT : '*';
DIV : '/';
MOD : '%';
POW : '^';
NOT : '!';

SCOL : ';';
OPAR : '(';
CPAR : ')';
OBRACE : '{';
CBRACE : '}';

TRUE : 'true';
FALSE : 'false';
NIL : 'nil';
IF : 'if';
ELSE : 'else';

ID
 : [a-zA-Z_] [a-zA-Z_0-9]*
 ;

INT
 : [0-9]+
 ;

FLOAT
 : [0-9]+ '.' [0-9]*
 | '.' [0-9]+
 ;

STRING
 : '"' (~["\r\n] | '""')* '"'
 ;

COMMENT
 : '#' ~[\r\n]* -> skip
 ;

SPACE
 : [ \t\r\n] -> skip
 ;

OTHER
 : .
 ;
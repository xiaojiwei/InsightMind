grammar LodExpr; // rename to distinguish from Expr.g4

prog:   stat+ ;

stat:   expr NEWLINE                # printExpr
    |   ID '=' expr NEWLINE         # assign
    |   NEWLINE                     # blank
    ;

expr:   meas op=('*'|'/') meas      # MulDiv
    |   meas op=('+'|'-') meas      # AddSub
    |   '(' expr ')'                # parens
    ;

meas: MEA(dims+)(filters+)          # mea
    ;

dims: DIM                           # dim
    ;
filters:'{' dims ops=('>=' | '<=' | 'in' | 'between') STRING '}' # filter
    ;

FILTER
 :'[FILTER_'('-' .. '9' | 'A' .. 'Z' | '_' | 'a' .. 'z')+']'
 ;
MEA
 :'[MEAS_'('-' .. '9' | 'A' .. 'Z' | '_' | 'a' .. 'z')+']'
 ;
DIM
 :'[DIM_'('-' .. '9' | 'A' .. 'Z' | '_' | 'a' .. 'z')+']'
 ;
MUL :   '*' ; // assigns token name to '*' used above in grammar
DIV :   '/' ;
ADD :   '+' ;
SUB :   '-' ;
ID  :   [a-zA-Z]+ ;      // match identifiers
INT :   [0-9]+ ;         // match integers

STRING
    :   '"' ( ESC | ~[\\"] )*? '"'
    |   '\'' ( ESC | ~[\\'] )*? '\''
    ;

fragment
HEXDIGIT : ('0'..'9'|'a'..'f'|'A'..'F') ;

fragment
ESC :   UNICODE_ESCAPE
    |   HEX_ESCAPE
    |   OCTAL_ESCAPE
    ;

fragment
UNICODE_ESCAPE
    :   '\\' 'u' HEXDIGIT HEXDIGIT HEXDIGIT HEXDIGIT
    |   '\\' 'u' '{' HEXDIGIT HEXDIGIT HEXDIGIT HEXDIGIT '}'
    ;

fragment
OCTAL_ESCAPE
    :   '\\' [0-3] [0-7] [0-7]
    |   '\\' [0-7] [0-7]
    |   '\\' [0-7]
    ;

fragment
HEX_ESCAPE
    :   '\\' HEXDIGIT HEXDIGIT?
    ;

NEWLINE:'\r'? '\n' ;     // return newlines to parser (is end-statement signal)
WS  :   [ \t]+ -> skip ; // toss out whitespace

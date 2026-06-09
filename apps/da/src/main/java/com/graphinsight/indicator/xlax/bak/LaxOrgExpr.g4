grammar LaxOrgExpr; // rename to distinguish from Expr.g4

prog:   stat+ ;

stat:   expr                # printExpr
    ;

expr:   expr op=('*'|'/') expr      # MulDiv
    |   expr op=('+'|'-') expr      # AddSub
    |   meas                        # measure
    |   expr '(' exprs ')'          # exprList
    |   '(' expr ')'                # parens
    |   FLOAT                       # float
    ;

exprs: expr (',' expr)*             # exps
    ;

meas: fun=('Calculate' | 'Ttest') '(' (MEA (',' scope=('fixed' | 'exclude')':(' dims ')')* (',' 'filters:(' filters ')')*  | exprs) ')'    # mea
    ;
dims: DIM (',' DIM)*                           # dim
    ;
filters: filter (',' filter)*                # fils
    ;
filter:'(' dims ops=('>=' | '<=' | 'in' | 'between') value ')' # fil
    ;


value: STRING   # vs
    ;

FILTER
 :'[FILTER_'('-' .. '9' | 'A' .. 'Z' | '_' | 'a' .. 'z') + ']'
 ;
MEA
 :'[MEAS_'('-' .. '9' | 'A' .. 'Z' | '_' | 'a' .. 'z') + ']'
 ;
DIM
 :'[DIM_'('-' .. '9' | 'A' .. 'Z' | '_' | 'a' .. 'z') + ']'
 ;
MUL :   '*' ; // assigns token name to '*' used above in grammar
DIV :   '/' ;
ADD :   '+' ;
SUB :   '-' ;
ID  :   [a-zA-Z]+ ;      // match identifiers
INT :   [0-9]+ ;         // match integers
FLOAT
 : [0-9]+ '.' [0-9]*
 | '.' [0-9]+
 ;

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

grammar LaxExpr; // rename to distinguish from Expr.g4

prog:   stat+ ;

stat: expr
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

expr:  expr op=('*'|'/') expr               # mulDiv
    |  expr op=('+'|'-') expr               # addSub
    |  expr op=(LTEQ | GTEQ | LT | GT) expr # relationalExpr
    |  expr op=(EQ | NEQ) expr              # equalityExpr
    |  expr AND expr                        # andExpr
    |  expr OR expr                         # orExpr
    |  if_stat                              # ifStat
    |  atom                                 # atomExpr
    |  fun                                  # funs
    |  expr '(' exprs ')'                   # exprList
    |  '(' expr ')'                         # parens
    |  FLOAT                                # float
    |  STRING                               # string
    |  dims                                 # dimz
    ;

atom
     : (INT | FLOAT)  #numberAtom
     | (TRUE | FALSE) #booleanAtom
     | ID             #idAtom
     | NIL            #nilAtom
     ;

exprs: expr (',' expr)*             # exps
    ;

fun : FUNNAME '(' (meas | exprs | dims | {}) ')' # function
    ;

meas: MEA (',' scope=('fixed' | 'exclude')':(' dims ')')* (',' 'filters:(' filters ')')*   # mea
    ;

dims: DIM (',' DIM)*                           # dim
    ;

filters: filter (',' filter)*                # fils
    ;

filter: dims ops=(GTEQ | LTEQ | IN | BETWEEN) value # fil
    ;

value: STRING # vs
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

IF : 'if';
FUNNAME: 'Calculate' | 'Concatenate' | 'Ttest' | 'Workday' | 'Format' | 'SelectColumns' | 'CDP' | 'ER';

OR : '||';
AND : '&&';
EQ : '==';
NEQ : '!=';
GT : '>';
LT : '<';
GTEQ : '>=';
LTEQ : '<=';
IN : 'in';
BETWEEN : 'between';
TRUE : 'true';
FALSE : 'false';
NIL : 'nil';

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
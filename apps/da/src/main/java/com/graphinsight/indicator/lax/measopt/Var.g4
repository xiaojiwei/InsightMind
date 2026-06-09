grammar Var;
gra: stat+;

/**
声明一个语法规则，由 表达式、赋值语句 或者 空行组成
*/
stat: expr NEWLINE 			#cal
  | VAR '=' expr NEWLINE	#value
  | NEWLINE					#blank
  ;

// 表达式定义：表达式由表达式乘除运算、表达式加减运算、数、变量、带括号的变量组成
expr: expr operator=('*'|'/'|'×'|'÷') expr 	#multiplyAndDivide
  | expr operator=('+'|'-') expr 		#additionAndSubtraction
  | NUM					#num
  | VAR				#var
  | MEASTEXT #meas
  | '(' expr ')'		#brackets
  ;

// 词的定义
VAR: ('a' .. 'z' | 'A' .. 'Z')+;
NUM
 : [0-9]+
 ;
MEASTEXT
 :'[MEAS_'('a' .. 'z')+']'
 ;
ADD : '+';
SUB : '-';
MUL : '*';
MULX: '×';
DIV : '/';
DIVX: '÷';
NEWLINE: '\r'?'\n';

SPACE
   : ' ' -> skip
   ;
// 定义了 grammar 的名字，名字需要与文件名对应
grammar Expression;
@header {
// 定义package
package pers.me.expression.parser;
}

/**
 * parser
 * calc 和 expr 就是定义的语法，会使用到下方定义的词法
 * 注意 # 后面的名字，是可以在后续访问和处理的时候使用的。
 * 一个语法有多种规则的时候可以使用 | 来进行配置。
 */

 calc
     : (expr)* EOF                                    # calculationBlock
     ;
// 四则运算分为了两个非常相似的语句，这样做的原因是为了实现优先级，乘除是优先级高于加减的。
 expr
    : BR_OPEN expr BR_CLOSE                           # expressionWithBr
    | sign=(PLUS|MINUS)? num=(NUMBER|PERCENT_NUMBER)  # expressionNumeric
    | expr op=(TIMES | DIVIDE) expr                   # expressionTimesOrDivide
    | expr op=(PLUS | MINUS) expr                     # expressionPlusOrMinus
   ;

/**
 * lexer
 */

BR_OPEN:   '(';
BR_CLOSE:  ')';
PLUS:      '+';
MINUS:     '-';
TIMES:     '*';
DIVIDE:    '/';
PERCENT:   '%';
POINT:     '.';

// 定义百分数
PERCENT_NUMBER
    : NUMBER ((' ')? PERCENT)
    ;

NUMBER
    : DIGIT+ ( POINT DIGIT+ )?
    ;

DIGIT
   : [0-9]+
   ;

// 定义了空白字符，后面的 skip 是一个特殊的标记，标记空白字符会被忽略
SPACE
   : ' ' -> skip
   ;
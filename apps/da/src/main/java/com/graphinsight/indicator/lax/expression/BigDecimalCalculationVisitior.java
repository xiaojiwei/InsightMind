package com.graphinsight.indicator.lax.expression;

import org.antlr.v4.runtime.Token;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/**
 * Date: 2023/6/1
 * Desc:
 */
public class BigDecimalCalculationVisitior extends ExpressionBaseVisitor<BigDecimal> {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;


    @Override
    public BigDecimal visitCalculationBlock(ExpressionParser.CalculationBlockContext ctx) {
        /**
         * 结束符的处理逻辑
         * 遍历所有的子表达式，计算结果，然后返回
         */
        BigDecimal calcResult = null;
        for (ExpressionParser.ExprContext expr : ctx.expr()) {
            calcResult = visit(expr);
        }
        return calcResult;
    }

    @Override
    public BigDecimal visitExpressionWithBr(ExpressionParser.ExpressionWithBrContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public BigDecimal visitExpressionTimesOrDivide(ExpressionParser.ExpressionTimesOrDivideContext ctx) {
        /**
         * 乘除实现
         */
        BigDecimal left = visit(ctx.expr(0));
        BigDecimal right = visit(ctx.expr(1));
        switch (ctx.op.getType()) {
            case ExpressionLexer.TIMES: return left.multiply(right, MATH_CONTEXT);
            case ExpressionLexer.DIVIDE: return left.divide(right, MATH_CONTEXT);
            default: throw new RuntimeException("unsupported operator type");
        }
    }

    @Override
    public BigDecimal visitExpressionNumeric(ExpressionParser.ExpressionNumericContext ctx) {
        /**
         * 将字符转换为BigDecimal
         */
        BigDecimal numeric = numberOrPercent(ctx.num);
        if (Objects.nonNull(ctx.sign) && ctx.sign.getType() == ExpressionLexer.MINUS) {
            return numeric.negate();
        }
        return numeric;
    }

    @Override
    public BigDecimal visitExpressionPlusOrMinus(ExpressionParser.ExpressionPlusOrMinusContext ctx) {
        /**
         * 乘除实现
         */
        BigDecimal left = visit(ctx.expr(0));
        BigDecimal right = visit(ctx.expr(1));
        switch (ctx.op.getType()) {
            case ExpressionLexer.PLUS: return left.add(right);
            case ExpressionLexer.MINUS: return left.subtract(right);
            default: throw new RuntimeException("unsupported operator type");
        }
    }


    private BigDecimal numberOrPercent(Token num) {
        String numberStr = num.getText();
        switch (num.getType()) {
            case ExpressionLexer.NUMBER: return decimal(numberStr);
            case ExpressionLexer.PERCENT_NUMBER: return decimal(numberStr.substring(0, numberStr.length() - 1).trim()).divide(HUNDRED, MATH_CONTEXT);
            default: throw new RuntimeException("unsupported number type");
        }
    }

    private BigDecimal decimal(String decimalStr) {
        return new BigDecimal(decimalStr);
    }

}

package com.graphinsight.indicator.lax.expression;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.math.BigDecimal;

/**
 * Date: 2023/6/1
 * Desc:
 */
public class Caculator {
    public static BigDecimal execute(String expression) {
        CharStream cs = CharStreams.fromString(expression);
        ExpressionLexer lexer = new ExpressionLexer(cs);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ExpressionParser parser = new ExpressionParser(tokens);
        ExpressionParser.CalcContext context = parser.calc();
        BigDecimalCalculationVisitior visitor = new BigDecimalCalculationVisitior();
        return visitor.visit(context);
    }

    public static void main(String[] args) {
        // String exp = "1 + 2 * (3 - 1)";
        // String exp = "1 + 2 * 50%";
        // String exp = "1.2 / 0.3 + 2 * 50%";
        String exp = "1.2 / 0.3 + 2 * 50% + (1 + (2 / (1 + 3)))";
        BigDecimal execute = execute(exp);
        System.out.println(execute);
    }
}

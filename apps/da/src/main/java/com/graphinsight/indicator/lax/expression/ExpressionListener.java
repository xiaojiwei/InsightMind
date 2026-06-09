// Generated from java-escape by ANTLR 4.11.1

// 定义package
package com.graphinsight.indicator.lax.expression;

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link ExpressionParser}.
 */
public interface ExpressionListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by the {@code calculationBlock}
	 * labeled alternative in {@link ExpressionParser#calc}.
	 * @param ctx the parse tree
	 */
	void enterCalculationBlock(ExpressionParser.CalculationBlockContext ctx);
	/**
	 * Exit a parse tree produced by the {@code calculationBlock}
	 * labeled alternative in {@link ExpressionParser#calc}.
	 * @param ctx the parse tree
	 */
	void exitCalculationBlock(ExpressionParser.CalculationBlockContext ctx);
	/**
	 * Enter a parse tree produced by the {@code expressionWithBr}
	 * labeled alternative in {@link ExpressionParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterExpressionWithBr(ExpressionParser.ExpressionWithBrContext ctx);
	/**
	 * Exit a parse tree produced by the {@code expressionWithBr}
	 * labeled alternative in {@link ExpressionParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitExpressionWithBr(ExpressionParser.ExpressionWithBrContext ctx);
	/**
	 * Enter a parse tree produced by the {@code expressionTimesOrDivide}
	 * labeled alternative in {@link ExpressionParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterExpressionTimesOrDivide(ExpressionParser.ExpressionTimesOrDivideContext ctx);
	/**
	 * Exit a parse tree produced by the {@code expressionTimesOrDivide}
	 * labeled alternative in {@link ExpressionParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitExpressionTimesOrDivide(ExpressionParser.ExpressionTimesOrDivideContext ctx);
	/**
	 * Enter a parse tree produced by the {@code expressionNumeric}
	 * labeled alternative in {@link ExpressionParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterExpressionNumeric(ExpressionParser.ExpressionNumericContext ctx);
	/**
	 * Exit a parse tree produced by the {@code expressionNumeric}
	 * labeled alternative in {@link ExpressionParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitExpressionNumeric(ExpressionParser.ExpressionNumericContext ctx);
	/**
	 * Enter a parse tree produced by the {@code expressionPlusOrMinus}
	 * labeled alternative in {@link ExpressionParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterExpressionPlusOrMinus(ExpressionParser.ExpressionPlusOrMinusContext ctx);
	/**
	 * Exit a parse tree produced by the {@code expressionPlusOrMinus}
	 * labeled alternative in {@link ExpressionParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitExpressionPlusOrMinus(ExpressionParser.ExpressionPlusOrMinusContext ctx);
}
// Generated from java-escape by ANTLR 4.11.1

// 定义package
package com.graphinsight.indicator.lax.expression;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link ExpressionParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface ExpressionVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by the {@code calculationBlock}
	 * labeled alternative in {@link ExpressionParser#calc}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCalculationBlock(ExpressionParser.CalculationBlockContext ctx);
	/**
	 * Visit a parse tree produced by the {@code expressionWithBr}
	 * labeled alternative in {@link ExpressionParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpressionWithBr(ExpressionParser.ExpressionWithBrContext ctx);
	/**
	 * Visit a parse tree produced by the {@code expressionTimesOrDivide}
	 * labeled alternative in {@link ExpressionParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpressionTimesOrDivide(ExpressionParser.ExpressionTimesOrDivideContext ctx);
	/**
	 * Visit a parse tree produced by the {@code expressionNumeric}
	 * labeled alternative in {@link ExpressionParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpressionNumeric(ExpressionParser.ExpressionNumericContext ctx);
	/**
	 * Visit a parse tree produced by the {@code expressionPlusOrMinus}
	 * labeled alternative in {@link ExpressionParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpressionPlusOrMinus(ExpressionParser.ExpressionPlusOrMinusContext ctx);
}
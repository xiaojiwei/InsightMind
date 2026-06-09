// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.xlax.xlod;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link LodExprParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface LodExprVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link LodExprParser#prog}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProg(LodExprParser.ProgContext ctx);
	/**
	 * Visit a parse tree produced by the {@code printExpr}
	 * labeled alternative in {@link LodExprParser#stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrintExpr(LodExprParser.PrintExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code assign}
	 * labeled alternative in {@link LodExprParser#stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssign(LodExprParser.AssignContext ctx);
	/**
	 * Visit a parse tree produced by the {@code blank}
	 * labeled alternative in {@link LodExprParser#stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlank(LodExprParser.BlankContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MulDiv}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMulDiv(LodExprParser.MulDivContext ctx);
	/**
	 * Visit a parse tree produced by the {@code AddSub}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAddSub(LodExprParser.AddSubContext ctx);
	/**
	 * Visit a parse tree produced by the {@code parens}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParens(LodExprParser.ParensContext ctx);
	/**
	 * Visit a parse tree produced by the {@code mea}
	 * labeled alternative in {@link LodExprParser#meas}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMea(LodExprParser.MeaContext ctx);
	/**
	 * Visit a parse tree produced by the {@code dim}
	 * labeled alternative in {@link LodExprParser#dims}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDim(LodExprParser.DimContext ctx);
	/**
	 * Visit a parse tree produced by the {@code filter}
	 * labeled alternative in {@link LodExprParser#filters}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFilter(LodExprParser.FilterContext ctx);
}
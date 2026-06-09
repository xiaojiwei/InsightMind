// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.xlax.lod;
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
	 * Visit a parse tree produced by the {@code parens}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParens(LodExprParser.ParensContext ctx);
	/**
	 * Visit a parse tree produced by the {@code measure}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMeasure(LodExprParser.MeasureContext ctx);
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
	 * Visit a parse tree produced by the {@code mea}
	 * labeled alternative in {@link LodExprParser#meas}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMea(LodExprParser.MeaContext ctx);
	/**
	 * Visit a parse tree produced by the {@code calc}
	 * labeled alternative in {@link LodExprParser#meas}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCalc(LodExprParser.CalcContext ctx);
	/**
	 * Visit a parse tree produced by the {@code dim}
	 * labeled alternative in {@link LodExprParser#dims}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDim(LodExprParser.DimContext ctx);
	/**
	 * Visit a parse tree produced by the {@code fils}
	 * labeled alternative in {@link LodExprParser#filters}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFils(LodExprParser.FilsContext ctx);
	/**
	 * Visit a parse tree produced by the {@code fil}
	 * labeled alternative in {@link LodExprParser#filter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFil(LodExprParser.FilContext ctx);
	/**
	 * Visit a parse tree produced by the {@code vs}
	 * labeled alternative in {@link LodExprParser#value}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVs(LodExprParser.VsContext ctx);
}
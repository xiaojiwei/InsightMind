// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.xlax.lod;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link LodExprParser}.
 */
public interface LodExprListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link LodExprParser#prog}.
	 * @param ctx the parse tree
	 */
	void enterProg(LodExprParser.ProgContext ctx);
	/**
	 * Exit a parse tree produced by {@link LodExprParser#prog}.
	 * @param ctx the parse tree
	 */
	void exitProg(LodExprParser.ProgContext ctx);
	/**
	 * Enter a parse tree produced by the {@code printExpr}
	 * labeled alternative in {@link LodExprParser#stat}.
	 * @param ctx the parse tree
	 */
	void enterPrintExpr(LodExprParser.PrintExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code printExpr}
	 * labeled alternative in {@link LodExprParser#stat}.
	 * @param ctx the parse tree
	 */
	void exitPrintExpr(LodExprParser.PrintExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code parens}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterParens(LodExprParser.ParensContext ctx);
	/**
	 * Exit a parse tree produced by the {@code parens}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitParens(LodExprParser.ParensContext ctx);
	/**
	 * Enter a parse tree produced by the {@code measure}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterMeasure(LodExprParser.MeasureContext ctx);
	/**
	 * Exit a parse tree produced by the {@code measure}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitMeasure(LodExprParser.MeasureContext ctx);
	/**
	 * Enter a parse tree produced by the {@code MulDiv}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterMulDiv(LodExprParser.MulDivContext ctx);
	/**
	 * Exit a parse tree produced by the {@code MulDiv}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitMulDiv(LodExprParser.MulDivContext ctx);
	/**
	 * Enter a parse tree produced by the {@code AddSub}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAddSub(LodExprParser.AddSubContext ctx);
	/**
	 * Exit a parse tree produced by the {@code AddSub}
	 * labeled alternative in {@link LodExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAddSub(LodExprParser.AddSubContext ctx);
	/**
	 * Enter a parse tree produced by the {@code mea}
	 * labeled alternative in {@link LodExprParser#meas}.
	 * @param ctx the parse tree
	 */
	void enterMea(LodExprParser.MeaContext ctx);
	/**
	 * Exit a parse tree produced by the {@code mea}
	 * labeled alternative in {@link LodExprParser#meas}.
	 * @param ctx the parse tree
	 */
	void exitMea(LodExprParser.MeaContext ctx);
	/**
	 * Enter a parse tree produced by the {@code calc}
	 * labeled alternative in {@link LodExprParser#meas}.
	 * @param ctx the parse tree
	 */
	void enterCalc(LodExprParser.CalcContext ctx);
	/**
	 * Exit a parse tree produced by the {@code calc}
	 * labeled alternative in {@link LodExprParser#meas}.
	 * @param ctx the parse tree
	 */
	void exitCalc(LodExprParser.CalcContext ctx);
	/**
	 * Enter a parse tree produced by the {@code dim}
	 * labeled alternative in {@link LodExprParser#dims}.
	 * @param ctx the parse tree
	 */
	void enterDim(LodExprParser.DimContext ctx);
	/**
	 * Exit a parse tree produced by the {@code dim}
	 * labeled alternative in {@link LodExprParser#dims}.
	 * @param ctx the parse tree
	 */
	void exitDim(LodExprParser.DimContext ctx);
	/**
	 * Enter a parse tree produced by the {@code fils}
	 * labeled alternative in {@link LodExprParser#filters}.
	 * @param ctx the parse tree
	 */
	void enterFils(LodExprParser.FilsContext ctx);
	/**
	 * Exit a parse tree produced by the {@code fils}
	 * labeled alternative in {@link LodExprParser#filters}.
	 * @param ctx the parse tree
	 */
	void exitFils(LodExprParser.FilsContext ctx);
	/**
	 * Enter a parse tree produced by the {@code fil}
	 * labeled alternative in {@link LodExprParser#filter}.
	 * @param ctx the parse tree
	 */
	void enterFil(LodExprParser.FilContext ctx);
	/**
	 * Exit a parse tree produced by the {@code fil}
	 * labeled alternative in {@link LodExprParser#filter}.
	 * @param ctx the parse tree
	 */
	void exitFil(LodExprParser.FilContext ctx);
	/**
	 * Enter a parse tree produced by the {@code vs}
	 * labeled alternative in {@link LodExprParser#value}.
	 * @param ctx the parse tree
	 */
	void enterVs(LodExprParser.VsContext ctx);
	/**
	 * Exit a parse tree produced by the {@code vs}
	 * labeled alternative in {@link LodExprParser#value}.
	 * @param ctx the parse tree
	 */
	void exitVs(LodExprParser.VsContext ctx);
}
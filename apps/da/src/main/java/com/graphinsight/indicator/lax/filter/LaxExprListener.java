// Generated from /Users/xiaojiwei/indicator/indicator/src/main/java/com/graphinsight/indicator/lax/filter/LaxExpr.g4 by ANTLR 4.13.1
package com.graphinsight.indicator.lax.filter;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link LaxExprParser}.
 */
public interface LaxExprListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link LaxExprParser#prog}.
	 * @param ctx the parse tree
	 */
	void enterProg(LaxExprParser.ProgContext ctx);
	/**
	 * Exit a parse tree produced by {@link LaxExprParser#prog}.
	 * @param ctx the parse tree
	 */
	void exitProg(LaxExprParser.ProgContext ctx);
	/**
	 * Enter a parse tree produced by {@link LaxExprParser#stat}.
	 * @param ctx the parse tree
	 */
	void enterStat(LaxExprParser.StatContext ctx);
	/**
	 * Exit a parse tree produced by {@link LaxExprParser#stat}.
	 * @param ctx the parse tree
	 */
	void exitStat(LaxExprParser.StatContext ctx);
	/**
	 * Enter a parse tree produced by {@link LaxExprParser#if_stat}.
	 * @param ctx the parse tree
	 */
	void enterIf_stat(LaxExprParser.If_statContext ctx);
	/**
	 * Exit a parse tree produced by {@link LaxExprParser#if_stat}.
	 * @param ctx the parse tree
	 */
	void exitIf_stat(LaxExprParser.If_statContext ctx);
	/**
	 * Enter a parse tree produced by {@link LaxExprParser#condition_block}.
	 * @param ctx the parse tree
	 */
	void enterCondition_block(LaxExprParser.Condition_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link LaxExprParser#condition_block}.
	 * @param ctx the parse tree
	 */
	void exitCondition_block(LaxExprParser.Condition_blockContext ctx);
	/**
	 * Enter a parse tree produced by {@link LaxExprParser#stat_block}.
	 * @param ctx the parse tree
	 */
	void enterStat_block(LaxExprParser.Stat_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link LaxExprParser#stat_block}.
	 * @param ctx the parse tree
	 */
	void exitStat_block(LaxExprParser.Stat_blockContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ifStat}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterIfStat(LaxExprParser.IfStatContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ifStat}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitIfStat(LaxExprParser.IfStatContext ctx);
	/**
	 * Enter a parse tree produced by the {@code parens}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterParens(LaxExprParser.ParensContext ctx);
	/**
	 * Exit a parse tree produced by the {@code parens}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitParens(LaxExprParser.ParensContext ctx);
	/**
	 * Enter a parse tree produced by the {@code funs}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterFuns(LaxExprParser.FunsContext ctx);
	/**
	 * Exit a parse tree produced by the {@code funs}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitFuns(LaxExprParser.FunsContext ctx);
	/**
	 * Enter a parse tree produced by the {@code string}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterString(LaxExprParser.StringContext ctx);
	/**
	 * Exit a parse tree produced by the {@code string}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitString(LaxExprParser.StringContext ctx);
	/**
	 * Enter a parse tree produced by the {@code atomExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAtomExpr(LaxExprParser.AtomExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code atomExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAtomExpr(LaxExprParser.AtomExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code orExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterOrExpr(LaxExprParser.OrExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code orExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitOrExpr(LaxExprParser.OrExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code addSub}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAddSub(LaxExprParser.AddSubContext ctx);
	/**
	 * Exit a parse tree produced by the {@code addSub}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAddSub(LaxExprParser.AddSubContext ctx);
	/**
	 * Enter a parse tree produced by the {@code float}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterFloat(LaxExprParser.FloatContext ctx);
	/**
	 * Exit a parse tree produced by the {@code float}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitFloat(LaxExprParser.FloatContext ctx);
	/**
	 * Enter a parse tree produced by the {@code relationalExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterRelationalExpr(LaxExprParser.RelationalExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code relationalExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitRelationalExpr(LaxExprParser.RelationalExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code mulDiv}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterMulDiv(LaxExprParser.MulDivContext ctx);
	/**
	 * Exit a parse tree produced by the {@code mulDiv}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitMulDiv(LaxExprParser.MulDivContext ctx);
	/**
	 * Enter a parse tree produced by the {@code exprList}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterExprList(LaxExprParser.ExprListContext ctx);
	/**
	 * Exit a parse tree produced by the {@code exprList}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitExprList(LaxExprParser.ExprListContext ctx);
	/**
	 * Enter a parse tree produced by the {@code dimz}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterDimz(LaxExprParser.DimzContext ctx);
	/**
	 * Exit a parse tree produced by the {@code dimz}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitDimz(LaxExprParser.DimzContext ctx);
	/**
	 * Enter a parse tree produced by the {@code equalityExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterEqualityExpr(LaxExprParser.EqualityExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code equalityExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitEqualityExpr(LaxExprParser.EqualityExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code andExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAndExpr(LaxExprParser.AndExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code andExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAndExpr(LaxExprParser.AndExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code numberAtom}
	 * labeled alternative in {@link LaxExprParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterNumberAtom(LaxExprParser.NumberAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code numberAtom}
	 * labeled alternative in {@link LaxExprParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitNumberAtom(LaxExprParser.NumberAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code booleanAtom}
	 * labeled alternative in {@link LaxExprParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterBooleanAtom(LaxExprParser.BooleanAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code booleanAtom}
	 * labeled alternative in {@link LaxExprParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitBooleanAtom(LaxExprParser.BooleanAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code idAtom}
	 * labeled alternative in {@link LaxExprParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterIdAtom(LaxExprParser.IdAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code idAtom}
	 * labeled alternative in {@link LaxExprParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitIdAtom(LaxExprParser.IdAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code nilAtom}
	 * labeled alternative in {@link LaxExprParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterNilAtom(LaxExprParser.NilAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code nilAtom}
	 * labeled alternative in {@link LaxExprParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitNilAtom(LaxExprParser.NilAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code exps}
	 * labeled alternative in {@link LaxExprParser#exprs}.
	 * @param ctx the parse tree
	 */
	void enterExps(LaxExprParser.ExpsContext ctx);
	/**
	 * Exit a parse tree produced by the {@code exps}
	 * labeled alternative in {@link LaxExprParser#exprs}.
	 * @param ctx the parse tree
	 */
	void exitExps(LaxExprParser.ExpsContext ctx);
	/**
	 * Enter a parse tree produced by the {@code function}
	 * labeled alternative in {@link LaxExprParser#fun}.
	 * @param ctx the parse tree
	 */
	void enterFunction(LaxExprParser.FunctionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code function}
	 * labeled alternative in {@link LaxExprParser#fun}.
	 * @param ctx the parse tree
	 */
	void exitFunction(LaxExprParser.FunctionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code mea}
	 * labeled alternative in {@link LaxExprParser#meas}.
	 * @param ctx the parse tree
	 */
	void enterMea(LaxExprParser.MeaContext ctx);
	/**
	 * Exit a parse tree produced by the {@code mea}
	 * labeled alternative in {@link LaxExprParser#meas}.
	 * @param ctx the parse tree
	 */
	void exitMea(LaxExprParser.MeaContext ctx);
	/**
	 * Enter a parse tree produced by the {@code dim}
	 * labeled alternative in {@link LaxExprParser#dims}.
	 * @param ctx the parse tree
	 */
	void enterDim(LaxExprParser.DimContext ctx);
	/**
	 * Exit a parse tree produced by the {@code dim}
	 * labeled alternative in {@link LaxExprParser#dims}.
	 * @param ctx the parse tree
	 */
	void exitDim(LaxExprParser.DimContext ctx);
	/**
	 * Enter a parse tree produced by the {@code fils}
	 * labeled alternative in {@link LaxExprParser#filters}.
	 * @param ctx the parse tree
	 */
	void enterFils(LaxExprParser.FilsContext ctx);
	/**
	 * Exit a parse tree produced by the {@code fils}
	 * labeled alternative in {@link LaxExprParser#filters}.
	 * @param ctx the parse tree
	 */
	void exitFils(LaxExprParser.FilsContext ctx);
	/**
	 * Enter a parse tree produced by the {@code fil}
	 * labeled alternative in {@link LaxExprParser#filter}.
	 * @param ctx the parse tree
	 */
	void enterFil(LaxExprParser.FilContext ctx);
	/**
	 * Exit a parse tree produced by the {@code fil}
	 * labeled alternative in {@link LaxExprParser#filter}.
	 * @param ctx the parse tree
	 */
	void exitFil(LaxExprParser.FilContext ctx);
	/**
	 * Enter a parse tree produced by the {@code vs}
	 * labeled alternative in {@link LaxExprParser#value}.
	 * @param ctx the parse tree
	 */
	void enterVs(LaxExprParser.VsContext ctx);
	/**
	 * Exit a parse tree produced by the {@code vs}
	 * labeled alternative in {@link LaxExprParser#value}.
	 * @param ctx the parse tree
	 */
	void exitVs(LaxExprParser.VsContext ctx);
}
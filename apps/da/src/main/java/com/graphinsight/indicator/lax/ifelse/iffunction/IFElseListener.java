// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.lax.ifelse.iffunction;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link IFElseParser}.
 */
public interface IFElseListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link IFElseParser#gra}.
	 * @param ctx the parse tree
	 */
	void enterGra(IFElseParser.GraContext ctx);
	/**
	 * Exit a parse tree produced by {@link IFElseParser#gra}.
	 * @param ctx the parse tree
	 */
	void exitGra(IFElseParser.GraContext ctx);
	/**
	 * Enter a parse tree produced by {@link IFElseParser#stat}.
	 * @param ctx the parse tree
	 */
	void enterStat(IFElseParser.StatContext ctx);
	/**
	 * Exit a parse tree produced by {@link IFElseParser#stat}.
	 * @param ctx the parse tree
	 */
	void exitStat(IFElseParser.StatContext ctx);
	/**
	 * Enter a parse tree produced by {@link IFElseParser#if_stat}.
	 * @param ctx the parse tree
	 */
	void enterIf_stat(IFElseParser.If_statContext ctx);
	/**
	 * Exit a parse tree produced by {@link IFElseParser#if_stat}.
	 * @param ctx the parse tree
	 */
	void exitIf_stat(IFElseParser.If_statContext ctx);
	/**
	 * Enter a parse tree produced by {@link IFElseParser#condition_block}.
	 * @param ctx the parse tree
	 */
	void enterCondition_block(IFElseParser.Condition_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link IFElseParser#condition_block}.
	 * @param ctx the parse tree
	 */
	void exitCondition_block(IFElseParser.Condition_blockContext ctx);
	/**
	 * Enter a parse tree produced by {@link IFElseParser#stat_block}.
	 * @param ctx the parse tree
	 */
	void enterStat_block(IFElseParser.Stat_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link IFElseParser#stat_block}.
	 * @param ctx the parse tree
	 */
	void exitStat_block(IFElseParser.Stat_blockContext ctx);
	/**
	 * Enter a parse tree produced by the {@code notExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterNotExpr(IFElseParser.NotExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code notExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitNotExpr(IFElseParser.NotExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code unaryMinusExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterUnaryMinusExpr(IFElseParser.UnaryMinusExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code unaryMinusExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitUnaryMinusExpr(IFElseParser.UnaryMinusExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code brExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterBrExpr(IFElseParser.BrExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code brExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitBrExpr(IFElseParser.BrExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code multiplicationExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterMultiplicationExpr(IFElseParser.MultiplicationExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code multiplicationExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitMultiplicationExpr(IFElseParser.MultiplicationExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code atomExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAtomExpr(IFElseParser.AtomExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code atomExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAtomExpr(IFElseParser.AtomExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code orExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterOrExpr(IFElseParser.OrExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code orExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitOrExpr(IFElseParser.OrExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code additiveExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAdditiveExpr(IFElseParser.AdditiveExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code additiveExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAdditiveExpr(IFElseParser.AdditiveExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code powExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterPowExpr(IFElseParser.PowExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code powExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitPowExpr(IFElseParser.PowExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code relationalExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterRelationalExpr(IFElseParser.RelationalExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code relationalExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitRelationalExpr(IFElseParser.RelationalExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code equalityExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterEqualityExpr(IFElseParser.EqualityExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code equalityExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitEqualityExpr(IFElseParser.EqualityExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code andExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAndExpr(IFElseParser.AndExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code andExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAndExpr(IFElseParser.AndExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code parExpr}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterParExpr(IFElseParser.ParExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code parExpr}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitParExpr(IFElseParser.ParExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code numberAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterNumberAtom(IFElseParser.NumberAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code numberAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitNumberAtom(IFElseParser.NumberAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code booleanAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterBooleanAtom(IFElseParser.BooleanAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code booleanAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitBooleanAtom(IFElseParser.BooleanAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code idAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterIdAtom(IFElseParser.IdAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code idAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitIdAtom(IFElseParser.IdAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code meas}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterMeas(IFElseParser.MeasContext ctx);
	/**
	 * Exit a parse tree produced by the {@code meas}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitMeas(IFElseParser.MeasContext ctx);
	/**
	 * Enter a parse tree produced by the {@code stringAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterStringAtom(IFElseParser.StringAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code stringAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitStringAtom(IFElseParser.StringAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code nilAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterNilAtom(IFElseParser.NilAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code nilAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitNilAtom(IFElseParser.NilAtomContext ctx);
}
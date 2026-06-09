// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.lax.ifelse.mubak;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link MUParser}.
 */
public interface MUListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link MUParser#parse}.
	 * @param ctx the parse tree
	 */
	void enterParse(MUParser.ParseContext ctx);
	/**
	 * Exit a parse tree produced by {@link MUParser#parse}.
	 * @param ctx the parse tree
	 */
	void exitParse(MUParser.ParseContext ctx);
	/**
	 * Enter a parse tree produced by {@link MUParser#block}.
	 * @param ctx the parse tree
	 */
	void enterBlock(MUParser.BlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link MUParser#block}.
	 * @param ctx the parse tree
	 */
	void exitBlock(MUParser.BlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link MUParser#stat}.
	 * @param ctx the parse tree
	 */
	void enterStat(MUParser.StatContext ctx);
	/**
	 * Exit a parse tree produced by {@link MUParser#stat}.
	 * @param ctx the parse tree
	 */
	void exitStat(MUParser.StatContext ctx);
	/**
	 * Enter a parse tree produced by {@link MUParser#assignment}.
	 * @param ctx the parse tree
	 */
	void enterAssignment(MUParser.AssignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link MUParser#assignment}.
	 * @param ctx the parse tree
	 */
	void exitAssignment(MUParser.AssignmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link MUParser#if_stat}.
	 * @param ctx the parse tree
	 */
	void enterIf_stat(MUParser.If_statContext ctx);
	/**
	 * Exit a parse tree produced by {@link MUParser#if_stat}.
	 * @param ctx the parse tree
	 */
	void exitIf_stat(MUParser.If_statContext ctx);
	/**
	 * Enter a parse tree produced by {@link MUParser#condition_block}.
	 * @param ctx the parse tree
	 */
	void enterCondition_block(MUParser.Condition_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link MUParser#condition_block}.
	 * @param ctx the parse tree
	 */
	void exitCondition_block(MUParser.Condition_blockContext ctx);
	/**
	 * Enter a parse tree produced by {@link MUParser#stat_block}.
	 * @param ctx the parse tree
	 */
	void enterStat_block(MUParser.Stat_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link MUParser#stat_block}.
	 * @param ctx the parse tree
	 */
	void exitStat_block(MUParser.Stat_blockContext ctx);
	/**
	 * Enter a parse tree produced by {@link MUParser#while_stat}.
	 * @param ctx the parse tree
	 */
	void enterWhile_stat(MUParser.While_statContext ctx);
	/**
	 * Exit a parse tree produced by {@link MUParser#while_stat}.
	 * @param ctx the parse tree
	 */
	void exitWhile_stat(MUParser.While_statContext ctx);
	/**
	 * Enter a parse tree produced by {@link MUParser#log}.
	 * @param ctx the parse tree
	 */
	void enterLog(MUParser.LogContext ctx);
	/**
	 * Exit a parse tree produced by {@link MUParser#log}.
	 * @param ctx the parse tree
	 */
	void exitLog(MUParser.LogContext ctx);
	/**
	 * Enter a parse tree produced by the {@code notExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterNotExpr(MUParser.NotExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code notExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitNotExpr(MUParser.NotExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code unaryMinusExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterUnaryMinusExpr(MUParser.UnaryMinusExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code unaryMinusExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitUnaryMinusExpr(MUParser.UnaryMinusExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code multiplicationExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterMultiplicationExpr(MUParser.MultiplicationExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code multiplicationExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitMultiplicationExpr(MUParser.MultiplicationExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code atomExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAtomExpr(MUParser.AtomExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code atomExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAtomExpr(MUParser.AtomExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code orExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterOrExpr(MUParser.OrExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code orExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitOrExpr(MUParser.OrExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code additiveExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAdditiveExpr(MUParser.AdditiveExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code additiveExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAdditiveExpr(MUParser.AdditiveExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code powExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterPowExpr(MUParser.PowExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code powExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitPowExpr(MUParser.PowExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code relationalExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterRelationalExpr(MUParser.RelationalExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code relationalExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitRelationalExpr(MUParser.RelationalExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code equalityExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterEqualityExpr(MUParser.EqualityExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code equalityExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitEqualityExpr(MUParser.EqualityExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code andExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAndExpr(MUParser.AndExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code andExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAndExpr(MUParser.AndExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code parExpr}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterParExpr(MUParser.ParExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code parExpr}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitParExpr(MUParser.ParExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code numberAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterNumberAtom(MUParser.NumberAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code numberAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitNumberAtom(MUParser.NumberAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code booleanAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterBooleanAtom(MUParser.BooleanAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code booleanAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitBooleanAtom(MUParser.BooleanAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code idAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterIdAtom(MUParser.IdAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code idAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitIdAtom(MUParser.IdAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code stringAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterStringAtom(MUParser.StringAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code stringAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitStringAtom(MUParser.StringAtomContext ctx);
	/**
	 * Enter a parse tree produced by the {@code nilAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterNilAtom(MUParser.NilAtomContext ctx);
	/**
	 * Exit a parse tree produced by the {@code nilAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitNilAtom(MUParser.NilAtomContext ctx);
}
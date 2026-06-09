// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.lax.ifelse.mu;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link MUParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface MUVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link MUParser#parse}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParse(MUParser.ParseContext ctx);
	/**
	 * Visit a parse tree produced by {@link MUParser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(MUParser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link MUParser#stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStat(MUParser.StatContext ctx);
	/**
	 * Visit a parse tree produced by {@link MUParser#if_stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIf_stat(MUParser.If_statContext ctx);
	/**
	 * Visit a parse tree produced by {@link MUParser#condition_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondition_block(MUParser.Condition_blockContext ctx);
	/**
	 * Visit a parse tree produced by {@link MUParser#stat_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStat_block(MUParser.Stat_blockContext ctx);
	/**
	 * Visit a parse tree produced by the {@code notExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNotExpr(MUParser.NotExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code unaryMinusExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryMinusExpr(MUParser.UnaryMinusExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code multiplicationExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMultiplicationExpr(MUParser.MultiplicationExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code atomExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAtomExpr(MUParser.AtomExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code orExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOrExpr(MUParser.OrExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code additiveExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdditiveExpr(MUParser.AdditiveExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code powExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPowExpr(MUParser.PowExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code relationalExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelationalExpr(MUParser.RelationalExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code equalityExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEqualityExpr(MUParser.EqualityExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code andExpr}
	 * labeled alternative in {@link MUParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAndExpr(MUParser.AndExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code parExpr}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParExpr(MUParser.ParExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code numberAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNumberAtom(MUParser.NumberAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code booleanAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBooleanAtom(MUParser.BooleanAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code idAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdAtom(MUParser.IdAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code stringAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStringAtom(MUParser.StringAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code nilAtom}
	 * labeled alternative in {@link MUParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNilAtom(MUParser.NilAtomContext ctx);
}
// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.lax.ifelse.iffunction;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link IFElseParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface IFElseVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link IFElseParser#gra}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGra(IFElseParser.GraContext ctx);
	/**
	 * Visit a parse tree produced by {@link IFElseParser#stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStat(IFElseParser.StatContext ctx);
	/**
	 * Visit a parse tree produced by {@link IFElseParser#if_stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIf_stat(IFElseParser.If_statContext ctx);
	/**
	 * Visit a parse tree produced by {@link IFElseParser#condition_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondition_block(IFElseParser.Condition_blockContext ctx);
	/**
	 * Visit a parse tree produced by {@link IFElseParser#stat_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStat_block(IFElseParser.Stat_blockContext ctx);
	/**
	 * Visit a parse tree produced by the {@code notExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNotExpr(IFElseParser.NotExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code unaryMinusExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryMinusExpr(IFElseParser.UnaryMinusExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code brExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBrExpr(IFElseParser.BrExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code multiplicationExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMultiplicationExpr(IFElseParser.MultiplicationExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code atomExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAtomExpr(IFElseParser.AtomExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code orExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOrExpr(IFElseParser.OrExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code additiveExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdditiveExpr(IFElseParser.AdditiveExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code powExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPowExpr(IFElseParser.PowExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code relationalExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelationalExpr(IFElseParser.RelationalExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code equalityExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEqualityExpr(IFElseParser.EqualityExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code andExpr}
	 * labeled alternative in {@link IFElseParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAndExpr(IFElseParser.AndExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code parExpr}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParExpr(IFElseParser.ParExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code numberAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNumberAtom(IFElseParser.NumberAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code booleanAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBooleanAtom(IFElseParser.BooleanAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code idAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdAtom(IFElseParser.IdAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code meas}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMeas(IFElseParser.MeasContext ctx);
	/**
	 * Visit a parse tree produced by the {@code stringAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStringAtom(IFElseParser.StringAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code nilAtom}
	 * labeled alternative in {@link IFElseParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNilAtom(IFElseParser.NilAtomContext ctx);
}
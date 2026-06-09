// Generated from /Users/xiaojiwei/indicator/indicator/src/main/java/com/graphinsight/indicator/lax/filter/LaxExpr.g4 by ANTLR 4.13.1
package com.graphinsight.indicator.lax.filter;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link LaxExprParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface LaxExprVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link LaxExprParser#prog}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProg(LaxExprParser.ProgContext ctx);
	/**
	 * Visit a parse tree produced by {@link LaxExprParser#stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStat(LaxExprParser.StatContext ctx);
	/**
	 * Visit a parse tree produced by {@link LaxExprParser#if_stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIf_stat(LaxExprParser.If_statContext ctx);
	/**
	 * Visit a parse tree produced by {@link LaxExprParser#condition_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondition_block(LaxExprParser.Condition_blockContext ctx);
	/**
	 * Visit a parse tree produced by {@link LaxExprParser#stat_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStat_block(LaxExprParser.Stat_blockContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ifStat}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfStat(LaxExprParser.IfStatContext ctx);
	/**
	 * Visit a parse tree produced by the {@code parens}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParens(LaxExprParser.ParensContext ctx);
	/**
	 * Visit a parse tree produced by the {@code funs}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuns(LaxExprParser.FunsContext ctx);
	/**
	 * Visit a parse tree produced by the {@code string}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitString(LaxExprParser.StringContext ctx);
	/**
	 * Visit a parse tree produced by the {@code atomExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAtomExpr(LaxExprParser.AtomExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code orExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOrExpr(LaxExprParser.OrExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code addSub}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAddSub(LaxExprParser.AddSubContext ctx);
	/**
	 * Visit a parse tree produced by the {@code float}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFloat(LaxExprParser.FloatContext ctx);
	/**
	 * Visit a parse tree produced by the {@code relationalExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelationalExpr(LaxExprParser.RelationalExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code mulDiv}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMulDiv(LaxExprParser.MulDivContext ctx);
	/**
	 * Visit a parse tree produced by the {@code exprList}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExprList(LaxExprParser.ExprListContext ctx);
	/**
	 * Visit a parse tree produced by the {@code dimz}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDimz(LaxExprParser.DimzContext ctx);
	/**
	 * Visit a parse tree produced by the {@code equalityExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEqualityExpr(LaxExprParser.EqualityExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code andExpr}
	 * labeled alternative in {@link LaxExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAndExpr(LaxExprParser.AndExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code numberAtom}
	 * labeled alternative in {@link LaxExprParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNumberAtom(LaxExprParser.NumberAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code booleanAtom}
	 * labeled alternative in {@link LaxExprParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBooleanAtom(LaxExprParser.BooleanAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code idAtom}
	 * labeled alternative in {@link LaxExprParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdAtom(LaxExprParser.IdAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code nilAtom}
	 * labeled alternative in {@link LaxExprParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNilAtom(LaxExprParser.NilAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code exps}
	 * labeled alternative in {@link LaxExprParser#exprs}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExps(LaxExprParser.ExpsContext ctx);
	/**
	 * Visit a parse tree produced by the {@code function}
	 * labeled alternative in {@link LaxExprParser#fun}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunction(LaxExprParser.FunctionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code mea}
	 * labeled alternative in {@link LaxExprParser#meas}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMea(LaxExprParser.MeaContext ctx);
	/**
	 * Visit a parse tree produced by the {@code dim}
	 * labeled alternative in {@link LaxExprParser#dims}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDim(LaxExprParser.DimContext ctx);
	/**
	 * Visit a parse tree produced by the {@code fils}
	 * labeled alternative in {@link LaxExprParser#filters}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFils(LaxExprParser.FilsContext ctx);
	/**
	 * Visit a parse tree produced by the {@code fil}
	 * labeled alternative in {@link LaxExprParser#filter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFil(LaxExprParser.FilContext ctx);
	/**
	 * Visit a parse tree produced by the {@code vs}
	 * labeled alternative in {@link LaxExprParser#value}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVs(LaxExprParser.VsContext ctx);
}
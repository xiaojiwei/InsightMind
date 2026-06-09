// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.lax.var;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link VarParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface VarVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link VarParser#gra}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGra(VarParser.GraContext ctx);
	/**
	 * Visit a parse tree produced by the {@code cal}
	 * labeled alternative in {@link VarParser#stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCal(VarParser.CalContext ctx);
	/**
	 * Visit a parse tree produced by the {@code value}
	 * labeled alternative in {@link VarParser#stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValue(VarParser.ValueContext ctx);
	/**
	 * Visit a parse tree produced by the {@code blank}
	 * labeled alternative in {@link VarParser#stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlank(VarParser.BlankContext ctx);
	/**
	 * Visit a parse tree produced by the {@code additionAndSubtraction}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdditionAndSubtraction(VarParser.AdditionAndSubtractionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code multiplyAndDivide}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMultiplyAndDivide(VarParser.MultiplyAndDivideContext ctx);
	/**
	 * Visit a parse tree produced by the {@code var}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVar(VarParser.VarContext ctx);
	/**
	 * Visit a parse tree produced by the {@code num}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNum(VarParser.NumContext ctx);
	/**
	 * Visit a parse tree produced by the {@code brackets}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBrackets(VarParser.BracketsContext ctx);
}
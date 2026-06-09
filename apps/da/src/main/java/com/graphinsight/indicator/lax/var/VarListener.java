// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.lax.var;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link VarParser}.
 */
public interface VarListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link VarParser#gra}.
	 * @param ctx the parse tree
	 */
	void enterGra(VarParser.GraContext ctx);
	/**
	 * Exit a parse tree produced by {@link VarParser#gra}.
	 * @param ctx the parse tree
	 */
	void exitGra(VarParser.GraContext ctx);
	/**
	 * Enter a parse tree produced by the {@code cal}
	 * labeled alternative in {@link VarParser#stat}.
	 * @param ctx the parse tree
	 */
	void enterCal(VarParser.CalContext ctx);
	/**
	 * Exit a parse tree produced by the {@code cal}
	 * labeled alternative in {@link VarParser#stat}.
	 * @param ctx the parse tree
	 */
	void exitCal(VarParser.CalContext ctx);
	/**
	 * Enter a parse tree produced by the {@code value}
	 * labeled alternative in {@link VarParser#stat}.
	 * @param ctx the parse tree
	 */
	void enterValue(VarParser.ValueContext ctx);
	/**
	 * Exit a parse tree produced by the {@code value}
	 * labeled alternative in {@link VarParser#stat}.
	 * @param ctx the parse tree
	 */
	void exitValue(VarParser.ValueContext ctx);
	/**
	 * Enter a parse tree produced by the {@code blank}
	 * labeled alternative in {@link VarParser#stat}.
	 * @param ctx the parse tree
	 */
	void enterBlank(VarParser.BlankContext ctx);
	/**
	 * Exit a parse tree produced by the {@code blank}
	 * labeled alternative in {@link VarParser#stat}.
	 * @param ctx the parse tree
	 */
	void exitBlank(VarParser.BlankContext ctx);
	/**
	 * Enter a parse tree produced by the {@code additionAndSubtraction}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAdditionAndSubtraction(VarParser.AdditionAndSubtractionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code additionAndSubtraction}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAdditionAndSubtraction(VarParser.AdditionAndSubtractionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code multiplyAndDivide}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterMultiplyAndDivide(VarParser.MultiplyAndDivideContext ctx);
	/**
	 * Exit a parse tree produced by the {@code multiplyAndDivide}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitMultiplyAndDivide(VarParser.MultiplyAndDivideContext ctx);
	/**
	 * Enter a parse tree produced by the {@code var}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterVar(VarParser.VarContext ctx);
	/**
	 * Exit a parse tree produced by the {@code var}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitVar(VarParser.VarContext ctx);
	/**
	 * Enter a parse tree produced by the {@code num}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterNum(VarParser.NumContext ctx);
	/**
	 * Exit a parse tree produced by the {@code num}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitNum(VarParser.NumContext ctx);
	/**
	 * Enter a parse tree produced by the {@code brackets}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterBrackets(VarParser.BracketsContext ctx);
	/**
	 * Exit a parse tree produced by the {@code brackets}
	 * labeled alternative in {@link VarParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitBrackets(VarParser.BracketsContext ctx);
}
package com.graphinsight.indicator.lax.measopt;

import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Date: 2023/6/5
 * Desc:
 */
public class CusVarVisitor extends VarBaseVisitor<Node> {
    private static Map<String, Node> varTable = new HashMap<>();

    private Map<String, Object> dataMap;

    public CusVarVisitor(Map<String, Object> dataMap) {
        this.dataMap = dataMap;
    }

    public CusVarVisitor() {
    }

    /**
     * ******************************声明部分******************************
     */
    /**
     * 赋值操作
     * 就是把变量及变量的值放到变量表里
     * 变量名就是text，变量值就是表达式的值，对应expr的定义
     * 比如 定义 a=1
     * 对应文法文件中的 expr: NUM规则
     * @param ctx
     * @return
     */
    @Override
    public Node visitValue(VarParser.ValueContext ctx) {
        String var = ctx.VAR().getText();
        varTable.put(var,visit(ctx.expr()));
        return null;
    }


    /**
     * 求值操作
     * 求值操作类似赋值操作，对应expr的定义
     * @param ctx
     * @return
     */
    @Override
    public Node visitCal(VarParser.CalContext ctx) {
        return visit(ctx.expr());
    }

    /**
     * ******************************表达式部分******************************
     */

    /**
     * 变量
     * @param ctx
     * @return
     */
    @Override
    public Node visitVar(VarParser.VarContext ctx) {
        String var = ctx.VAR().getText();
        return varTable.get(var);
    }


    /**
     * 数
     * @param ctx
     * @return
     */
    @Override
    public Node visitNum(VarParser.NumContext ctx) {
        TerminalNode num = ctx.NUM();
        return num instanceof ErrorNode ? null : new NumNode(num.getText());
    }

    /**
     * 加减法
     * @param ctx
     * @return
     */
    @Override
    public Node visitAdditionAndSubtraction(VarParser.AdditionAndSubtractionContext ctx) {
        ExprNode node = new ExprNode();
        node.operator = ctx.operator.getType() == VarParser.ADD ? Operator.ADD : Operator.SUB;
        node.leftNode = visit(ctx.expr(0));
        node.rightNode = visit(ctx.expr(1));
        return node;
    }

    @Override
    public Node visitMeas(VarParser.MeasContext ctx) {
        String text = ctx.MEASTEXT().getText();
        NumNode numNode = new NumNode(dataMap.get(text).toString());
        return numNode;
    }

    /**
     * 乘除法
     * @param ctx
     * @return
     */
    @Override
    public Node visitMultiplyAndDivide(VarParser.MultiplyAndDivideContext ctx) {
        ExprNode node = new ExprNode();
        node.operator = (ctx.operator.getType() == VarParser.MUL || ctx.operator.getType() == VarParser.MULX) ? Operator.MUL : Operator.DIV;
        node.leftNode = visit(ctx.expr(0));
        node.rightNode = visit(ctx.expr(1));
        return node;
    }


    @Override
    public Node visitBrackets(VarParser.BracketsContext ctx) {
        return visit(ctx.expr());
    }
}

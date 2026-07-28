package com.graphinsight.indicator.lax.ifelse.iffunction;

import com.graphinsight.indicator.lax.ifelse.mu.MUParser;

import java.util.HashMap;
import java.util.Map;

/**
 * Date: 2023/6/6
 * Desc:
 */
public class IFFunctionVisitor extends IFElseBaseVisitor<Node> {

    private Map<String, Object> dataMap;

    private Map<String, Node> memory = new HashMap<String, Node>();

    public IFFunctionVisitor(Map<String, Object> dataMap) {
        this.dataMap = dataMap;
    }

    public IFFunctionVisitor() {
    }

    @Override
    public Node visitIf_stat(IFElseParser.If_statContext ctx) {
        Node condition = visit(ctx.condition_block());
        IFElseParser.Stat_blockContext ifexpr = ctx.stat_block(0);
        IFElseParser.Stat_blockContext elexpr = ctx.stat_block(1);
        Node node;
        if (condition.condition()){
            node = visit(ifexpr.expr());
        } else {
            node = visit(elexpr.expr());
        }
        return node;
    }

    @Override
    public Node visitOrExpr(IFElseParser.OrExprContext ctx) {
        Node left = this.visit(ctx.expr(0));
        Node right = this.visit(ctx.expr(1));
        return new Node(left.condition() || right.condition() ? "true" : "false");    }

    @Override
    public Node visitAndExpr(IFElseParser.AndExprContext ctx) {
        Node left = this.visit(ctx.expr(0));
        Node right = this.visit(ctx.expr(1));
        return new Node(left.condition() && right.condition() ? "true" : "false");
    }

    @Override
    public Node visitEqualityExpr(IFElseParser.EqualityExprContext ctx) {
        Node left = this.visit(ctx.expr(0));
        Node right = this.visit(ctx.expr(1));

        switch (ctx.op.getType()) {
            case IFElseParser.EQ:
                return new Node(left.numberic().compareTo(right.numberic()) == 0 ? "true" : "false");
            case IFElseParser.NEQ:
                return new Node(left.numberic().compareTo(right.numberic()) != 0 ? "true" : "false");
            default:
                throw new RuntimeException("unknown operator: " + MUParser.tokenNames[ctx.op.getType()]);
        }
    }

    @Override
    public Node visitRelationalExpr(IFElseParser.RelationalExprContext ctx) {
        Node left = this.visit(ctx.expr(0));
        Node right = this.visit(ctx.expr(1));

        switch (ctx.op.getType()) {
            case IFElseParser.LT:
                return new Node(left.numberic().doubleValue() < right.numberic().doubleValue() ? "true" : "false");
            case IFElseParser.LTEQ:
                return new Node(left.numberic().doubleValue() <= right.numberic().doubleValue() ? "true" : "false");
            case IFElseParser.GT:
                return new Node(left.numberic().doubleValue() > right.numberic().doubleValue() ? "true" : "false");
            case IFElseParser.GTEQ:
                return new Node(left.numberic().doubleValue() >= right.numberic().doubleValue() ? "true" : "false");
            default:
                throw new RuntimeException("unknown operator: " + MUParser.tokenNames[ctx.op.getType()]);
        }
    }

    @Override
    public Node visitNotExpr(IFElseParser.NotExprContext ctx) {
        Node node = visit(ctx.expr());
        return new Node(node.condition() ? "false" : "true");
    }

    @Override
    public Node visitAdditiveExpr(IFElseParser.AdditiveExprContext ctx) {
        Node left = this.visit(ctx.expr(0));
        Node right = this.visit(ctx.expr(1));

        switch (ctx.op.getType()) {
            case IFElseParser.PLUS:
                return new Node(String.valueOf(left.numberic().doubleValue() + right.numberic().doubleValue()));
            case IFElseParser.MINUS:
                return new Node(String.valueOf(left.numberic().doubleValue() - right.numberic().doubleValue()));
            default:
                throw new RuntimeException("unknown operator: " + MUParser.tokenNames[ctx.op.getType()]);
        }
    }

    @Override
    public Node visitMultiplicationExpr(IFElseParser.MultiplicationExprContext ctx) {
        Node left = this.visit(ctx.expr(0));
        Node right = this.visit(ctx.expr(1));

        switch (ctx.op.getType()) {
            case IFElseParser.MULT:
                return new Node(String.valueOf(left.numberic().doubleValue() * right.numberic().doubleValue()));
            case IFElseParser.DIV:
                return new Node(String.valueOf(left.numberic().doubleValue() / right.numberic().doubleValue()));
            case IFElseParser.MOD:
                return new Node(String.valueOf(left.numberic().doubleValue() % right.numberic().doubleValue()));
            default:
                throw new RuntimeException("unknown operator: " + MUParser.tokenNames[ctx.op.getType()]);
        }
    }

    @Override
    public Node visitNilAtom(IFElseParser.NilAtomContext ctx) {
        return new Node(null);
    }

    @Override
    public Node visitNumberAtom(IFElseParser.NumberAtomContext ctx) {
        return new Node(ctx.getText());
    }

    @Override
    public Node visitBooleanAtom(IFElseParser.BooleanAtomContext ctx) {
        return new Node(ctx.getText());
    }

    @Override
    public Node visitStringAtom(IFElseParser.StringAtomContext ctx) {
        String str = ctx.getText();
        // strip quotes
        str = str.substring(1, str.length() - 1).replace("\"\"", "\"");
        return new Node(str);
    }

    @Override
    public Node visitIdAtom(IFElseParser.IdAtomContext ctx) {
        String id = ctx.getText();
        Node value = memory.get(id);
        if(value == null) {
            throw new RuntimeException("no such variable: " + id);
        }
        return value;
    }

    @Override
    public Node visitBrExpr(IFElseParser.BrExprContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public Node visitMeas(IFElseParser.MeasContext ctx) {
        String text = ctx.MEASTEXT().getText();
        NumNode numNode = new NumNode(dataMap.get(text).toString());
        return numNode;
    }

}

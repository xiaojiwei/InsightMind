/***
 * Excerpted from "The Definitive ANTLR 4 Reference",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material, 
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose. 
 * Visit http://www.pragmaticprogrammer.com/titles/tpantlr2 for more book information.
***/
package com.graphinsight.indicator.xlax.lod;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvalVisitor extends LodExprBaseVisitor<Double> {
    /** "memory" for our calculator; variable/value pairs go here */
    Map<String, Double> memory = new HashMap<String, Double>();


    /** expr NEWLINE */
    @Override
    public Double visitPrintExpr(LodExprParser.PrintExprContext ctx) {
        Double value = visit(ctx.expr()); // evaluate the expr child
        System.out.println("end :::s" + value);         // print the result
        return value;                          // return dummy value
    }


    @Override
    public Double visitMeasure(LodExprParser.MeasureContext ctx) {
        LodExprParser.MeasContext measContext = ctx.meas();
        return visit(measContext);
    }

    @Override
    public Double visitMea(LodExprParser.MeaContext ctx) {

        List<LodExprParser.DimsContext> dimsContexts = ctx.dims();
        if (!CollectionUtils.isEmpty(dimsContexts)) {
            LodExprParser.DimsContext dims = dimsContexts.get(0);
            if (null != dims) {
                int childCount = dims.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    ParseTree c = dims.getChild(i);
                    String text = c.getText();
                    System.out.println(c);
                }
            }
        }


        List<LodExprParser.FiltersContext> filterList = ctx.filters();

        if (!CollectionUtils.isEmpty(filterList) ) {

            LodExprParser.FiltersContext filters = filterList.get(0);

            if (null != filters) {
                int childCount = filters.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    ParseTree c = filters.getChild(i);
                    String text = c.getText();
                    System.out.println(c);
                }
            }
        }

        Token token = ctx.getStart();
        String text = token.getText();

        return Double.valueOf(10);
    }

    @Override
    public Double visitDim(LodExprParser.DimContext ctx) {
        String dim = ctx.DIM().toString();
        return super.visitDim(ctx);
    }

    /** expr op=('*'|'/') expr */
    @Override
    public Double visitMulDiv(LodExprParser.MulDivContext ctx) {

        Double left = visit(ctx.expr(0));  // get value of left subexpression
        Double right = visit(ctx.expr(1)); // get value of right subexpression

        if ( ctx.op.getType() == LodExprParser.MUL ) {
            return left * right;
        }
        return left / right; // must be DIV
    }

    /** expr op=('+'|'-') expr */
    @Override
    public Double visitAddSub(LodExprParser.AddSubContext ctx) {
        Double left = visit(ctx.expr(0));  // get value of left subexpression
        Double right = visit(ctx.expr(1)); // get value of right subexpression
        if ( ctx.op.getType() == LodExprParser.ADD ) {
            return left + right;
        }
        return left - right; // must be SUB
    }

    /** '(' expr ')' */
    @Override
    public Double visitParens(LodExprParser.ParensContext ctx) {
        return visit(ctx.expr()); // return child expr's value
    }
}

/***
 * Excerpted from "The Definitive ANTLR 4 Reference",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material, 
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose. 
 * Visit http://www.pragmaticprogrammer.com/titles/tpantlr2 for more book information.
***/
package com.graphinsight.indicator.xlax.xlod;

import com.graphinsight.indicator.xlax.xlod.LodExprParser;
import org.antlr.v4.runtime.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvalVisitor extends LodExprBaseVisitor<Double> {
    /** "memory" for our calculator; variable/value pairs go here */
    Map<String, Double> memory = new HashMap<String, Double>();

    /** ID '=' expr NEWLINE */
    @Override
    public Double visitAssign(LodExprParser.AssignContext ctx) {
        String id = ctx.ID().getText();  // id is left-hand side of '='
        double value = visit(ctx.expr());   // compute value of expression on right
        memory.put(id, value);           // store it in our memory
        return value;
    }



    /** expr NEWLINE */
    @Override
    public Double visitPrintExpr(LodExprParser.PrintExprContext ctx) {
        Double value = visit(ctx.expr()); // evaluate the expr child
        System.out.println("end :::s" + value);         // print the result
        return value;                          // return dummy value
    }

    @Override
    public Double visitFilter(LodExprParser.FilterContext ctx) {
        return super.visitFilter(ctx);
    }

    @Override
    public Double visitMea(LodExprParser.MeaContext ctx) {

        List<LodExprParser.DimsContext> dims = ctx.dims();
        List<LodExprParser.FiltersContext> filters = ctx.filters();

        Token token = ctx.getStart();
        String text = token.getText();

        return Double.valueOf(10);
    }

    @Override
    public Double visitDim(LodExprParser.DimContext ctx) {
        String dim = ctx.DIM().getText();
        return super.visitDim(ctx);
    }

    /** expr op=('*'|'/') expr */
    @Override
    public Double visitMulDiv(LodExprParser.MulDivContext ctx) {

        Double left = visit(ctx.meas(0));  // get value of left subexpression
        Double right = visit(ctx.meas(1)); // get value of right subexpression

//        visitChildren(ctx);
        System.out.println(ctx.op.getType());
        if ( ctx.op.getType() == LodExprParser.MUL ) {
            return left * right;
        }
        return left / right; // must be DIV
    }

    /** expr op=('+'|'-') expr */
    @Override
    public Double visitAddSub(LodExprParser.AddSubContext ctx) {
        Double left = visit(ctx.meas(0));  // get value of left subexpression
        Double right = visit(ctx.meas(1)); // get value of right subexpression
        if ( ctx.op.getType() == LodExprParser.ADD ) return left + right;
        return left - right; // must be SUB
    }

    /** '(' expr ')' */
    @Override
    public Double visitParens(LodExprParser.ParensContext ctx) {
        return visit(ctx.expr()); // return child expr's value
    }
}

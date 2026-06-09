/***
 * Excerpted from "The Definitive ANTLR 4 Reference",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material, 
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose. 
 * Visit http://www.pragmaticprogrammer.com/titles/tpantlr2 for more book information.
***/
package com.graphinsight.indicator.lax.filter;

import com.graphinsight.indicator.enums.SqlOprType;
import com.graphinsight.indicator.model.Filter;
import com.graphinsight.indicator.model.Operator;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class InfoVisitor extends LaxExprBaseVisitor<Double> {
    /** "memory" for our calculator; variable/value pairs go here */
    Map<String, Double> memory = new HashMap<String, Double>();


    /** expr NEWLINE */
//    @Override
//    public Double visitPrintExpr(LaxExprParser.PrintExprContext ctx) {
//        Double value = visit(ctx.expr()); // evaluate the expr child
//        System.out.println("end :::s" + value);         // print the result
//        return value;                          // return dummy value
//    }

    @Override
    public Double visitMea(LaxExprParser.MeaContext ctx) {

        List<String> dimGroupList = new LinkedList<>();
        List<LaxExprParser.DimsContext> dimsContexts = ctx.dims();
        if (!CollectionUtils.isEmpty(dimsContexts)) {
            LaxExprParser.DimsContext dims = dimsContexts.get(0);
            if (null != dims) {
                int childCount = dims.getChildCount();
                for (int i = 0; i < childCount; i++) {

                    ParseTree c = dims.getChild(i);
                    if (c instanceof TerminalNode) {

                        TerminalNode node = (TerminalNode)c;
                        Token token = node.getSymbol();
                        int iType = token.getType();

                        if (LaxExprLexer.DIM == iType) {
                            dimGroupList.add(token.getText());
                        }
                    }
                }
            }
        }

        System.out.println("dimGroupListSize:" + dimGroupList.size());

        List<Filter> filterList = new LinkedList<>();

        List<LaxExprParser.FiltersContext> filtersContextList = ctx.filters();

        if (!CollectionUtils.isEmpty(filtersContextList) ) {

            LaxExprParser.FiltersContext filters = filtersContextList.get(0);

            if (null != filters) {
                int childCount = filters.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    ParseTree c = filters.getChild(i);
                    if (c instanceof LaxExprParser.FilContext) {

                        Filter filter = new Filter();

                        LaxExprParser.FilContext filContext = (LaxExprParser.FilContext)c;
                        LaxExprParser.DimsContext dimContext = filContext.dims();
                        System.out.println("dimContextText:" + dimContext.getText());
                        filter.setCode(dimContext.getText());
                        List<Operator> operatorList = new LinkedList<>();
                        Operator operator = new Operator();

                        Token ops = filContext.ops;
                        System.out.println("opsText:" + ops.getText());

                        SqlOprType sqlOprType = SqlOprType.IN;

                        operator.setSqlOprType(sqlOprType);
                        LaxExprParser.ValueContext valueContext = filContext.value();
                        System.out.println("valueContextText:" + valueContext.getText());

                        filter.setOperatorList(operatorList);

                        System.out.println(filContext);
                    }
                    String text = c.getText();
                    System.out.println(c);
                }
            }
        }

        Double result = Double.valueOf(10);

        return result;
    }

    @Override
    public Double visitDim(LaxExprParser.DimContext ctx) {
        String dim = ctx.DIM().toString();
        return super.visitDim(ctx);
    }

    /** expr op=('*'|'/') expr */
    @Override
    public Double visitMulDiv(LaxExprParser.MulDivContext ctx) {

        Double left = visit(ctx.expr(0));  // get value of left subexpression
        Double right = visit(ctx.expr(1)); // get value of right subexpression

        if ( ctx.op.getType() == LaxExprParser.MUL ) {
            return left * right;
        }
        return left / right; // must be DIV
    }

    /** expr op=('+'|'-') expr */
    @Override
    public Double visitAddSub(LaxExprParser.AddSubContext ctx) {
        Double left = visit(ctx.expr(0));  // get value of left subexpression
        Double right = visit(ctx.expr(1)); // get value of right subexpression
        if ( ctx.op.getType() == LaxExprParser.ADD ) {
            return left + right;
        }
        return left - right; // must be SUB
    }

    /** '(' expr ')' */
    @Override
    public Double visitParens(LaxExprParser.ParensContext ctx) {
        return visit(ctx.expr()); // return child expr's value
    }
}

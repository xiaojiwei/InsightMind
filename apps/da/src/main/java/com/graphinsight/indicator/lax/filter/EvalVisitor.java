/***
 * Excerpted from "The Definitive ANTLR 4 Reference",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material, 
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose. 
 * Visit http://www.pragmaticprogrammer.com/titles/tpantlr2 for more book information.
***/
package com.graphinsight.indicator.lax.filter;

import com.graphinsight.indicator.enums.LodType;
import com.graphinsight.indicator.enums.SqlOprType;
import com.graphinsight.indicator.lax.filter.function.Function;
import com.graphinsight.indicator.lax.filter.function.impl.CalculateFun;
import com.graphinsight.indicator.lax.filter.function.mode.CalculateParam;
import com.graphinsight.indicator.lax.filter.function.mode.LodDim;
import com.graphinsight.indicator.lax.ifelse.mu.MUParser;
import com.graphinsight.indicator.lax.tools.Tuple;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.ChartQueryService;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.hibernate.event.spi.LoadEventListener;
import org.springframework.util.CollectionUtils;

import java.util.*;

public class EvalVisitor extends LaxExprBaseVisitor<Node> {

    /** "memory" for our calculator; variable/value pairs go here */
    Map<String, Node> memory = new HashMap<String, Node>();
    List<List<Cell>> cellList = null;
    List<Cell> rowCells = null;
    BuildSqlTuple buildSqlTuple = null;
    PageData pageData = null;
    ChartQueryService chartQueryService;


    /**
     *
     * @param buildSqlTuple 信息元组
     * @param pageData
     * @param cellList 所有返回数据
     * @param rowCells 当前行
     */
    public EvalVisitor(BuildSqlTuple buildSqlTuple, PageData pageData, List<List<Cell>> cellList, List<Cell> rowCells, ChartQueryService chartQueryService) {
        this.buildSqlTuple = buildSqlTuple;
        this.pageData = pageData;
        this.cellList = cellList;
        this.rowCells = rowCells;
        this.chartQueryService = chartQueryService;

    }

//    /**
//     *
//     * @param cellList 所有返回数据
//     * @param rowCells 当前行
//     */
//    public EvalVisitor(List<List<Cell>> cellList, List<Cell> rowCells) {
//        this.cellList = cellList;
//        this.rowCells = rowCells;
//    }

    @Override
    public Node visitFuns(LaxExprParser.FunsContext ctx) {
        return super.visitFuns(ctx);
    }

    @Override
    public Node visitOrExpr(LaxExprParser.OrExprContext ctx) {

        Node left = this.visit(ctx.expr(0));
        Node right = this.visit(ctx.expr(1));
        Node result = new Node();
        if (left.condition() || right.condition()) {
            result.setResult(true);
        } else {
            result.setResult(false);
        }

        return result;

    }

    @Override
    public Node visitEqualityExpr(LaxExprParser.EqualityExprContext ctx) {

        Node left = this.visit(ctx.expr(0));
        Node right = this.visit(ctx.expr(1));

        Node result = new Node();
        switch (ctx.op.getType()) {
            case LaxExprParser.EQ:
                result.setResult(left.result.equals(right.result) ? true : false);
                return result;
            case LaxExprParser.NEQ:
                result.setResult(!left.result.equals(right.result) ? true : false);
                return result;
            default:
                throw new RuntimeException("unknown operator: " + MUParser.tokenNames[ctx.op.getType()]);
        }

    }

    @Override
    public Node visitNumberAtom(LaxExprParser.NumberAtomContext ctx) {

        Node result = new Node();
        String text = ctx.getText();
        result.setResult(text);

        return result;
    }

    @Override
    public Node visitRelationalExpr(LaxExprParser.RelationalExprContext ctx) {

        Object obj = ctx.expr(0);

        Node left = this.visit(ctx.expr(0));
        Node right = this.visit(ctx.expr(1));

        Node result = new Node();
        switch (ctx.op.getType()) {
            case LaxExprParser.LT:
                result.setResult(left.numberic().doubleValue() < right.numberic().doubleValue());
                return result;
            case LaxExprParser.LTEQ:
                result.setResult(left.numberic().doubleValue() <= right.numberic().doubleValue());
                return result;
            case LaxExprParser.GT:
                result.setResult(left.numberic().doubleValue() > right.numberic().doubleValue());
                return result;
            case LaxExprParser.GTEQ:
                result.setResult(left.numberic().doubleValue() >= right.numberic().doubleValue());
                return result;
            default:
                throw new RuntimeException("unknown operator: " + MUParser.tokenNames[ctx.op.getType()]);
        }

    }

    @Override
    public Node visitAndExpr(LaxExprParser.AndExprContext ctx) {
        Node left = this.visit(ctx.expr(0));
        Node right = this.visit(ctx.expr(1));
        Node result = new Node();
        if (left.condition() && right.condition()) {
            result.setResult(true);
        } else {
            result.setResult(false);
        }

        return result;
    }

    @Override
    public Node visitIf_stat(LaxExprParser.If_statContext ctx) {
        Node block = visit(ctx.condition_block());
        Boolean condition = (Boolean) block.result;
        LaxExprParser.Stat_blockContext ifexpr = ctx.stat_block(0);
        LaxExprParser.Stat_blockContext elexpr = ctx.stat_block(1);
        Node node;
        if (condition){
            node = visit(ifexpr.expr());
        } else {
            node = visit(elexpr.expr());
        }
        return node;
    }

    private String replaceSplit(String text) {

        try {

            char[] texts = text.toCharArray();
            if (null != texts && texts.length > 0) {

                int ichar = (int)texts[0];
                int echar = (int)texts[texts.length - 1];
                // 单引号 ' ascii码 = 39
                if (39 == ichar && echar == 39) {
                    StringBuilder textBuilder = new StringBuilder();
                    for (int i = 1; i < texts.length - 1; i++) {
                        textBuilder.append(texts[i]);
                    }
                    text = textBuilder.toString();
                }

            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return text;
    }

    @Override
    public Node visitString(LaxExprParser.StringContext ctx) {
        Node node = new Node();
        Token token = ctx.getStart();
        String text = replaceSplit(token.getText());
        node.setResult(text);
        return node;
    }

    @Override
    public Node visitVs(LaxExprParser.VsContext ctx) {
        return super.visitVs(ctx);
    }

    @Override
    public Node visitBooleanAtom(LaxExprParser.BooleanAtomContext ctx) {
        Token booleanToken = ctx.getStart();
        Node node = new Node();
        node.setResult(LaxExprLexer.TRUE == booleanToken.getType());
        return node;
    }

    @Override
    public Node visitFunction(LaxExprParser.FunctionContext ctx) {

        TerminalNode node = ctx.FUNNAME();
        Token funToken = node.getSymbol();

        Node d = new Node();

        LaxExprParser.ExprsContext exprsContext = ctx.exprs();

        if (null != exprsContext) {

            List<ParseTree> childrenList = exprsContext.children;
            if (!CollectionUtils.isEmpty(childrenList)) {

                List<Node> valueList = new LinkedList<>();
                for (ParseTree parseTree : childrenList) {

                    if (parseTree instanceof LaxExprParser.FunsContext) {

                        LaxExprParser.FunsContext funsContext = (LaxExprParser.FunsContext) parseTree;
                        Node rNode = visitFuns(funsContext);
                        valueList.add(rNode);

                    } else if (parseTree instanceof LaxExprParser.AddSubContext) {

                        LaxExprParser.AddSubContext addSubContext = (LaxExprParser.AddSubContext) parseTree;
                        Node value = visitAddSub(addSubContext);
                        valueList.add(value);

                    } else if (parseTree instanceof LaxExprParser.MulDivContext) {

                        LaxExprParser.MulDivContext mulDivContext = (LaxExprParser.MulDivContext) parseTree;
                        Node value = visitMulDiv(mulDivContext);
                        valueList.add(value);

                    } else if (parseTree instanceof LaxExprParser.StringContext) {
                        LaxExprParser.StringContext stringContext = (LaxExprParser.StringContext) parseTree;
                        Node value = new Node();
                        value.setResult(stringContext.getText());
                        valueList.add(value);
                    } else if (parseTree instanceof LaxExprParser.DimzContext) {
                        LaxExprParser.DimzContext dimzContext = (LaxExprParser.DimzContext) parseTree;
                        Node value = new Node();
                        value.setResult(replaceCode(dimzContext.getText()));
                        valueList.add(value);
                    }
                }

                Tuple tuple = new Tuple();
                tuple.setCellList(this.cellList);
                tuple.setRowCells(this.rowCells);
                Function<List<Node>, Node> fun = FunctionFactory.buildFunction(funToken.getText(), valueList, tuple, this.chartQueryService);
                d = fun.apply();

                return d;

            }

        } else {
            //Calculate function
            LaxExprParser.MeasContext measContext = ctx.meas();

            if (measContext instanceof LaxExprParser.MeaContext && node.getText().equalsIgnoreCase("Calculate")) {
                d = visitMea((LaxExprParser.MeaContext)measContext);
            } else {

                List<Node> valueList = new LinkedList<>();
                Tuple tuple = new Tuple();
                tuple.setCellList(this.cellList);
                tuple.setRowCells(this.rowCells);
                tuple.setBuildSqlTuple(this.buildSqlTuple);
                Function<List<Node>, Node> fun = FunctionFactory.buildFunction(funToken.getText(), valueList, tuple, this.chartQueryService);
                d = fun.apply();

                return d;
            }

        }

//        Double result = super.visitFunction(ctx);
        return d;

    }

    /**
     * 去掉维度、指标code两边的中括号
     * @param code
     * @return
     */
    private String replaceCode(String code) {
        if (null != code) {
            code = code.replaceAll("\\[", "").replaceAll("]", "");
        }
        return code;
    }

    private List<String> replaceValue(String dataList) {
        if (dataList.length() <= 2) {
            return new ArrayList<>();
        }
        dataList = dataList.substring(1, dataList.length() - 1);

        return Arrays.asList(dataList.split(","));
    }

    private List<String> replaceValue(List<String> dataList) {
        List<String> formatDataList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(dataList)) {
            for (String data : dataList) {
                if (null != data) {
                    data = data.substring(1, data.length() - 1);
                    formatDataList.add(data);
                }
            }
        }
        return formatDataList;
    }

    @Override
    public Node visitMea(LaxExprParser.MeaContext ctx) {

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
                            dimGroupList.add(replaceCode(token.getText()));
                        }
                    }
                }
            }
        }

//        System.out.println("dimGroupListSize:" + dimGroupList.size());

        //筛选项
        List<Filter> filterList = new LinkedList<>();

        //页面交互筛选项目
        List<Filter> paramFilterList = this.buildSqlTuple.getQueryParam().getFilterList();
        if (!CollectionUtils.isEmpty(paramFilterList)) {
            filterList.addAll(paramFilterList);
        }

        //指标相关信息
        CalculateParam calculateParam = new CalculateParam();

        List<LaxExprParser.FiltersContext> filtersContextList = ctx.filters();

        if (!CollectionUtils.isEmpty(filtersContextList) ) {
            calculateParam.setHasLodFilter(true);

            LaxExprParser.FiltersContext filters = filtersContextList.get(0);

            if (null != filters) {
                int childCount = filters.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    ParseTree c = filters.getChild(i);
                    if (c instanceof LaxExprParser.FilContext) {

                        //筛选项
                        LaxExprParser.FilContext filContext = (LaxExprParser.FilContext)c;
                        //维度
                        LaxExprParser.DimsContext dimContext = filContext.dims();

                        //操作类型
                        Token ops = filContext.ops;
                        //值
                        LaxExprParser.ValueContext valueContext = filContext.value();

                        //构建筛选项
                        Filter filter = new Filter();
                        //操作内容
                        List<Operator> operatorList = new LinkedList<>();
                        Operator operator = new Operator();

                        SqlOprType sqlOprType = null;
                        //维度设置
                        filter.setCode(replaceCode(dimContext.getText()));
                        //操作类型
                        Integer opsType = ops.getType();

                        List<String> dataList = new ArrayList<String>();
                        String value = valueContext.getText();
//                        System.out.println("valueContextText:" + valueContext.getText());

                        if (LaxExprParser.IN == opsType) {
                            sqlOprType = SqlOprType.IN;
                            dataList.addAll(replaceValue(value));
                        } else if (LaxExprParser.GTEQ == opsType) {
                            sqlOprType = SqlOprType.GREATER_THAN;
                        } else if (LaxExprParser.LTEQ == opsType) {
                            sqlOprType = SqlOprType.SMALLER_THAN_OR_EQUAL;
                        } else if (LaxExprParser.BETWEEN == opsType) {
                            sqlOprType = SqlOprType.BETEEN;
                            dataList.addAll(replaceValue(value));
                        }

                        operator.setSqlOprType(sqlOprType);
                        //值
                        operator.setDataList(dataList);
                        operatorList.add(operator);
                        filter.setOperatorList(operatorList);

                        filterList.add(filter);


                    }
                    String text = c.getText();
//                    System.out.println(c);
                }
            }
        }

        //指标名称
        Token measureToken = ctx.getStart();

        String measCode = replaceCode(measureToken.getText());
        calculateParam.setMeasCode(measCode);

        //过滤器相关信息
        calculateParam.setFilterList(filterList);

        //维度相关信息
        Token lodTypeToken = ctx.scope;

        if (null != lodTypeToken) {

            LodDim lodDim = new LodDim();
            LodType lodType = LodType.build(lodTypeToken.getText());
            lodDim.setLodType(lodType);
            lodDim.setDimCodeList(dimGroupList);

            calculateParam.setLodDim(lodDim);
        }

        //计算函数
        CalculateFun calculateFun = new CalculateFun();

        Tuple tuple = new Tuple();
        tuple.setRowCells(this.rowCells);
        tuple.setCellList(this.cellList);
        tuple.setBuildSqlTuple(this.buildSqlTuple);

        calculateFun.build(calculateParam, tuple, this.chartQueryService);
        return calculateFun.apply();

    }

    @Override
    public Node visitDim(LaxExprParser.DimContext ctx) {
        String dim = ctx.DIM().toString();
        return super.visitDim(ctx);
    }

    /** expr op=('*'|'/') expr */
    @Override
    public Node visitMulDiv(LaxExprParser.MulDivContext ctx) {

        Node leftNode = visit(ctx.expr(0));  // get value of left subexpression
        Node rightNode = visit(ctx.expr(1)); // get value of right subexpression

        Double left = Double.valueOf(this.getValue(leftNode.result));
        Double right = Double.valueOf(this.getValue(rightNode.result));

        Node resultNode = new Node();
        if ( ctx.op.getType() == LaxExprParser.MUL ) {
            resultNode.result = left * right;
            return resultNode;
        }

        resultNode.result = left / right;
        return resultNode; // must be DIV
    }

    private String getValue(Object value) {
        String result = null;
        if (value instanceof Node) {
            Object temp = ((Node) value).result;
            result = String.valueOf(temp);
        } else {
            result = String.valueOf(value);
        }
        return result;
    }

    /** expr op=('+'|'-') expr */
    @Override
    public Node visitAddSub(LaxExprParser.AddSubContext ctx) {
        Node leftNode = visit(ctx.expr(0));  // get value of left subexpression
        Node rightNode = visit(ctx.expr(1)); // get value of right subexpression

        Double left = Double.valueOf(String.valueOf(this.getValue(leftNode.result)));
        Double right = Double.valueOf(String.valueOf(this.getValue(rightNode.result)));

        Node resultNode = new Node();

        if ( ctx.op.getType() == LaxExprParser.ADD ) {
            resultNode.result = left + right;
            return resultNode;
        }
        resultNode.result = left - right;
        return resultNode; // must be DIV
    }

    /** '(' expr ')' */
    @Override
    public Node visitParens(LaxExprParser.ParensContext ctx) {
        return visit(ctx.expr()); // return child expr's value
    }
}

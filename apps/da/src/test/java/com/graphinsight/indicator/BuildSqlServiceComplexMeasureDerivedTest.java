package com.graphinsight.indicator;

import com.alibaba.fastjson.JSONObject;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.impl.BuildSqlServiceImpl;
import com.graphinsight.indicator.service.impl.QueryExecutorService;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * BuildSqlService Test 衍生、复合指标
 */
public class BuildSqlServiceComplexMeasureDerivedTest {

    /**
     * mock
     */
    BuildSqlServiceImpl mockBuildSqlService = new BuildSqlServiceImpl();

    @Test
    public void testComplexMeasureTable() {

        /**
         * 复合指标需要指定对象下的所依赖的指标重要:
         *
         *         增加当前指标所依赖的下级指标
         *         measure.getHasAllMeasureSet().add(measure1);
         *         measure.getHasAllMeasureSet().add(measure2);
         *
         *         增加当前指标所依赖的派生维度
         *         measure.getHasAllDimensionSet().add(dimension1);
         *         measure.getHasAllDimensionSet().add(dimension2);
         *
         *  派生指标除上述需要增加所依赖的指标外，还需要增加where 派生条件：
         */

        String SCHEMA = "eps_test";

        /**
         *  事实表 tbl_fact_1
         *  事实表 tbl_fact_2
         *  维度表 tbl_dim_1
         *  维度表 tbl_dim_2
         */
        //基础指标1
        Measure measure1 = new Measure();
        measure1.setCode("meas_1");
        measure1.setName("指标1");
        measure1.setAlias("指标别名1");

        Table factTable = new Table();
        factTable.setSourceType(SourceType.DORIS);
        factTable.setSchemaName(SCHEMA);
        factTable.setTableName("employee_channel_sale_stat_day_v1");
        factTable.setFactColumn("follow_cnt");
//        factTable.setWhereCondition("gender=0");
        factTable.setApplyType(MeasureType.ORIGIN);
        factTable.setExpression("[{\"operatingType\":\"operator\",\"operator\":\"count\"}]");

        measure1.setFactTable(Arrays.asList(factTable));

        //基础指标2
        Measure measure2 = new Measure();
        measure2.setCode("meas_2");
        measure2.setName("指标2");
        measure2.setAlias("指标别名2");

        Table factTable1 = new Table();
        factTable1.setSourceType(SourceType.DORIS);
        factTable1.setSchemaName(SCHEMA);
        factTable1.setTableName("employee_channel_sale_stat_day_v1");
        factTable1.setFactColumn("appoint_cnt");
//        factTable1.setWhereCondition("gender=1");
        factTable1.setApplyType(MeasureType.ORIGIN);
        factTable1.setExpression("[{\"operatingType\":\"operator\",\"operator\":\"sum\"}]");

        measure2.setFactTable(Arrays.asList(factTable1));


        /**
         * 衍生指标（复合指标）
         *  measure3 = measure1 + measure2
         */
        Measure measure3 = new Measure();
        measure3.setCode("meas_3");
        measure3.setName("衍生复合指标3");
        measure3.setAlias("衍生复合指标3");

        Table factTable3 = new Table();
        factTable3.setSourceType(SourceType.DORIS);
        factTable3.setSchemaName(SCHEMA);
        factTable3.setApplyType(MeasureType.DERIVED);
        List<OperationItem> itemList = new LinkedList<OperationItem>();

        OperationItem item = new OperationItem();
        item.setOperatingType("operand");
        OperationItem.MeasureBasicInfo basicInfo = new OperationItem.MeasureBasicInfo(Long.valueOf(1), measure1.getCode(), measure1.getName());
        item.setOperand(basicInfo);

        itemList.add(item);

        OperationItem item1 = new OperationItem();
        item1.setOperatingType("operator");
        item1.setOperator("+");

        itemList.add(item1);

        OperationItem item2 = new OperationItem();
        item2.setOperatingType("operand");
        OperationItem.MeasureBasicInfo basicInfo1 = new OperationItem.MeasureBasicInfo(Long.valueOf(2), measure2.getCode(), measure2.getName());
        item2.setOperand(basicInfo1);

        itemList.add(item2);

        Object json = JSONObject.toJSON(itemList);
        String str = json.toString();//将json对象转换为字符串
        System.out.println(str);

        factTable3.setExpression(str);
        measure3.setFactTable(Arrays.asList(factTable3));

        measure3.getHasAllMeasureSet().add(measure1);
        measure3.getHasAllMeasureSet().add(measure2);

        ValueFormat format = new ValueFormat();
        format.setFormatType(FormatType.DECIMAL);
        format.setValue(1);

        measure3.setValueFormat(format);

        //衍生（复合）指标



        /**
         * 派生指标（复合指标）
         *  measure3 = measure1 + measure3
         */
        Measure measure4 = new Measure();
        measure4.setCode("meas_6");
        measure4.setName("派生指标6");
        measure4.setAlias("派生指标6");

        Table factTable4 = new Table();
        factTable4.setSourceType(SourceType.DORIS);
        factTable4.setApplyType(MeasureType.EXTENDED);
        List<OperationItem> itemList1 = new LinkedList<OperationItem>();

        OperationItem pitem = new OperationItem();
        pitem.setOperatingType("operand");
        OperationItem.MeasureBasicInfo pBasicInfo = new OperationItem.MeasureBasicInfo(Long.valueOf(1), measure1.getCode(), measure1.getName());
        pitem.setOperand(pBasicInfo);

        itemList1.add(pitem);

        OperationItem pitem1 = new OperationItem();
        pitem1.setOperatingType("operator");
        pitem1.setOperator("+");

        itemList1.add(pitem1);

        OperationItem pitem2 = new OperationItem();
        pitem2.setOperatingType("operand");
        OperationItem.MeasureBasicInfo pBasicInfo1 = new OperationItem.MeasureBasicInfo(Long.valueOf(3), measure3.getCode(), measure3.getName());
        pitem2.setOperand(pBasicInfo1);

        itemList1.add(pitem2);

        Object pjson = JSONObject.toJSON(itemList1);
        String pstr = pjson.toString();//将json对象转换为字符串

        factTable4.setExpression(pstr);

        Filter exFilter = new Filter();
        exFilter.setCode("dim_3");

        Operator operator11 = new Operator();
        operator11.setSqlOprType(SqlOprType.LIKE);
        operator11.setSqlLogicalType(SqlLogicalType.AND);

        List<String> dataList1 = new ArrayList<>();
        dataList1.add("测试维度分组");
        operator11.setDataList(dataList1);

        exFilter.getOperatorList().add(operator11);
        factTable4.getFilterList().add(exFilter);

        measure4.setFactTable(Arrays.asList(factTable4));
        measure4.getHasAllMeasureSet().add(measure1);
        measure4.getHasAllMeasureSet().add(measure3);

        //end 派生指标
        Dimension dimension1 = new Dimension();
        dimension1.setCode("dim_1");
        dimension1.setName("维度1");
        dimension1.setAlias("维度别名1");
        dimension1.setMaster(true);

        Table factTable2 = new Table();
        factTable2.setSourceType(SourceType.DORIS);
        factTable2.setSchemaName(SCHEMA);
        factTable2.setTableName("employee_channel_sale_stat_day_v1");
        factTable2.setFactColumn("area_id");
        factTable2.setMasterPrimaryKey("area_dept_id");

        //维度事实表添加
        List<Table> factTableList = new ArrayList<Table>();
        factTableList.add(factTable2);

        dimension1.setFactTableList(factTableList);

        Table dimTable1 = new Table();
        dimTable1.setSourceType(SourceType.DORIS);
        dimTable1.setSchemaName(SCHEMA);
        dimTable1.setTableName("nr_sales_dim_dept_info");
        dimTable1.setDimPrimaryKey("area_dept_id");
        dimTable1.setDimColumn("area");

        dimension1.setDimTableList(Arrays.asList(dimTable1));
        dimension1.setDimType(DimType.STD_WITH_TABLE);

        //**
        Dimension dimension2 = new Dimension();
        dimension2.setCode("dim_2");
        dimension2.setName("维度2");
        dimension2.setAlias("维度别名2");
        dimension2.setMaster(true);

        Table factTable5 = new Table();
        factTable5.setSourceType(SourceType.DORIS);
        factTable5.setSchemaName(SCHEMA);
        factTable5.setTableName("employee_channel_sale_stat_day_v1");
        factTable5.setFactColumn("dept_store_id");
        factTable5.setMasterPrimaryKey("owner_store_dept_id");

        //维度事实表添加
        List<Table> factTableList2 = new ArrayList<Table>();
        factTableList2.add(factTable5);

        dimension2.setFactTableList(factTableList2);

        Table dimTable2 = new Table();
        dimTable2.setSourceType(SourceType.DORIS);
        dimTable2.setSchemaName(SCHEMA);
        dimTable2.setTableName("nr_sales_dim_dept_info");
        dimTable2.setDimPrimaryKey("owner_store_dept_id");
        dimTable2.setDimColumn("owner_store_dept_name");

        dimension2.setDimTableList(Arrays.asList(dimTable2));
        dimension2.setDimType(DimType.STD_WITH_TABLE);


        //衍生维度
        Dimension dimension3 = new Dimension();
        dimension3.setCode("dim_3");
        dimension3.setName("衍生维度3");
        dimension3.setAlias("衍生维度3");

        Table factTable12 = new Table();
        factTable12.setSourceType(SourceType.DORIS);
        factTable12.setSchemaName(SCHEMA);
        factTable12.setTableName("employee_channel_sale_stat_day_v1");
        factTable12.setFactColumn("area_id");
        factTable12.setMasterPrimaryKey("area_dept_id");

        //维度事实表添加
        List<Table> factTableList12 = new ArrayList<Table>();
        factTableList12.add(factTable12);

        dimension3.setFactTableList(factTableList);

        Table dimTable12 = new Table();
        dimTable12.setSourceType(SourceType.DORIS);
        dimTable12.setSchemaName(SCHEMA);
        dimTable12.setTableName("nr_sales_dim_dept_info");
        dimTable12.setDimPrimaryKey("area_dept_id");
        dimTable12.setDimColumn("area");


        dimension3.setDimTableList(Arrays.asList(dimTable12));
        dimension3.setDimType(DimType.STD_WITH_TABLE);

        GroupColumn groupColumn = new GroupColumn();
        groupColumn.setName("测试维度分组");
        Filter filter = new Filter();

        Operator operatoreq = new Operator();

        operatoreq.setSqlOprType(SqlOprType.LIKE);
        operatoreq.setSqlLogicalType(SqlLogicalType.OR);

        List<String> dataList = new LinkedList<String>();
        dataList.add("测试中文对比值");
        operatoreq.setDataList(dataList);
        filter.getOperatorList().add(operatoreq);
        filter.setCode("dim_1");

        groupColumn.getFilterList().add(filter);
        dimension3.getHasAllDimensionSet().add(dimension1);
        dimension3.getGroupColumnList().add(groupColumn);


        measure4.getHasAllDimensionSet().add(dimension3);

        //
        Set<Measure> measureSet = new LinkedHashSet<>();
        measureSet.add(measure1);
        measureSet.add(measure2);
        measureSet.add(measure3);
        measureSet.add(measure4);

        Set<Dimension> dimensionSet = new LinkedHashSet<Dimension>();
        dimensionSet.add(dimension1);
        dimensionSet.add(dimension2);
        dimensionSet.add(dimension3);


        BuildSqlTuple tuple = new BuildSqlTuple();
        Set<String> displayDimCodeSet = tuple.getDisplayDimensionCodeSet();
        displayDimCodeSet.add("dim_1");
        displayDimCodeSet.add("dim_2");
        displayDimCodeSet.add("dim_3");

        Set<String> displayMeasCodeSet = tuple.getDisplayMeasureCodeSet();
//        displayMeasCodeSet.add("meas_1");
        displayMeasCodeSet.add("meas_2");
        displayMeasCodeSet.add("meas_3");
        displayMeasCodeSet.add("meas_6");

        //mock页面用户所选择维度code
        tuple.setDisplayDimensionCodeSet(displayDimCodeSet);
        tuple.setDisplayMeasureCodeSet(displayMeasCodeSet);

        //mock页面用户所选择的维度指标
        tuple.setChoiceDimensionSet(dimensionSet);
        tuple.setChoiceMeasureSet(measureSet);

        //mock所有的指标和维度，由衍生、派生指标中包含的所有指标
        tuple.setDimensionSet(dimensionSet);
        tuple.setMeasureSet(measureSet);

        QueryParam queryParam = new QueryParam();

        /**
         * 筛选filter、排序order入参数据
         */
        List<Filter> filterList = new ArrayList<>();

        //维度筛选
        Filter dimFilter = new Filter();
        dimFilter.setCode("dim_1");
        Operator operator = new Operator();
        operator.setSqlOprType(SqlOprType.IN);
        operator.getDataList().add("值1");

        dimFilter.getOperatorList().add(operator);
        filterList.add(dimFilter);

        //指标筛选
        Filter measFilter = new Filter();
        measFilter.setCode("meas_1");
        Operator operator1 = new Operator();
        operator1.setSqlOprType(SqlOprType.GREATER_THAN);
        operator1.getDataList().add("2");

        measFilter.getOperatorList().add(operator1);
        filterList.add(measFilter);


        Filter measFilter3 = new Filter();
        measFilter3.setCode("meas_6");
        Operator operator3 = new Operator();
        operator3.setSqlOprType(SqlOprType.GREATER_THAN);
        operator3.getDataList().add("20");

//        measFilter3.getOperatorList().add(operator3);

        filterList.add(measFilter3);

//        queryParam.setFilterList(filterList);

        /**
         * 排序字段
         */
        List<Order> orderList = new ArrayList<Order>();
        //维度排序1
        Order dimOrder = new Order();
        dimOrder.setCode("dim_3");
        dimOrder.setSortType(SortType.DESC);
        orderList.add(dimOrder);

        //指标排序1
//        Order measOrder = new Order();
//        measOrder.setCode("meas_6");
//        measOrder.setSortType(SortType.DESC);
//        orderList.add(measOrder);

        queryParam.setOrderList(orderList);

        tuple.setQueryParam(queryParam);

        //分页数据
        tuple.setStartPage(0);
        tuple.setEndPage(10);

        //构建SQL
        Map<String, List<SingleFactTableSqlAgg>> rootTableMap = mockBuildSqlService.getBySourceTable(tuple);
        tuple.setRootTableMap(rootTableMap);

        List<String> rootSqls = mockBuildSqlService.buildRootSqls(tuple);
        for (String rootSql : rootSqls) {
            String sql = QueryExecutorService.formatSql(SourceType.MYSQL, rootSql);
        }

        String fullJoinGroupSql = mockBuildSqlService.buildFullJoinGroupSql(tuple);
        //count sql
        String countSql = mockBuildSqlService.buildCountSql(fullJoinGroupSql, tuple);

        String pageSql = null;
        String aggregatorSql = null;
        String baseReviewSql = null;
        boolean isMeasureOpr = true;
        if (!isMeasureOpr) {

            pageSql = mockBuildSqlService.buildPageSql(tuple);
            aggregatorSql = mockBuildSqlService.buildAggregatorSql(tuple);
            baseReviewSql = mockBuildSqlService.buildReViewSQL(tuple);

        } else {

            aggregatorSql = mockBuildSqlService.buildAggregatorSql(tuple);
            //增加排序（维度、指标）、rownumber
            String hasMeasOrderSql = mockBuildSqlService.buildHasMeasOprSql(tuple);
            //增加分页
            tuple.setFullJoinGroupSql(hasMeasOrderSql);
            String hasMeasOrderPageSql = mockBuildSqlService.buildHasMeasOprPageSql(tuple);
            tuple.setAggregatorSql(hasMeasOrderPageSql);
            baseReviewSql = mockBuildSqlService.buildReViewSQL(tuple);

        }

        String sql = QueryExecutorService.formatSql(SourceType.MYSQL, baseReviewSql);
        System.out.println(sql);

    }

}

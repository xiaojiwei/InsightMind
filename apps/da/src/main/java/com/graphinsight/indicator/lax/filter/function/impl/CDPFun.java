package com.graphinsight.indicator.lax.filter.function.impl;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.lax.filter.Node;
import com.graphinsight.indicator.lax.filter.function.Function;
import com.graphinsight.indicator.lax.filter.function.mode.CalculateParam;
import com.graphinsight.indicator.lax.filter.function.mode.LodDim;
import com.graphinsight.indicator.lax.tools.Tuple;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.impl.BuildSqlServiceImpl;
import com.graphinsight.indicator.util.CloneUtils;
import com.graphinsight.indicator.util.StringUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.LinkedList;
import java.util.List;

@Service
public class CDPFun implements Function<List, Node> {

    private ChartQueryService chartQueryService;

    private Tuple tuple;

    @Override
    public Function build(List values, Tuple tuple, ChartQueryService chartQueryService) {

        this.tuple = tuple;
        this.chartQueryService = chartQueryService;

        return this;

    }

    private static final String CDP_SUM = "MEAS_b5159de555304d21b7869ac3c834b380";
    private static final String ACCOUNT_CODE = "DIM_e4f67bbf3f1d427aab9548c65a52e93c"; //科目编码

    private static final String ACCOUNT_LEVEL1_CODE = "DIM_1bed611f72e649e6a35b7cfe78d9374d"; //科目Leve1编码
    private static final String ACCOUNT_LEVEL2_CODE = "DIM_c419804bc46b406abef945955a5c56d6"; //科目Leve2编码
    private static final String ACCOUNT_LEVEL3_CODE = "DIM_a35f4682c638450e867a3036f9441448"; //科目Leve3编码
    private static final String ACCOUNT_LEVEL4_CODE = "DIM_f73e6930139e491b973c70e3ddfd6556"; //科目Leve4编码
    private static final String ACCOUNT_LEVEL5_CODE = "DIM_ad0db5b66720417d8fadf8c4e35a827b"; //科目Leve5编码
    private static final String CATEGORY_CODE = "DIM_e2c424a6997343a19fcaf6371883436d"; //科目编码

    private static final String ACCOUNT_VALUE_ID_JFL = "EXP_DALL";

    private static final String ACCOUNT_V = "EXP_D0201";
    private static final String ACCOUNT_VALUE_NAME_JFFYHJ = "交付费用合计";
    @Override
    public Node apply() {

        //所有筛选项
        //返回结果
        Node node = new Node();
        //分母构建
        DataSource dataSource = this.buildDS(CDP_SUM);

        QueryParam queryParam = this.tuple.getBuildSqlTuple().getQueryParam();
        QueryParam copyQueryParam = CloneUtils.clone(queryParam);

        //移除默认6个AccountCode的维度,避免分子的条件卡到分母上。
        List<Filter> filterList = this.delAccountFilters(copyQueryParam.getFilterList());
        dataSource.getFilterList().addAll(filterList);

        //分母筛选条件 - 分母交付量
        Filter filter = new Filter();
        filter.setCode(ACCOUNT_CODE);

        Operator operator = new Operator();
        operator.setSqlOprType(SqlOprType.IN);
        //科目 交付量 code SA_D02
        operator.getDataList().add("SA_D02");
        filter.getOperatorList().add(operator);

        dataSource.getFilterList().add(filter);

        Filter filterCategory1 = new Filter();
        filterCategory1.setCode(CATEGORY_CODE);

        Operator operatorCategory1 = new Operator();
        operatorCategory1.setSqlOprType(SqlOprType.IN);
        operatorCategory1.getDataList().add("ACTUAL");

        filterCategory1.getOperatorList().add(operatorCategory1);
        dataSource.getFilterList().add(filterCategory1);

        List<Filter> rowFilters = this.buildRowCellFilter();
        //分母值中需要删除掉行中出现的科目值，分母只留SA_DO2和ACTUAL。
        if (!CollectionUtils.isEmpty(rowFilters)) {
            List<Filter> delAccountRowFilters = this.delAccountFilters(rowFilters);
            dataSource.getFilterList().addAll(delAccountRowFilters);
        }

        //查询分母
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        dataSource.setPageSize(99999999);
        dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        dataSource.setChartType(ChartType.TABLE);
        PageData pageData = this.chartQueryService.execQuery(dataSource);

        //分母值
        Double value = this.findMeasValue(pageData);

        //分子构建
        DataSource dataSourceSon = this.buildDS(CDP_SUM);

        QueryParam copyQueryParam2 = CloneUtils.clone(queryParam);

        List<Filter> filterList2 = copyQueryParam2.getFilterList();

        //分子筛选条件

        Filter filterSon = new Filter();
        filterSon.setCode(ACCOUNT_LEVEL2_CODE);

        Operator operator2 = new Operator();
        operator2.setSqlOprType(SqlOprType.IN);
        operator2.getDataList().add("EXP_DALL");

        filterSon.getOperatorList().add(operator2);
        dataSourceSon.getFilterList().addAll(filterList2);
        dataSourceSon.getFilterList().add(filterSon);

        //6个子维度的筛选条件作用到分子上。
        List<Filter> hasAllAccountList = this.getAccountFilters(copyQueryParam2);
        if (!CollectionUtils.isEmpty(hasAllAccountList)) {
            dataSourceSon.getFilterList().addAll(hasAllAccountList);
        }

        Filter filterCategorySon = new Filter();
        filterCategorySon.setCode(CATEGORY_CODE);

        Operator operatorCategorySon = new Operator();
        operatorCategorySon.setSqlOprType(SqlOprType.IN);
        operatorCategorySon.getDataList().add("ACTUAL");

        filterCategorySon.getOperatorList().add(operatorCategorySon);

        List<Filter> rowFilters1 = this.buildRowCellFilter();
        if (!CollectionUtils.isEmpty(rowFilters1)) {
            dataSourceSon.getFilterList().addAll(rowFilters1);
        }
        dataSourceSon.getFilterList().add(filterCategorySon);

        //查询分母
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        dataSourceSon.setPageSize(99999999);
        dataSourceSon.setCacheStrategy(CacheStrategy.OVERWRITE);
        dataSourceSon.setChartType(ChartType.TABLE);
        PageData pageData2 = this.chartQueryService.execQuery(dataSourceSon);

        //分子值
        Double value2 = this.findMeasValue(pageData2);

        node.result = value2 / value;

        return node;

    }


    public List<Filter> buildRowCellFilter() {

        List<Filter> rowFilterList = new LinkedList<>();
        List<Cell> rowCells = this.tuple.getRowCells();
        for (Cell cell : rowCells) {

            if (CellType.DIMENSION.equals(cell.getType())) {
                ViewType viewType = cell.getViewType();
                Filter filter = new Filter();
                filter.setViewType(viewType);
                filter.setCode(cell.getCode());
                Operator operator = new Operator();


                if (BuildSqlServiceImpl.isDateViewType(viewType)) {
                    operator.setSqlOprType(SqlOprType.IN);
                    operator.setTimeRange(TimeRange.DATE);
                } else {
                    operator.setSqlOprType(SqlOprType.IN);
                }

                operator.getDataList().add(cell.getId());
                filter.getOperatorList().add(operator);
                rowFilterList.add(filter);
            }

        }

        return rowFilterList;

    }



    private List<Filter> delAccountFilters(List<Filter> filterList) {

        List<Filter> allFillerList = new LinkedList<>();
        if (!CollectionUtils.isEmpty(filterList)) {
            for (Filter filter : filterList) {

                String code = filter.getCode();
                if (ACCOUNT_CODE.equalsIgnoreCase(code)) {

                } else if (ACCOUNT_LEVEL1_CODE.equalsIgnoreCase(code)) {

                } else if (ACCOUNT_LEVEL2_CODE.equalsIgnoreCase(code)) {

                } else if (ACCOUNT_LEVEL3_CODE.equalsIgnoreCase(code)) {

                } else if (ACCOUNT_LEVEL4_CODE.equalsIgnoreCase(code)) {

                } else if (ACCOUNT_LEVEL5_CODE.equalsIgnoreCase(code)) {

                } else {
                    allFillerList.add(filter);
                }
            }
        }

        return allFillerList;

    }



    /**
     * DIM_ad0db5b66720417d8fadf8c4e35a827b 费用细分 level5_id
     * DIM_f73e6930139e491b973c70e3ddfd6556 费用类别 level4_id
     * DIM_a35f4682c638450e867a3036f9441448 费用项目 level3_id
     * DIM_c419804bc46b406abef945955a5c56d6 科目二级名称 level2_id
     * DIM_1bed611f72e649e6a35b7cfe78d9374d 科目一级名称 level1_id
     * DIM_e4f67bbf3f1d427aab9548c65a52e93c 科目 id
     *
     *  private static final String ACCOUNT_CODE = "DIM_e4f67bbf3f1d427aab9548c65a52e93c"; //科目编码
     *  private static final String ACCOUNT_LEVEL1_CODE = "DIM_1bed611f72e649e6a35b7cfe78d9374d"; //科目Leve1编码
     *  private static final String ACCOUNT_LEVEL2_CODE = "DIM_c419804bc46b406abef945955a5c56d6"; //科目Leve2编码
     *  private static final String ACCOUNT_LEVEL3_CODE = "DIM_a35f4682c638450e867a3036f9441448"; //科目Leve3编码
     *  private static final String ACCOUNT_LEVEL4_CODE = "DIM_f73e6930139e491b973c70e3ddfd6556"; //科目Leve4编码
     *  private static final String ACCOUNT_LEVEL5_CODE = "DIM_ad0db5b66720417d8fadf8c4e35a827b"; //科目Leve5编码
     *
     * @param queryParam
     * @return
     */
    private List<Filter> getAccountFilters(QueryParam queryParam) {

        List<Filter> allFillerList = new LinkedList<>();
        List<Filter> filterList = queryParam.getFilterList();

        Filter accountFilter = this.findFilterByCode(ACCOUNT_CODE, filterList);
        if (null != accountFilter) {
            allFillerList.add(accountFilter);
        }

        Filter accountLevel1Code = this.findFilterByCode(ACCOUNT_LEVEL1_CODE, filterList);
        if (null != accountLevel1Code) {
            allFillerList.add(accountLevel1Code);
        }

        Filter accountLevel2Code = this.findFilterByCode(ACCOUNT_LEVEL2_CODE, filterList);
        if (null != accountLevel2Code) {
            allFillerList.add(accountLevel2Code);
        }

        Filter accountLevel3Code = this.findFilterByCode(ACCOUNT_LEVEL3_CODE, filterList);
        if (null != accountLevel3Code) {
            allFillerList.add(accountLevel3Code);
        }

        Filter accountLevel4Code = this.findFilterByCode(ACCOUNT_LEVEL4_CODE, filterList);
        if (null != accountLevel4Code) {
            allFillerList.add(accountLevel4Code);
        }

        Filter accountLevel5Code = this.findFilterByCode(ACCOUNT_LEVEL5_CODE, filterList);
        if (null != accountLevel5Code) {
            allFillerList.add(accountLevel5Code);
        }

        return allFillerList;

    }


    private Filter findFilterByCode(String dimCode, List<Filter> filterList) {

        if (!CollectionUtils.isEmpty(filterList)) {
            for (Filter filter : filterList) {
                if (filter.getCode().equalsIgnoreCase(dimCode)) {
                    return filter;
                }
            }

        }

        return null;

    }



    private Double findMeasByRow(DataSource ds) {

        List<Cell> rowCells = this.tuple.getRowCells();
        String measCode = null;
        for (BaseConfigure baseConfigure : ds.getConfigureList()) {
            measCode = baseConfigure.getCode();
            break;
        }

        if (!CollectionUtils.isEmpty(rowCells)) {
            for (Cell rowCell : rowCells) {
                if (null != measCode && measCode.equalsIgnoreCase(rowCell.getCode())) {
                    String value = rowCell.getData();
                    value = value.replaceAll(",", "");
                    Double result = Double.valueOf(0);
                    try {
                        result = Double.valueOf(value);
                    } catch (Exception ex) {
                        //非正常字符格式，统一用0代替。
//                        ex.printStackTrace();
                    }
                    return result;
                }
            }
        }

        return Double.valueOf(0);

    }

    private DataSource buildDS(String measCode) {

        DataSource dataSource = new DataSource();

        //指标
        BaseConfigure measConf = new BaseConfigure();
        measConf.setCode(measCode);
        dataSource.getConfigureList().add(measConf);

        //过滤器
        dataSource.setCacheStrategy(CacheStrategy.DEFAULT);

        return dataSource;

    }

    private Double findMeasValue(PageData pageData) {

        List<List<Cell>> cellList = pageData.getCellList();
        Double value = Double.valueOf(0);

        //原始当前行
        List<Cell> rowCells = this.tuple.getRowCells();

        for (List<Cell> cells : cellList) {

            if (this.currentRowCell(rowCells, cells)) {

                for (Cell cell : cells) {
                    CellType cellType = cell.getType();
                    if (CellType.MEASURE.equals(cellType)) {
                        String valueStr = cell.getData();
                        if (StringUtil.isNotEmpty(valueStr)) {
                            valueStr = valueStr.replaceAll(",", "");
                            if (StringUtil.isNumber(valueStr)) {
                                value = Double.valueOf(valueStr);
                                break;
                            }
                        }
                    }
                }

            }

        }

        return value;

    }

    private boolean currentRowCell(List<Cell> rowCell, List<Cell> cursorCell) {

        boolean current = true;

        for (int i = 0; i < cursorCell.size(); i++) {

            Cell cursor = cursorCell.get(i);
            CellType cellType = cursor.getType();
            if (CellType.MEASURE.equals(cellType)) {
                continue;
            }
            Cell row = rowCell.get(i);
            if (cursor.getType().equals(row.getType()) && cursor.getCode().equalsIgnoreCase(row.getCode()) && cursor.getId().equalsIgnoreCase(row.getId())) {

            } else {
                current = false;
                break;
            }

        }

        return current;
    }
}

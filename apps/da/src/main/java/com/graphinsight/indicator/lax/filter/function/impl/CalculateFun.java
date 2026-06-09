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
import com.graphinsight.indicator.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
public class CalculateFun implements Function<CalculateParam, Node> {

    private CalculateParam calcParam;

    private ChartQueryService chartQueryService;

    private Tuple tuple;

    @Override
    public Function build(CalculateParam calculateParam, Tuple tuple, ChartQueryService chartQueryService) {

        this.calcParam = calculateParam;
        this.tuple = tuple;
        this.chartQueryService = chartQueryService;

        return this;

    }

    @Override
    public Node apply() {

        Node node = new Node();
        DataSource dataSource = this.buildDS();
        if (onlyMeasure(dataSource)) {
            Double value = this.findMeasByRow(dataSource);
            node.result = value;
        } else {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            dataSource.setPageSize(99999999);
            dataSource.setCacheStrategy(CacheStrategy.DEFAULT);
            PageData pageData = this.chartQueryService.execQuery(dataSource);

            Double value = this.findMeasValue(pageData);
            node.result = value;
        }

        return node;

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

    /**
     * 如果只有一个指标则默认取当前行指标数
     * @param ds
     * @return
     */
    private boolean onlyMeasure(DataSource ds) {

        //只有一个指标
        boolean only = ds.getConfigureList().size() == 1;
        //筛选项大于0
        only = only && !this.calcParam.getHasLodFilter();

        return only;

    }

    private DataSource buildDS() {

        DataSource dataSource = new DataSource();

        //指标
        String measCode = this.calcParam.getMeasCode();
        BaseConfigure measConf = new BaseConfigure();
        measConf.setCode(measCode);
        dataSource.getConfigureList().add(measConf);

        //维度
        LodDim lodDim = this.calcParam.getLodDim();
        if (null != lodDim) {
            List<String> dimCodeList = lodDim.getDimCodeList();
            LodType lodType = lodDim.getLodType();
            if (LodType.FIXED.equals(lodType)) {

                if (!CollectionUtils.isEmpty(dimCodeList)) {
                    for (String dimCode : dimCodeList) {
                        BaseConfigure dimConfig = new BaseConfigure();
                        dimConfig.setCode(dimCode);
                        Order order = new Order();
                        order.setSortType(SortType.DESC);
                        dimConfig.setOrder(order);
                        dataSource.getConfigureList().add(dimConfig);
                    }
                }

            }
        }

        //过滤器
        dataSource.getFilterList().addAll(this.calcParam.getFilterList());
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

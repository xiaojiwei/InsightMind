package com.graphinsight.indicator.lax.filter.function.impl;

import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.lax.filter.Node;
import com.graphinsight.indicator.lax.filter.function.Function;
import com.graphinsight.indicator.lax.tools.Tuple;
import com.graphinsight.indicator.model.Cell;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.RedisCacheService;
import com.graphinsight.indicator.service.impl.ChartQueryServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.List;

@Service
public class WorkdayFun implements Function<List, Node> {

    private String dimCode;

    private ChartQueryService chartQueryService;

    private Tuple tuple;

    public static CacheProperties.EhCache EH_CAHCE = new CacheProperties.EhCache();

    @Override
    public Function build(List dimCodeList, Tuple tuple, ChartQueryService chartQueryService) {

        this.dimCode = String.valueOf(((Node)dimCodeList.get(0)).result);
        this.tuple = tuple;
        this.chartQueryService = chartQueryService;

        return this;

    }

    @Override
    public Node apply() {

        Node node = new Node();

        /**
         * 1、获取指定维度的RowCell
         * 2、根据维度code获取维列信息
         * 3、获取筛选条件
         * 4、查询当前所在的工作日。
         *
         */
        Cell cell = this.findCell(dimCode);
        ViewType viewType = cell.getViewType();

        String value = cell.getId();
        String column = "date_key";
        if (ViewType.YEAR.equals(viewType)) {
            column = "year_key";
        } else if (ViewType.SEASON.equals(viewType)) {
            column = "quarter_key";
        } else if (ViewType.MONTH.equals(viewType)) {
            column = "month_key";
        } else if (ViewType.WEEK.equals(viewType)) {
            column = "week_key";
        } else if (ViewType.DAY.equals(viewType)) {
            column = "date_key";
        }

        String sql = "select count(1) from eps_dim.dim_base_date where day_type_id=1 and " + column + "='" + value + "'";
        RedisCacheService redisCacheService = this.chartQueryService.getRedisCacheService();

        Long count = redisCacheService.get(sql, Long.class);
        if (null == count) {
            Object[] args = new Object[] {value};
            int[] argType = {Types.VARCHAR};
            JdbcTemplate jdbcTemplate = this.chartQueryService.getJdbcTemplate();
            count = jdbcTemplate.queryForObject(sql, Long.class);

            redisCacheService.setIfAbsent(sql, String.valueOf(count));
        }

        node.setResult(count);

        return node;

    }

    /**
     * 根据维度code定位到cell
     *
     * @param dimCode
     * @return
     */
    private Cell findCell(String dimCode) {
        List<Cell> rowCells = this.tuple.getRowCells();
        for (Cell rowCell : rowCells) {

            String cellCode = rowCell.getCode();
            if (dimCode.equalsIgnoreCase(cellCode)) {
                return rowCell;
            }
        }
        return null;
    }

}

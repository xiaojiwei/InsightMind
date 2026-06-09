package com.graphinsight.indicator.lax.filter.function.impl;

import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.lax.filter.Node;
import com.graphinsight.indicator.lax.filter.function.Function;
import com.graphinsight.indicator.lax.tools.Tuple;
import com.graphinsight.indicator.model.Cell;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.RedisCacheService;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.List;

@Service
public class SelectColumnsFun implements Function<List, Node> {

    private List<String> dimCodeList;

    private ChartQueryService chartQueryService;

    private Tuple tuple;

    @Override
    public Function build(List dimCodeList, Tuple tuple, ChartQueryService chartQueryService) {

        for (Object dimCodeNode : dimCodeList) {
            String dimCode = String.valueOf(((Node)dimCodeNode).result);
            this.dimCodeList.add(dimCode);
        }

        this.tuple = tuple;
        this.chartQueryService = chartQueryService;

        return this;

    }

    @Override
    public Node apply() {

        Node node = new Node();

        StringBuilder builder = new StringBuilder();
        for (String dimCode : this.dimCodeList) {
            Cell cell = this.findCell(dimCode);
            String value = null;
            if (cell == null) {
                value = "null";
            } else {
                value = cell.getData();
            }

            builder.append(value);

        }
        node.setResult(builder.toString());

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

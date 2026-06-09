package com.graphinsight.indicator.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据矩阵
 */
@Data
public class CellSet extends BaseModel {

    private Axis columnAxis;

    private Axis rowAxis;

    private List<Cell> cellList;

    private static class Cell {
        String value;
        String formattedValue;
    }

    public static CellSet buildNull() {

        CellSet cellSet = new CellSet();
        cellSet.setCellList(new ArrayList<Cell>());
        cellSet.setColumnAxis(Axis.buildNull());
        cellSet.setRowAxis(Axis.buildNull());

        return cellSet;

    }

}

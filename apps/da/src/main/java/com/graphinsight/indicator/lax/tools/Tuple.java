package com.graphinsight.indicator.lax.tools;

import com.graphinsight.indicator.model.BuildSqlTuple;
import com.graphinsight.indicator.model.Cell;
import lombok.Data;

import java.util.List;

@Data
public class Tuple {

    /**
     * 所有结果数据
     */
    List<List<Cell>> cellList;

    /**
     * 行数据
     */
    List<Cell> rowCells;

    /**
     * sqlTuple
     */
    BuildSqlTuple buildSqlTuple;


}

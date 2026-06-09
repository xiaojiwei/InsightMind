package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.*;

/**
 * 交叉表构建服务
 */
public interface PivotService {

    /**
     * 构建 CellSet 多维数据结果集
     * @param tuple
     * @param cubeTuple
     * @param orgTuple
     * @return
     */
    CellSet buildCellSet(BuildSqlTuple tuple, DataTuple cubeTuple, DataTuple orgTuple);

    /**
     * 构建 Matrix 结果矩阵
     * @param tuple
     * @param result
     * @return
     */
    Matrix buildMatrix(BuildSqlTuple tuple, QueryResult result);

    /**
     * 根据矩阵内容二次计算左下RowSpan
     * @param matrix
     * @return
     */
    void resetCoorColSpan(Matrix matrix);

}

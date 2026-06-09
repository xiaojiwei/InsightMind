package com.graphinsight.indicator.model;

import lombok.Data;

import java.sql.Connection;

/**
 * cube数据元信息
 */
@Data
public class DataTuple {

    public DataTuple(TempTable tempTable, Connection conn) {
        this.tempTable = tempTable;
        this.conn = conn;
    }

    /**
     * 数据表
     */
    private TempTable tempTable;

    /**
     * 连接
     */
    private Connection conn;

}

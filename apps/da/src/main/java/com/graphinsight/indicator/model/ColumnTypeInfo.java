package com.graphinsight.indicator.model;

import lombok.Data;

@Data
public class ColumnTypeInfo {

    /**
     * 列名称
     */
    private String name;

    /**
     * 列类型
     */
    private String type;

    private ColumnTypeInfo(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public static ColumnTypeInfo build(String name, String type) {

        String mysqlType = "varchar";
        if ("-5".equals(type)) {
            mysqlType = "double";
        } else if ("-6".equals(type)) {
            mysqlType = "double";
        } else if ("4".equals(type)) {
            mysqlType = "double";
        } else if ("12".equals(type)) {
            mysqlType = "varchar";
        }

        return new ColumnTypeInfo(name, mysqlType);

    }

}

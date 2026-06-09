package com.graphinsight.indicator.model;

import lombok.Data;

@Data
public class LeftJoinDimTable {

    /**
     * join 维度
     */
    private Dimension dim;

    /**
     * 层次code
     */
    private String hierCode;

    /**
     * 数据源
     */
    private String source;

    /**
     * db
     */
    private String schema;

    /**
     * 关联维度表
     */
    private String tableName;

    /**
     * 维度主键
     */
    private String selfId;

    /**
     * 维度外键
     */
    private String fkId;

    /**
     * 别名
     */
    private String alias;

    /**
     * 表是否含有日期DT
     */
    private boolean hasColumnDT;

    /**
     * 主或次维度本身主键，主纬度时与selfId相同
     */
    private String columnId;

    /**
     * 主或次维度名称
     */
    private String columnName;

    /**
     * 表table
     */
    private Table table;

    /**
     * 换维度
     */
    private boolean replace = false;

    /**
     * 判断left是否可以按退化维处理
     * @return
     */
    public boolean isDegDim() {

        boolean isDegDim = false;
        if (null != this.columnId && null != this.columnName && null != this.selfId) {
            isDegDim = this.columnId.equalsIgnoreCase(this.columnName) && this.columnId.equalsIgnoreCase(this.selfId);
        }

        return isDegDim;

    }

    /**
     * 不需要columnName时，判断基表时是否可以不join维度表。
     * @return
     */
    public boolean isSelfGroupId() {

        boolean isSelf = false;
        if (null != this.columnId && null != this.selfId) {
            isSelf = this.columnId.equalsIgnoreCase(this.selfId);
        }

//        return isSelf;
        return false;

    }

}

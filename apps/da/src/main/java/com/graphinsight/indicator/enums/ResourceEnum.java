package com.graphinsight.indicator.enums;

import com.graphinsight.indicator.auto.entity.Dashboard;
import lombok.Data;


public enum ResourceEnum {

    DATA_SOURCE(0,"数据集"),

    DASHBOARD(1,"数据看板"),

    DISMANTLING_TREE(2,"拆解树"),

    DIMENSION_ANALYSIS_TASK(3,"多维分析"),

    MEASURE_MONITORING(4,"指标预警"),

    GOAL_MANAGEMENT(5,"目标管理");



    private Integer type;

    private String name;

    private void setType(Integer type){
        this.type = type;
    }

    private void setName(String name){
        this.name = name;
    }

    public Integer getType(){
        return this.type;
    }

    public String getName(){
        return this.name;
    }


    ResourceEnum(Integer type,String name){
        this.type = type;
        this.name = name;
    }
}

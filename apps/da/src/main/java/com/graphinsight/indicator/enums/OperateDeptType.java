package com.graphinsight.indicator.enums;

public enum OperateDeptType {

    REGIN(0,"区域"),

    PROVINCE(1,"省份"),

    CITY(2,"城市"),

    STORE(3,"门店"),

    GROUP(4,"小组");


    private Integer type;

    private String name;


    OperateDeptType(Integer type,String name){
        this.type = type;
        this.name = name;
    }


}

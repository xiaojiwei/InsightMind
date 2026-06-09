package com.graphinsight.indicator.enums;


public enum StatPeriodEnum {

    CUR(1,"本周期"),

    PER(0,"上周期");

    private Integer code;

    private String desc;

    StatPeriodEnum(Integer code,String desc){
        this.code = code;
        this.desc = desc;
    }

    public String getDesc(){
        return desc;
    }

    public Integer code(){
        return code;
    }

    public static StatPeriodEnum getByCode(Integer code){
        if (code==null){
            return null;
        }
        StatPeriodEnum[] elements = StatPeriodEnum.values();
        for (StatPeriodEnum statPeriodEnum : elements){
            if (statPeriodEnum.code.equals(code)){
                return statPeriodEnum;
            }
        }
        return null;
    }

}

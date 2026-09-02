package com.graphinsight.indicator.model.vo;


import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class TaskScheduleVO {


    /**
     * 所选作业类型:
     * 1  -> 每天
     * 2  -> 每月
     * 3  -> 每周
     * 4  ->间隔（每隔2个小时，每隔30分钟）
     */
    @NotNull(message = "定时时间类型不能为空")
    Integer jobType;

    Integer[] dayOfWeeks;

    Integer[] dayOfMonths;


    Integer minute;


    Integer hour;

    public Integer getJobType() {
        return jobType;
    }


}

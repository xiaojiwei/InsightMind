package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * 轴
 */
public enum CronEnum {

    DAILY_REPEAT(0,"0 0 9 * * ? "),
    WEEK_REPEAT(1,"0 0 9 ? * MON"),
    MONTH_REPEAT(2,"0 0 9 01 * ?");

    private Integer value;

    private String cron;

    CronEnum(int value, String cron) {
        this.value = value;
        this.cron = cron;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public String getCron() {
        return cron;
    }

    public static CronEnum findByInt(Integer value) {
        for (CronEnum item : CronEnum.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item).orElse(null);
            }
        }

        return null;
    }

}

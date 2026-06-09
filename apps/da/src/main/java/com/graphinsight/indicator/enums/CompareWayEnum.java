package com.graphinsight.indicator.enums;

import com.graphinsight.indicator.util.NumberFormatUtil;

import java.math.BigDecimal;

/**
 * Author: lixiaolong
 * Date: 2022/10/12
 * Desc:
 */
public enum CompareWayEnum {

    EQ(0, "等于"),
    NOT_EQ(1, "不等于"),
    GT(2, "大于"),
    GE(3,"大于等于"),
    LT(4,"小于"),
    LE(5,"小于等于"),
    BETWEEN(6,"区间"),
    NOT_BETWEEN(7,"区间不在");

    private int code;
    private String desc;

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static CompareWayEnum getByCode(Integer code){
        if (code == null){
            return null;
        }
        CompareWayEnum[] values = CompareWayEnum.values();
        for (CompareWayEnum value : values) {
            if (value.code == code.intValue()){
                return value;
            }
        }
        return null;
    }

    public static Boolean compare(BigDecimal realValue, String threshold, Integer code){
        CompareWayEnum compareWayEnum = getByCode(code);
        if (compareWayEnum == null || realValue == null ){
            return null;
        }

        BigDecimal startValue = null;
        BigDecimal endValue = null;

        switch (compareWayEnum){
            case EQ:
                if (threshold == null){
                    return null;
                }
                return realValue.compareTo(NumberFormatUtil.format(threshold)) == 0;
            case NOT_EQ:
                if (threshold == null){
                    return null;
                }
                return realValue.compareTo(NumberFormatUtil.format(threshold)) != 0;

            case GE:
                if (threshold == null){
                    return null;
                }
                return realValue.compareTo(NumberFormatUtil.format(threshold)) >= 0;

            case GT:
                if (threshold == null){
                    return null;
                }
                return realValue.compareTo(NumberFormatUtil.format(threshold)) > 0;
            case LE:
                if (threshold == null){
                    return null;
                }
                return realValue.compareTo(NumberFormatUtil.format(threshold)) <= 0;
            case LT:
                if (threshold == null){
                    return null;
                }
                return realValue.compareTo(NumberFormatUtil.format(threshold)) < 0;
            case BETWEEN:
                String[] split = threshold.split(",");
                startValue = NumberFormatUtil.format(split[0]);
                endValue = NumberFormatUtil.format(split[1]);
                if (startValue == null || endValue == null){
                    return null;
                }
                return realValue.compareTo(startValue) >= 0 && realValue.compareTo(endValue) <= 0;

            case NOT_BETWEEN:
                String[] split2 = threshold.split(",");
                startValue = NumberFormatUtil.format(split2[0]);
                endValue = NumberFormatUtil.format(split2[1]);
                if (startValue == null || endValue == null){
                    return null;
                }
                return realValue.compareTo(startValue) <= 0 || realValue.compareTo(endValue) >= 0;
        }
        return null;
    }



    CompareWayEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

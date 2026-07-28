package com.graphinsight.indicator.util;

import com.graphinsight.indicator.exception.IndicatorParamNotValidException;

/**
 * Date: 2022/9/2
 * Desc:
 */
public class IndicatorAssert {

    public static void indicatorAssert(boolean flag, String errorMsg){
        if (flag){
            throw IndicatorParamNotValidException.error(errorMsg);
        }
    }
}

package com.graphinsight.indicator.exception;

/**
 * Author: lixiaolong
 * Date: 2022/7/12
 * Desc:基尼系数计算异常
 */
public class GiniCalculationException extends Exception{

    public GiniCalculationException(Throwable cause) {
        super(cause);
    }

    public GiniCalculationException(String message) {
        super(message);
    }

    public static GiniCalculationException error(String message){
        return new GiniCalculationException(message);
    }

    public static GiniCalculationException error(Exception e){
        return new GiniCalculationException(e);
    }

}

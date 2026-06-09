package com.graphinsight.indicator.exception;

/**
 * Author: lixiaolong
 * Date: 2022/2/11
 * Desc:
 */
public class IndicatorParamNotValidException extends RuntimeException {

    public IndicatorParamNotValidException() {
    }





    public IndicatorParamNotValidException(Throwable cause) {
        super(cause);
    }

    public IndicatorParamNotValidException(String message) {
        super(message);
    }

    public static IndicatorParamNotValidException error(String message){
        return new IndicatorParamNotValidException(message);
    }

    public static IndicatorParamNotValidException error(RuntimeException e){
        return new IndicatorParamNotValidException(e);
    }

    public static IndicatorParamNotValidException error(String message, Object data){
        return new IndicatorParamNotValidException(message);
    }
}

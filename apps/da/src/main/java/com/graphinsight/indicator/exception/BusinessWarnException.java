package com.graphinsight.indicator.exception;

import com.graphinsight.indicator.enums.ResultCode;

/**
 */
public class BusinessWarnException extends RuntimeException {

    private int code;

    public BusinessWarnException(String message) {
        super(message);
        this.code = 200;
    }

    public BusinessWarnException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessWarnException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public int getCode() {
        return code;
    }
}

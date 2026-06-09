package com.graphinsight.indicator.exception;

import com.graphinsight.indicator.enums.ResultCode;

/**
 * 业务警告异常（替代私有 lixiang-framework SDK 中的同名类）
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

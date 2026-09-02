package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.ResponseErrorType;
import lombok.Data;

import java.util.UUID;

@Data
public class Response<T> {

    /**
     * 成功
     */
    public static final Integer SUCCESS = 200;
    public static final String SUCCESS_MSG = "操作成功";

    /**
     * 警告
     */
    public static final Integer WARN = 0;

    /**
     * 错误联系人
     */
    private String errorOwner;

    /**
     * 异常 失败
     */
    public static final Integer FAIL = 500;
    public static final String FAIL_MSG = "服务器开小差";
    public static final Integer UNAUTHORIZED = 401;

    private String message;
    private Integer code;
    private T data;
    public ResponseErrorType errorType;

    /**
     * codeInfo
     */
    private String codeInfo;

    /**
     * 错误信息
     */
    String errorMessage;

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        if (null != errorMessage) {
            try {
                this.codeInfo = this.findCode(errorMessage);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 异常信息堆栈
     */
    StackTraceElement[] errorStackTrace;

    /**
     * traceId整体跟踪
     */
    String traceId;

    String skywalkingTraceId = UUID.randomUUID().toString();

    public Response() {
        this.code = SUCCESS;
        this.message = SUCCESS_MSG;
//        this.traceId = MDC.get("TRACE_ID");
    }

    public static Response ok(){
        return new Response();
    }

    public static Response ok(String msg) {
        Response response = new Response();
        response.setCode(200);
        response.setMessage(msg);
        return response;
    }

    public static <T> Response ok(T data) {
        Response response = new Response();
        response.setCode(200);
        response.setData(data);
        return response;
    }

    public static <T> Response ok(String msg, T data) {
        Response response = new Response();
        response.setCode(200);
        response.setMessage(msg);
        response.setData(data);
        return response;
    }

    public static Response error(String msg) {
        Response response = new Response();
        response.setCode(FAIL);
        response.setMessage(msg);
        return response;
    }

    public static <T> Response error(String msg, T data) {
        Response response = new Response();
        response.setCode(FAIL);
        response.setMessage(msg);
        response.setData(data);
        return response;
    }

    public static <T> Response error(String msg, Integer code,T data) {
        Response response = new Response();
        response.setCode(code);
        response.setMessage(msg);
        response.setData(data);
        return response;
    }

    public static Response error() {
        Response response = new Response();
        response.setCode(FAIL);
        response.setMessage(FAIL_MSG);
        return response;
    }

    public static Response error(RuntimeException e) {
        Response response = new Response();
        response.setCode(FAIL);
        response.setMessage(FAIL_MSG);
        response.setErrorStackTrace(e.getStackTrace());
        return response;
    }

    public static Response error(int code,String message) {
        Response response = new Response();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    public static Response unauthorized() {
        Response response = new Response();
        response.setCode(UNAUTHORIZED);
        response.setMessage("未登录");
        return response;
    }

    public static Response unauthorized(String message) {
        Response response = new Response();
        response.setCode(UNAUTHORIZED);
        response.setMessage(message);
        return response;
    }

    public static Response warn(String msg) {
        Response response = new Response();
        response.setCode(WARN);
        response.setMessage(msg);
        return response;
    }

    public static Response response(int code, String msg) {
        if (code == SUCCESS) {
            return ok(msg);
        }
        if (code == WARN) {
            return warn(msg);
        }
        if (code == FAIL) {
            return error(msg);
        }
        return new Response();
    }

    // 这里的code是为了区分情况用的, 便于前端根据不同情况，进行不同的处理
    // code无需区分正负， 按顺序从1开始排即可
    @Data
    public static class DataWithCode<T> {
        String msg;
        Integer code;
        // 这里， 不同的code，返回的data类型很可能不一样，需要和前端约定好
        T data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

//    private void setTraceId(){
////        traceId = MDC.get("TRACE_ID");
//        traceId = UUID.randomUUID().toString();
//    }

    private String findCode(String info) {
        if (info.indexOf("code:'") > 0 && info.length() > 30) {
            return info.substring(info.indexOf("code:'") + 6, info.indexOf("' name:'"));
        } else {
            return "";
        }

    }

}

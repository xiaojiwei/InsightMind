package com.graphinsight.indicator.handler;

import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.exception.NoAuthorizationException;
import com.graphinsight.indicator.model.Response;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @Description: 参数异常处理类
 * @Date: 2021/11/16
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ResponseBody
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Response handleValidationException(MethodArgumentNotValidException e){
        ObjectError objectError = e.getBindingResult().getAllErrors().stream().findFirst().orElse(null);
        return Response.error(400,objectError.getDefaultMessage());
    }

    @ResponseBody
    @ExceptionHandler(value = IndicatorParamNotValidException.class)
    public Response handleRuntimeException(IndicatorParamNotValidException e){
        return Response.error(400,e.getMessage());
    }

    @ResponseBody
    @ExceptionHandler(value = NoAuthorizationException.class)
    public Response handleRuntimeException(NoAuthorizationException e){
        return Response.error(401,e.getMessage());
    }


    @ResponseBody
    @ExceptionHandler(value = RuntimeException.class)
    public Response handleRuntimeException(RuntimeException e){
        e.printStackTrace();
        return Response.error(e);
    }

}

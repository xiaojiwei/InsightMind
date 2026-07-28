package com.graphinsight.indicator.exception;

/**
 * Date: 2022/2/11
 * Desc:
 */
public class NoAuthorizationException extends RuntimeException {


    public NoAuthorizationException() {
    }



    public NoAuthorizationException(Throwable cause) {
        super(cause);
    }

    public NoAuthorizationException(String message) {
        super(message);
    }

    public static NoAuthorizationException error(String message){
        return new NoAuthorizationException(message);
    }

    public static NoAuthorizationException error(RuntimeException e){
        return new NoAuthorizationException(e);
    }
}

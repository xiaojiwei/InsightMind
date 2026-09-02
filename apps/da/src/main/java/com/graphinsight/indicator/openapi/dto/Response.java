package com.graphinsight.indicator.openapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Response<T> {


    private T data;

    public static <T> Response ok(T data){
        return new Response(data);
    }
}

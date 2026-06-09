package com.graphinsight.indicator.openapi.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "响应体")
public class Response<T> {


    @ApiModelProperty(value = "响应数据",required = true)
    private T data;

    public static <T> Response ok(T data){
        return new Response(data);
    }
}

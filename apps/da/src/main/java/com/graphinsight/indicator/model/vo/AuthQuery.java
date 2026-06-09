package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * Author: lixiaolong
 * Date: 2022/11/28
 * Desc:
 */
@Data
public class AuthQuery {

   @NotNull(message = "授权元素不能为空")
   IndicatorAuthElement authElement;

   Set<String> objectCodes;

   Set<String> elementCodes;

   @Min(value = 1, message = "页码不能小于1")
   @NotNull(message = "页码不能为空")
   Integer pageNo;

   @Min(value = 1,message = "分页大小不能小于1")
   @NotNull(message = "分页大小不能为空")
   Integer pageSize;

   String keyword;
}

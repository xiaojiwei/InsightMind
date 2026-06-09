package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.Set;

/**
 * Author: lixiaolong
 * Date: 2022/3/15
 * Desc:
 */
@Data
public class QueryBaseInfoVO extends BaseVO{

    private Set<String> codes;

}

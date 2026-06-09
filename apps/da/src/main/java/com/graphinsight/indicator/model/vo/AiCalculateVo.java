package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;


@Data
public class AiCalculateVo {


    // 关键字
    private String filterInfo;
    private Set<String> filterInfoSet = new HashSet<>();


}

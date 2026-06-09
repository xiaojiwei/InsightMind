package com.graphinsight.indicator.controller;

import lombok.Data;

import java.util.Set;

/**
 * @Author: lixiaolong
 * @Description:
 * @Date: 2021/11/24
 */
@Data
public class Param {
    Set<String> dimCodeSet;
    Set<String> measCodeSet;
    String dimCode;
}

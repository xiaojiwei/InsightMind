package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Date: 2022/3/28
 * Desc:
 */
@Data
public class UpdateItem {

    private List<Integer> ids;

    private Integer creator;

    private Integer updater;
}

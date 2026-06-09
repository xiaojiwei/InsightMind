package com.graphinsight.indicator.model;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;

@Data
public class FindMeasureTuple {

    /**
     * 目标指标
     */
    private Matrix.Cell targetMeasCell;

    /**
     * 目标指标交叉的维度
     */
    private List<Matrix.Cell> dimCellList = new LinkedList<>();

}

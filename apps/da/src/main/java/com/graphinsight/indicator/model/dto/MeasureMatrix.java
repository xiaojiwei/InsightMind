package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.enums.MeasureType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/8/9
 * Desc: 指标基础信息
 */
@Data
public class MeasureMatrix {

    private Integer id;

    private String code;

    private String cnName;

    private String enName;

    private String column;

    private String alias;


    /**
     * 指标所在的事实表
     */
    private List<FactTable> factTableList = new ArrayList<>();



}

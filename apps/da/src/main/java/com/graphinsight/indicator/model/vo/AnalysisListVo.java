package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class AnalysisListVo {

    // 推荐问题
    private String title;

    // 推荐类型
    private String type;

    private List<String> textList = new ArrayList<>();

}

package com.graphinsight.indicator.model.vo;

import lombok.Data;

@Data
public class DismantlingTreeWrapVO {
    private String queryId;
    private int progress;
    private DismantlingTreeVO dismantlingTree;
}

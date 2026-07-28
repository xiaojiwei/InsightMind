package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Date: 2023/3/6
 * Desc:
 */
@Data
public class BatchRelatedCodeSet extends BaseVO{

    private List<RelatedCodeSet> relatedCodeSetList = new ArrayList<>();
}

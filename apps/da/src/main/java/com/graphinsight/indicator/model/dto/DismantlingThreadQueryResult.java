package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.model.vo.DismantlingTreeNode;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/12/12
 * Desc:
 */
@Data
public class DismantlingThreadQueryResult {

    private Integer index;

    private List<List<DismantlingTreeNode>> nodes = new LinkedList<>();
}

package com.graphinsight.indicator.model.dto;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/11/3
 * Desc:
 */
@Data
public class DismantlingConfigTreeFloor {

    /**
     * 每一层的region
     */
    private List<DismantlingConfigTreeRegion> regions = new LinkedList<>();


}

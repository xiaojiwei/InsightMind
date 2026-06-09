package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2023/2/7
 * Desc:
 */
@Data
public class ModelColumnSyncParam extends BaseVO{

    private List<ModelColumnVO> modelColumns;
}

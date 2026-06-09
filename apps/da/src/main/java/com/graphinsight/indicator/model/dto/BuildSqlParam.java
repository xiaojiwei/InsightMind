package com.graphinsight.indicator.model.dto;

import lombok.Data;

import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/3/10
 * Desc:
 */
@Data
public class BuildSqlParam {

    private List<ColumnItemExp> columnExps;

    private String factTable;

    private String schema;

    private boolean limit0 = true;

}

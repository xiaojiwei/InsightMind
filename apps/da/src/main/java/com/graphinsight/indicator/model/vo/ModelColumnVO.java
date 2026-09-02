package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.enums.EntryType;
import lombok.Data;

/**
 * Date: 2023/2/3
 * Desc:
 */
@Data
public class ModelColumnVO {

    private String columnName;

    private String columnComment;

    private String enName;

    private String cnName;

    private Integer dimensionId;

    private String dataType;

    private EntryType entryType;

    private CategoryVO category;

    private String description;

    private User developer;

    private Integer viewType;
}

package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Date: 2022/2/24
 * Desc:
 */
@Data
public class TableUpdateVO extends BaseVO {

    @NotNull
    private Integer id;

    /**
     * 100-Doris,101-TiDB,102-MySQL
     * 默认Doris
     */
    private Integer sourceType = 100;

    /**
     * 模型英文名
     */
    private String enName;

    /**
     * 模型英文名
     */
    private String tableName;

    /**
     * 模型中文名
     */
    private String cnName;

//    /**
//     * 是否在线 1-下线 0-下线
//     */
//    private Integer online;

    /**
     * 备注
     */
    private String remark;

    /**
     * 业务描述
     */
    private String description;

    /**
     * 叶子分类节点ID
     */
    @LeafCategoryId
    Integer leafCategoryId;

    /**
     * 库名
     */
    private String schemaName;


    private Integer hasDt;

    /**
     * 加工方式
     * 0-聚合表 1-明细表
     */
    private Integer factTableType;

    private String developer;


}

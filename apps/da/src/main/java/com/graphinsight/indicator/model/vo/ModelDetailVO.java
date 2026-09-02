package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/22
 */
@Data
public class ModelDetailVO extends BaseVO{

    private Integer id;

    /**
     * 100-Doris,101-TiDB,102-MySQL
     * 默认Doris
     */
    private Integer sourceType = 100;

    /**
     * 库名
     */
    @NotBlank
    private String schemaName;

    /**
     * 表名
     */
    @NotBlank
    private String tableName;

    /**
     * 模型英文名
     */
    private String enName;

    /**
     * 模型中文名
     */
    private String cnName;

   /**
    * 是否在线 1-下线 0-下线
    */
   private Integer online;

    /**
     * 备注
     */
    private String remark;

    /**
     * 业务描述
     */
    @NotBlank
    private String description;

    private User creator;

    private User developer;

    private Long createTime;

    private User updater;

    private Long updateTime;

    List<ModelFieldVO> modelFieldList;

    /**
     * 叶子分类节点ID
     */
    Integer leafCategoryId;

    /**
     * 是否需要卡dt
     * 0-否 1-是
     */
    private Integer hasDt;




    /**
     * 加工方式
     * 0-聚合表 1-明细表
     */
    private Integer factTableType;





}

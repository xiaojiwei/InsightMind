package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

/**
 * 
 * @author houfenglei
 */
@Getter
@Setter
@TableName(value ="ai_recommend_question")
public class AiRecommendQuestionEntity {

    /**
    * 唯一主键
    */
    @TableId(type = IdType.AUTO)
    @TableField(value = "id")
    private Long id;

    /**
    * 问题类型
    */
    @TableField(value = "type")
    private String type;

    /**
    * 问题类型标题
    */
    @TableField(value = "title")
    private String title;

    /**
    * 问题
    */
    @TableField(value = "info")
    private String info;

    /**
    * 创建时间
    */
    @TableField(value = "create_date")
    private String createDate;

    /**
    * 创建人
    */
    @TableField(value = "creator")
    private String creator;

    /**
    * 修改时间
    */
    @TableField(value = "update_date")
    private String updateDate;

    /**
    * 修改人
    */
    @TableField(value = "updater")
    private String updater;

}

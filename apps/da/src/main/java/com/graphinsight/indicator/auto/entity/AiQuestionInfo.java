package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 
 * @author houfenglei
 */
@Data
public class AiQuestionInfo implements Serializable {

    /**
    * 主键
    */
    @TableId(type = IdType.AUTO)
    @TableField(value = "id")
    private Long id;

    /**
    * 回答是否成功
    */
    @TableField(value = "content")
    private String content;

    /**
    * 搜索内容
    */
    @TableField(value = "reply_info")
    private String replyInfo;

    /**
    * 备注
    */
    @TableField(value = "notes")
    private String notes;

    /**
    * 对话角色
    */
    @TableField(value = "reply_type")
    private String replyType;

    /**
    * 是否追问 0否 1是
    */
    @TableField(value = "category")
    private String category;

    /**
    * 用户名称
    */
    @TableField(value = "user")
    private String user;

    /**
    * 修改用户名称
    */
    @TableField(value = "updater")
    private String updater;

    /**
     * 修改用户名称
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 修改用户名称
     */
    @TableField(value = "update_time")
    private Date updateTime;

    @TableField(value = "is_del")
    private Integer isDel;

}

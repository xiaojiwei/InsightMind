package com.graphinsight.indicator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * 
 * @author houfenglei
 */
@Data
@TableName(value ="ai_gpt_user")
public class AiGptUser {

    /**
    * 主键
    */
    @TableId(type = IdType.AUTO)
    @TableField(value = "id")
    private Long id;

    /**
    * 用户ID
    */
    @TableField(value = "user_id")
    private String userId;

    /**
     * 用户ID
     */
    @TableField(value = "user_name")
    private String userName;

}

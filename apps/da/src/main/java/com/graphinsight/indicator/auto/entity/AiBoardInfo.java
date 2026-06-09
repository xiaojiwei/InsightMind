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
public class AiBoardInfo implements Serializable  {

    /**
    * 唯一主键
    */
    @TableId(type = IdType.AUTO)
    @TableField(value = "id")
    private Long id;

    /**
    * 帆软地址
    */
    @TableField(value = "board_url")
    private String boardUrl;

    @TableField(value = "board_name")
    private String boardName;

    /**
    * 是否删除 0否 1是
    */
    @TableField(value = "is_del")
    private Integer isDel;

    /**
    * 创建时间
    */
    @TableField(value = "create_date")
    private Date createDate;

    /**
    * 创建人
    */
    @TableField(value = "creator")
    private String creator;

    /**
    * 修改时间
    */
    @TableField(value = "update_date")
    private Date updateDate;

    /**
    * 修改人
    */
    @TableField(value = "updater")
    private String updater;

}

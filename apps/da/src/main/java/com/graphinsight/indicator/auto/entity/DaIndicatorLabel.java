package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * Table: da_meas_label
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DaIndicatorLabel implements Serializable{
    private static final long serialVersionUID = 1L;

    /**
     * 唯一主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;
    private Date createTime;
    private Date updateTime;


    /**
     * Column: is_del
     * Type: TINYINT(3)
     * Remark: 是否删除 0否 1是
     */
    private Integer isDel;

}
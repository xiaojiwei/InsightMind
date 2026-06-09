package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-05-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Organization implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 组织code
     */
    private String orgCode;

    /**
     * 组织名称
     */
    private String orgName;

    /**
     * 父组织code
     */
    private String parentCode;

    /**
     * 0-飞书架构 1-运营架构
     */
    private Integer orgType;

    //组织类型，0区域，1省份，2城市，3门店，4小组
    private Integer deptType;

    /**
     * 业务类型 0-飞书 1-售后 2-销售 3-交付 ...
     */
    private Integer bizType;

    private Integer userNum;



}

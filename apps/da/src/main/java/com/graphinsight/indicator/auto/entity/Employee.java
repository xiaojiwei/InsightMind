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
public class Employee implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 域账号(邮箱前缀)
     */
    private String username;

    /**
     * 员工号
     */
    private String jobNum;

    /**
     * 中文名
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 组织code
     */
    private String orgCode;

    /**
     * 0-飞书 1-售后 2-销售 3-交付
     */
    private Integer bizType;

    /**
     * 0-飞书架构 1-运营架构
     */
    private Integer orgType;

    /**
     * 是否可用 0-不可用 1-可用
     */
    private Integer available;


    /**
     * 是否离岗 0-否 1-是
     */
    private Integer offduty;


    /**
     * 员工类型
     * 0 - 内部员工
     * 1 - 三方员工
     * 2 - 外援
     */
    private Integer employeeType;

    /**
     * 员工邮箱
     */
    private String email;


}

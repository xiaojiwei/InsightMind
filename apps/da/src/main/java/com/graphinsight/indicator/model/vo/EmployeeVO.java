package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/5/16
 * Desc:
 */
@Data
public class EmployeeVO {

    private Integer employeeType;

    /**
     * 员工唯一标识
     */
    private String code;

    /**
     * 员工姓名
     */
    private String name;

    /**
     * 部门Code
     */
    private String orgCode;

    /**
     * 头像
     */
    private String avatar;


    /**
     * 员工邮箱
     */
    private String email;

    /**
     * 部门名称全路径
     */
    private String namePath;


    /**
     * 员工所属部门
     */
    private OrganizationTree organization;




}

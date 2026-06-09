package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/3/8
 * Desc:
 */
@Data
public class DepartmentVO {

    private Long id;

    /**
     * 部门全称
     */
    private String fullname;

    /**
     * 名称路径
     */
    private String namePath;

    /**
     * 部门等级
     */
    private Integer deptLevel;

    /**
     * 部门编号
     */
    private String code;

    /**
     * 部门ID
     * 注意不是主键
     */
    private Integer departmentId;

    /**
     * 父级部门ID
     */
    private Integer parentId;

    /**
     * 部门下的用户数
     */
    private Integer userNum;
}

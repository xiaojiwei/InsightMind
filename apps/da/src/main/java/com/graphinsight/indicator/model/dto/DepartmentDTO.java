package com.graphinsight.indicator.model.dto;


import lombok.Data;

/**
 * Date: 2022/5/17
 * Desc:
 */
@Data
public class DepartmentDTO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 飞书部门ID
     */
    private Integer feishuDeptId;

    /**
     * 部门全称
     */
    private String fullname;

    /**
     * 公司ID
     */
    private Integer companyId;

    /**
     * id路径
     */
    private String idPath;

    /**
     * 名称路径
     */
    private String namePath;

    /**
     * 部门等级
     */
    private Integer deptLevel;

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
     * 部门CODD
     */
    private String code;

    /**
     * 员工数量
     */
    private Integer userNum;
}

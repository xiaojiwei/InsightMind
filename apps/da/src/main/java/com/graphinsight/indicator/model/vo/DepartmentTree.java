package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/3/8
 * Desc:
 */
@Data
public class DepartmentTree extends BaseVO {
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

    private List<DepartmentTree> children;
}

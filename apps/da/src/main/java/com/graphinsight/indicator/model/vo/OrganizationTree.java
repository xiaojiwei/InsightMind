package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Date: 2022/3/8
 * Desc:
 */
@Data
public class OrganizationTree extends BaseVO {

    /**
     * 部门名称
     */
    private String name;

    /**
     * 部门路径名
     */
    private String namePath;

    /**
     * 部门唯一标识
     */
    private String code;

    private Integer deptType;

    /**
     * 父级部门code
     */
    private String parentCode;

    private List<OrganizationTree> children = new ArrayList<>();
}

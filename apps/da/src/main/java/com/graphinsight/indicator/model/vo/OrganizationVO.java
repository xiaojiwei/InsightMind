package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.AuthObjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Date: 2022/5/16
 * Desc:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrganizationVO {

    /**
     * ORG(0,"组织"),
     * EMPLOYEE(1,"人员");
     */
    private AuthObjectType authObjectType;

    /**
     * 组织唯一标识
     * 部门：departmentId
     * 员工：username
     */
    private String code;

    /**
     * 名称
     */
    private String name;


    /**
     * 部门下的用户数量
     */
    private Integer userNum;

    /**
     * 头像
     */
    private String avatar;

}

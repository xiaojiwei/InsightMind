package com.graphinsight.indicator.service;

import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.model.dto.DepartmentDTO;
import com.graphinsight.indicator.model.dto.OperateGrantValue;
import com.graphinsight.indicator.model.dto.UserContext;
import com.graphinsight.indicator.model.dto.UserDTO;

import java.util.List;

/**
 * Date: 2022/5/17
 * Desc:
 */
public interface IndicatorUserService {

    /**
     * 获取运营架构授权维值
     * @param operateGrantConfigId
     * @param username
     * @return
     */
    OperateGrantValue getOperateGrantValue(String username,Long operateGrantConfigId);


    /**
     * 获取空间下的用户上下文信息
     * @param spaceId
     * @param username
     * @return
     */
    UserContext getUserContext(Long spaceId,String username);

    /**
     * 获取部门下的所有员工列表(包含子部门员工)
     * @param authObjectType 参数类型
     * @param identCode 识别码
     *
     * @return
     */
    List<UserDTO> listUserByDeptId(AuthObjectType authObjectType, String identCode);


    /**
     * 获取部门详情
     * @param deptId 部门ID
     * @param authObjectType 参数类型
     * @return
     */
    DepartmentDTO getDepartmentByCode(String deptId, AuthObjectType authObjectType);


    /**
     * 判断用户是否属于目标部门
     * @param username
     * @param targetCode
     * @param authObjectType
     * @return
     */
    boolean belongDept(String username,String targetCode, AuthObjectType authObjectType);

}

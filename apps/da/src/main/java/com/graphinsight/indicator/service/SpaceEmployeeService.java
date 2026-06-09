package com.graphinsight.indicator.service;

import com.graphinsight.indicator.enums.RoleType;
import com.graphinsight.indicator.model.Page;
import com.graphinsight.indicator.model.SearchText;
import com.graphinsight.indicator.model.Space;
import com.graphinsight.indicator.model.SpaceEmployee;
import com.graphinsight.indicator.model.vo.OrganizationVO;
import org.springframework.web.bind.annotation.PathVariable;

import javax.persistence.Transient;
import java.util.List;

/**
 * 空间服务接口
 */
public interface SpaceEmployeeService {

    /**
     * 空间人员持久化
     * @param space
     * @return
     */
    Long save(Space space);

    /**
     * 查看空间人员
     * @param spaceId
     * @param employeeCode
     * @param spaceEmpId
     * @return
     */
    SpaceEmployee get(Long spaceId, String employeeCode, Long spaceEmpId);

    /**
     * 删除空间人员
     * @param spaceId
     * @param employeeCode
     * @return
     */
    boolean delete(Long spaceId, String employeeCode);

    /**
     * 查询空间人员接口
     * @param searchText
     * @return
     */
    Page list(SearchText searchText);

    /**
     * 删除空间黑名单
     * @param employeeCode
     * @param space
     */
    Space removeBlacklist(String employeeCode, Space space);

    /**
     * 根据空间id，职员类型获取用户角色
     * @param spaceId
     * @param employeId
     * @return
     */
    RoleType getRoleType(Long spaceId, String employeId);

    /**
     * 根据空间id，职员类型获取用户角色
     * @param spaceId
     * @param employeId
     * @param defRoleType 默认type
     * @return
     */
    RoleType getRoleType(Long spaceId, String employeId, RoleType defRoleType);

    /**
     * 获取组织view
     * @param parentId
     * @param orgType
     * @return
     */
    List<OrganizationVO> get(Integer parentId, Integer orgType);

    /**
     * 补充用户空间下的部门头像
     * @param space
     * @return
     */
    Space applyUserNumAndAvatar(Space space);

    /**
     * 批量授予
     * @param space
     * @return
     */
    List<Space> applyUserNumAndAvatarAndCategoryName(List<Space> space);

    Space applyOwnerNumAndAvatar(Space space);

}

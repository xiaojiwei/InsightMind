package com.graphinsight.indicator.service;

import com.graphinsight.indicator.enums.RoleType;
import com.graphinsight.indicator.model.*;
import org.apache.xpath.operations.Bool;

import java.util.Set;

/**
 * 空间服务接口
 */
public interface SpaceService {

    /**
     * 空间持久化
     * @param space
     * @return
     */
    Long save(Space space);

    /**
     * 查看空间
     * @param id
     * @return
     */
    Space get(Long id);

    /**
     * space
     * @param id
     * @return
     */
    Boolean has(Long id);

    /**
     * 删除空间
     * @param id
     * @return
     */
    boolean delete(Long id);

    /**
     * 查询所有空间接口
     * @param searchText
     * @return
     */
    Page list(SearchText searchText);

    /**
     * 查询所有空间接口，不包含权限关系，对授权列表使用。
     * @param searchText
     * @return
     */
    Page listAll(SearchText searchText);

    /**
     * 根据空间id获取所有拥有的授权指标、维度。
     * @param spaceId
     * @return
     */
    Set<AuthElement> getAuthElementBySpaceId(Long spaceId);

    /**
     * 根据空间id获取所有拥有的授权指标、维度。
     * @param spaceId
     * @return
     */
    Set<AuthElement> getAuthElementBySpaceId(Long spaceId, String employeeCode, Boolean isDetail);

    /**
     * 根据空间id获取所有拥有的授权指标、维度。
     * @param spaceId
     * @return
     */
    Set<AuthElement> getAuthElementBySpaceId(Long spaceId, String employeeCode);

    /**
     * 获取空间下所有角色
     * @param spaceId
     * @return
     */
    Set<RoleType> getRoleSet(Long spaceId);


}

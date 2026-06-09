package com.graphinsight.indicator.service;

import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.model.*;

import java.util.List;
import java.util.Set;

/**
 * 指标、维度授权接口
 */
public interface AuthService {

    /**
     * 指标维度授权持久化
     * @param space
     * @return
     */
    Long save(Space space);

    /**
     * 获取详情
     * @param spaceId
     * @param employeeCode
     * @param authId
     * @return
     */
    Auth get(Long spaceId, String employeeCode, Long authId);

    /**
     * 获取详情 职员信息的全部授权信息
     * @param spaceId
     * @param employeeCode
     * @return
     */
    List<AuthElement> get(Long spaceId, String employeeCode);

    /**
     * 删除授权
     * @param spaceId
     * @param employeeCode
     * @param authElementType
     * @return
     */
    boolean delete(Long spaceId, String employeeCode, AuthElementType authElementType);

    /**
     * 查询授权人员接口
     * @param searchText
     * @return
     */
    Page list(AuthSearchText searchText);

    /**
     * 根据code判断是否是超级管理员
     * @return
     */
    boolean isSuperAdmin();

    /**
     * 根据code判断是否是超级管理员
     * @param employeeCode
     * @return
     */
    boolean isSuperAdmin(String employeeCode);

    void authPeopleApply(Set<String> authMeasCodes, Long spaceId, String userCode, String userNickname, Long userId);
}

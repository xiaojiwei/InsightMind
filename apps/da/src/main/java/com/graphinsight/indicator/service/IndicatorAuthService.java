package com.graphinsight.indicator.service;

import com.graphinsight.indicator.enums.AuthBizType;
import com.graphinsight.indicator.enums.AuthMoudleType;
import com.graphinsight.indicator.enums.IndicatorAuthType;
import com.graphinsight.indicator.model.vo.AuthObject;
import com.graphinsight.indicator.model.vo.AuthQuery;
import com.graphinsight.indicator.model.vo.Grant;
import com.graphinsight.indicator.model.vo.GrantAuth;
import com.graphinsight.indicator.model.vo.IndicatorAuthElement;
import com.graphinsight.indicator.model.vo.PageVO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Date: 2022/11/28
 * Desc: 指标平台授权接口
 */
public interface IndicatorAuthService {

    /**
     * 追加授权接口
     * 对多个用户或者部门授权，授权采用追加逻辑，及saveOrUpdate。不支持取消权限
     */
    void appendGrant(Grant grant);

    /**
     * 覆盖授权接口
     * 授权逻辑是先删除已有的权限，再新增新的权限
     */
    void coverGrant(Grant grant);

    /**
     * 根据资源对象获取授权对象列表
     * @return
     */
    PageVO<GrantAuth> pageObjectByElement(AuthQuery query);

    /**
     * 当前用户拥有的资源权限列表
     * @return
     */
    List<IndicatorAuthType> listAuthByElement(IndicatorAuthElement authElement);

    /**
     * 批量获取资源权限
     * @return
     */
    Map<String,List<IndicatorAuthType>> listAuthByElement(AuthMoudleType moudleType, AuthBizType authBizType, Set<String> codes);



//    /**
//     * 获取权限对象拥有的管理资源列表
//     * @return
//     */
//    List<GrantAuth> listManageElements(AuthObject object);

    /**
     * 获取权限对象拥有的所有资源列表
     * @return
     */
    List<GrantAuth> listElements(List<AuthObject> objects, Long spaceId);

    /**
     * 删除权限资源
     * @param authElements
     */
    void removeElement(List<IndicatorAuthElement> authElements);


    List<AuthObject> currentAuthObject();

    /**
     * 判断用户是否是空间管理员
     * 包括 空间拥有者、空间管理员、超管
     * @return
     */
    boolean isManager(Long spaceId);


}

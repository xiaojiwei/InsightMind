package com.graphinsight.indicator.manager;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.service.ICustomerService;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.IndicatorAuth;
import com.graphinsight.indicator.auto.entity.Portal;
import com.graphinsight.indicator.auto.entity.PortalMenu;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.service.IIndicatorAuthService;
import com.graphinsight.indicator.auto.service.IPortalMenuService;
import com.graphinsight.indicator.auto.service.IPortalService;
import com.graphinsight.indicator.enums.AuthBizType;
import com.graphinsight.indicator.enums.AuthMoudleType;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.IndicatorAuthType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.exception.NoAuthorizationException;
import com.graphinsight.indicator.model.vo.*;
import com.graphinsight.indicator.model.post.Post;
import com.graphinsight.indicator.model.vo.AuthQuery;
import com.graphinsight.indicator.model.vo.Grant;
import com.graphinsight.indicator.model.vo.GrantAuth;
import com.graphinsight.indicator.model.vo.IndicatorAuthElement;
import com.graphinsight.indicator.model.vo.PageVO;
import com.graphinsight.indicator.model.vo.PortalMenuVO;
import com.graphinsight.indicator.model.vo.PortalQuery;
import com.graphinsight.indicator.model.vo.PortalVO;
import com.graphinsight.indicator.model.vo.TreeNode;
import com.graphinsight.indicator.util.IndicatorAssert;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/9/1
 * Desc:
 */
@Slf4j
@Component
public class PortalManager {

    @Resource
    IPortalService portalService;
    @Resource
    IPortalMenuService portalMenuService;
    @Resource
    PortalAuthManager portalAuthManager;
    @Resource
    UserManager userManager;
    @Resource
    DepartmentManager departmentManager;
    @Resource
    PostManager postManager;

    @Resource
    ICustomerService customerService;


    public PageVO<GrantAuth> pageObjectByElement(AuthQuery query) {
        if (StringUtils.hasLength(query.getKeyword())) {
            List<User> users = userManager.getUserBySearchText(query.getKeyword());
            List<Department> departments = departmentManager.listDeptsByText(query.getKeyword());
            List<Post> posts = postManager.listAllPost();
            posts = posts.stream().filter(a-> StringUtils.hasLength(a.getPostName()) && a.getPostName().contains(query.getKeyword())).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(users) || !CollectionUtils.isEmpty(departments) || !CollectionUtils.isEmpty(posts)) {
                Set<String> usernames = users.stream().map(User::getUsername).collect(Collectors.toSet());
                Set<String> departmentnames = departments.stream().map(Department::getFullname).collect(Collectors.toSet());
                Set<String> postCodes = posts.stream().map(Post::getPostCode).collect(Collectors.toSet());
                Set<String> codes = new HashSet<>();
                codes.addAll(usernames);
                codes.addAll(departmentnames);
                codes.addAll(postCodes);
                query.setObjectCodes(codes);
            } else {
                PageVO<GrantAuth> vo = new PageVO<>();
                vo.setData(Collections.EMPTY_LIST);
                vo.setTotal(0L);
                return vo;
            }
        }
        Set<String> codes = listParentCode(query.getAuthElement());
        query.setElementCodes(codes);
        PageVO<GrantAuth> pageVO = portalAuthManager.pageObjectByElement(query);
        List<GrantAuth> grantAuths = pageVO.getData();
        grantAuths.forEach(auth -> {
            String elementCode = auth.getAuthElement().getElementCode();
            auth.setInherit(!Objects.equals(query.getAuthElement().getElementCode(), elementCode));
        });
        return pageVO;
    }

    private Set<String> listParentCode(IndicatorAuthElement authElement) {
        Set<String> codes = new HashSet<>();
        if (Objects.equals(authElement.getBizType(), AuthBizType.PORTAL)) {
            codes.add(authElement.getElementCode());
        } else if (Objects.equals(authElement.getBizType(), AuthBizType.MENU)) {
            Long menuId = Long.valueOf(authElement.getElementCode());
            PortalMenu menu = portalMenuService.getById(menuId);
            IndicatorAssert.indicatorAssert(menu == null, "资源不存在");
            Long portalId = menu.getPortalId();
            codes.add(portalId.toString());
            codes.add(menuId.toString());
            Set<Long> parentsId = new HashSet();
            List<PortalMenu> portalMenus = portalMenuService.list(Wrappers.<PortalMenu>lambdaQuery().eq(PortalMenu::getPortalId, portalId));
            findParentMenu(portalMenus, menu, parentsId);
            Set<String> ids = parentsId.stream().map(id -> id.toString()).collect(Collectors.toSet());
            codes.addAll(ids);
        }
        return codes;
    }

    private void findParentMenu(List<PortalMenu> menus, PortalMenu child, Set<Long> parentsId) {
        if (child != null) {
            List<PortalMenu> parents = menus.stream().filter(menu -> Objects.equals(child.getParentId(), menu.getId())).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(parents)) {
                parents.forEach(parent -> {
                    parentsId.add(parent.getId());
                    findParentMenu(menus, parent, parentsId);
                });
            }
        }
    }


    /**
     * 追加授权
     */
    public void appendGrant(Grant grant) {
        if (!CollectionUtils.isEmpty(grant.getGrantAuths())) {
            if (!portalAuthManager.isManager(grant.getSpaceId())) {
                checkManageGrant(grant.getGrantAuths());
            }
            portalAuthManager.appendGrant(grant);
        }

    }

    /**
     * 检查当前用户是否有管理权限
     *
     * @param grantAuths
     */
    private void checkManageGrant(List<GrantAuth> grantAuths) {
        List<PortalVO> portalVOS = listGrantedPortal(IndicatorAuthType.MANAGE);
        Set<String> portalIds = new HashSet<>();
        Set<Long> menuIds = new HashSet<>();
        portalVOS.forEach(portalVO -> {
            portalIds.add(portalVO.getId().toString());
            findSubMenus(portalVO.getMenus(), menuIds);
        });
        grantAuths.forEach(auth -> {
            if (!Objects.equals(auth.getAuthElement().getModuleType(), AuthMoudleType.PORTAL)) {
                throw NoAuthorizationException.error("模块类型不合法");
            }
            if (Objects.equals(auth.getAuthElement().getBizType(), AuthBizType.PORTAL)) {
                if (!portalIds.contains(auth.getAuthElement().getElementCode())) {
                    throw NoAuthorizationException.error("没有该元素的管理权限");
                }

            } else if (Objects.equals(auth.getAuthElement().getBizType(), AuthBizType.MENU)) {
                if (!menuIds.contains(Long.valueOf(auth.getAuthElement().getElementCode()))) {
                    throw NoAuthorizationException.error("没有该元素的管理权限");
                }
            } else {
                throw NoAuthorizationException.error("业务类型不合法");
            }
        });
    }

    private void findSubMenus(List<PortalMenuVO> menus, Set<Long> menuIds) {
        if (!CollectionUtils.isEmpty(menus)) {
            Set<Long> ids = menus.stream().map(PortalMenuVO::getId).collect(Collectors.toSet());
            menuIds.addAll(ids);
            menus.forEach(menu -> {
                findSubMenus(menu.getChildren(), menuIds);
            });
        }
    }


    /**
     * 覆盖授权
     */
    public void converGrant(Grant grant) {
        if (!CollectionUtils.isEmpty(grant.getGrantAuths())) {
            if (!portalAuthManager.isManager(grant.getSpaceId())) {
                checkManageGrant(grant.getGrantAuths());
            }
            portalAuthManager.coverGrant(grant);
        }
    }


    /**
     * 获取当前用户有特定权限的门户
     *
     * @return
     */
    public List<PortalVO> listGrantedPortal(IndicatorAuthType authType) {
        List<PortalVO> result = new ArrayList<>();
        List<GrantAuth> grantAuths = listGrantAuth(authType);
        List<GrantAuth> portalAuths = grantAuths.stream().filter(auth -> Objects.equals(auth.getAuthElement().getBizType(), AuthBizType.PORTAL)).collect(Collectors.toList());
        List<GrantAuth> menuAuths = grantAuths.stream().filter(auth -> Objects.equals(auth.getAuthElement().getBizType(), AuthBizType.MENU)).collect(Collectors.toList());
        List<PortalVO> portalVOS = portalAuths.stream().map(element -> {
            // 如果元素是门户，则所有门户下的菜单都有权限
            PortalVO vo = detailWithoutAuthCheck(Long.valueOf(element.getAuthElement().getElementCode()), element.getAuthTypes());
            if (vo == null) {
                return null;
            }
            vo.setAuthTypes(element.getAuthTypes());
            return vo;
        }).filter(vo -> Objects.nonNull(vo)).collect(Collectors.toList());
        result.addAll(portalVOS);
        Map<Long, List<IndicatorAuthType>> map = menuAuths.stream().collect(Collectors.toMap(auth -> Long.valueOf(auth.getAuthElement().getElementCode()), auth -> auth.getAuthTypes()));
        if (!CollectionUtils.isEmpty(map.keySet())) {
            List<PortalVO> list = listPortalByMenu(map);
            Set<Long> ids = portalVOS.stream().map(PortalVO::getId).collect(Collectors.toSet());
            list.forEach(vo -> {
                if (!ids.contains(vo.getId())) {
                    result.add(vo);
                }
            });
        }
        return result;
    }

    /**
     * 向下获取所有子菜单，向上获取父级菜单直到门户
     *
     * @return
     */
    private List<PortalVO> listPortalByMenu(Map<Long, List<IndicatorAuthType>> menuAuthMap) {
        List<PortalVO> result = new ArrayList<>();
        List<PortalMenu> portalMenus = portalMenuService.listByIds(menuAuthMap.keySet());
        Map<Long, List<PortalMenu>> portalMap = portalMenus.stream().collect(Collectors.groupingBy(PortalMenu::getPortalId));
        portalMap.forEach((portalId, menus) -> {
            Portal portal = portalService.getById(portalId);
            if (portal != null) {

                List<PortalMenu> innerPortalMenus = portalMenuService.list(Wrappers.<PortalMenu>lambdaQuery().eq(PortalMenu::getPortalId, portalId));
                Map<Long, PortalMenu> menuMap = innerPortalMenus.stream().collect(Collectors.toMap(PortalMenu::getId, menu -> menu));


                // 所有子菜单
                Map<Long, List<IndicatorAuthType>> childIdMap = new HashMap<>();
                menus.forEach(menu -> findChildren(menu, menuAuthMap.get(menu.getId()), innerPortalMenus, childIdMap));

                // 所有父菜单
                Set<Long> parentIds = new HashSet<>();
                menus.forEach(menu -> findParent(menu, innerPortalMenus, parentIds));

                // 结构变换
                List<PortalMenuVO> children = childIdMap.keySet().stream().map(id -> {
                    PortalMenu menu = menuMap.get(id);
                    PortalMenuVO menuVO = convert(menu, null);

                    menuVO.setAuthTypes(childIdMap.get(id));
                    return menuVO;
                }).collect(Collectors.toList());

                List<PortalMenuVO> parent = parentIds.stream().map(id -> {
                    PortalMenu menu = menuMap.get(id);
                    PortalMenuVO menuVO = convert(menu, null);
                    menuVO.setAuthTypes(Arrays.asList(IndicatorAuthType.READ_ONLY));
                    return menuVO;
                }).collect(Collectors.toList());

                List<PortalMenuVO> all = new ArrayList<>();
                all.addAll(parent);
                // 去重
                Set<Long> menuIds = parent.stream().map(PortalMenuVO::getId).collect(Collectors.toSet());
                children.forEach(c -> {
                    if (!menuIds.contains(c.getId())) {
                        all.add(c);
                    }
                });
                PortalVO vo = convertVO(portal, all);
                vo.setAuthTypes(Arrays.asList(IndicatorAuthType.READ_ONLY));
                result.add(vo);
            }
        });
        return result;
    }

    private void findParent(PortalMenu child, List<PortalMenu> all, Set<Long> parentIds) {
        PortalMenu parent = all.stream().filter(menu -> Objects.equals(child.getParentId(), menu.getId())).findFirst().orElse(null);
        if (parent != null) {
            parentIds.add(parent.getId());
            findParent(parent, all, parentIds);
        }
    }


    private void findChildren(PortalMenu parent, List<IndicatorAuthType> parentAuths, List<PortalMenu> all, Map<Long, List<IndicatorAuthType>> childMap) {
        childMap.put(parent.getId(), parentAuths);
        List<PortalMenu> children = all.stream().filter(menu -> Objects.equals(parent.getId(), menu.getParentId())).collect(Collectors.toList());
        Set<Long> ids = children.stream().map(PortalMenu::getId).collect(Collectors.toSet());
        ids.forEach(id -> {
            childMap.put(id, parentAuths);
        });
        children.forEach(menu -> {
            findChildren(menu, parentAuths, all, childMap);
        });
    }

    public List<GrantAuth> listGrantAuth(IndicatorAuthType authType) {
        List<GrantAuth> privilegedElements = portalAuthManager.listElements(portalAuthManager.currentAuthObject(),null)
                .stream()
                .filter(auth -> auth.getAuthTypes().stream().map(type -> type.getCode()).collect(Collectors.toList()).contains(authType.getCode()))
                .filter(auth -> Objects.equals(AuthMoudleType.PORTAL, auth.getAuthElement().getModuleType()))
                .collect(Collectors.toList());
        return privilegedElements;
    }


    public List<PortalVO> list(PortalQuery portalQuery) {
        List<PortalVO> vos = new ArrayList<>();
        QueryWrapper<Portal> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("space_id", portalQuery.getSpaceId())
                .eq("is_delete", YesNoType.NO.getCode())
                .eq(portalQuery.getIsMine(), "creator", UserThreadLocalUtil.getUserName())
                .and(StringUtils.hasLength(portalQuery.getKeyword()), query -> query.like("name", portalQuery.getKeyword()))
        ;
        List<Portal> portals = portalService.list(queryWrapper);
        // 根据用户权限过滤
        if (!portalAuthManager.isManager(portalQuery.getSpaceId())) {
            List<GrantAuth> grantAuths = portalAuthManager.listElements(portalAuthManager.currentAuthObject(),portalQuery.getSpaceId());
            Map<String, List<GrantAuth>> portalMap = grantAuths.stream().filter(auth -> Objects.equals(auth.getAuthElement().getBizType(), AuthBizType.PORTAL)).collect(Collectors.groupingBy(auth -> auth.getAuthElement().getElementCode()));
            List<Long> portalIds = grantAuths.stream().map(auth -> convertPortalId(auth)).filter(id -> Objects.nonNull(id)).collect(Collectors.toList());
            List<Long> menuIds = grantAuths.stream().map(auth -> convertMenuId(auth)).filter(id -> Objects.nonNull(id)).collect(Collectors.toList());
            Set<Long> menuPortalIds = new HashSet<>();
            if (!CollectionUtils.isEmpty(menuIds)) {
                List<PortalMenu> menus = portalMenuService.listByIds(menuIds);
                Set<Long> set = menus.stream().collect(Collectors.groupingBy(PortalMenu::getPortalId)).keySet();
                menuPortalIds.addAll(set);
            }
            for (Portal portal : portals) {
                if (portalIds.contains(portal.getId())) {
                    PortalVO vo = convert(portal, null);
                    GrantAuth grantAuth = portalMap.get(portal.getId().toString()).get(0);
                    vo.setAuthTypes(grantAuth.getAuthTypes());
                    vos.add(vo);
                } else if (menuPortalIds.contains(portal.getId())) {
                    PortalVO vo = convert(portal, null);
                    vo.setAuthTypes(Arrays.asList(IndicatorAuthType.READ_ONLY));
                    vos.add(vo);
                }
            }
        } else {
            // 管理员具有所有权限
            List<PortalVO> list = portals.stream().map(p -> {
                PortalVO vo = convert(p, null);
                vo.setAuthTypes(IndicatorAuthType.MANAGER_AUTH_SET);
                return vo;
            }).collect(Collectors.toList());
            vos.addAll(list);
        }
        return vos;
    }


    private Long convertPortalId(GrantAuth grantAuth) {
        if (Objects.equals(grantAuth.getAuthElement().getBizType(), AuthBizType.PORTAL)) {
            return Long.valueOf(grantAuth.getAuthElement().getElementCode());
        }
        return null;
    }

    private Long convertMenuId(GrantAuth grantAuth) {
        if (Objects.equals(grantAuth.getAuthElement().getBizType(), AuthBizType.MENU)) {
            return Long.valueOf(grantAuth.getAuthElement().getElementCode());
        }
        return null;
    }

    private void sort(PortalVO portalVO) {
        if (portalVO != null) {
            List<PortalMenuVO> portalMenuVOS = sortMenu(portalVO.getMenus());
            portalVO.setMenus(portalMenuVOS);
        }
    }

    private List<PortalMenuVO> sortMenu(List<PortalMenuVO> menus) {
        if (!CollectionUtils.isEmpty(menus)) {
            List<PortalMenuVO> vos = menus.stream().sorted(Comparator.comparing(PortalMenuVO::getSeq)).collect(Collectors.toList());
            vos.forEach(m -> {
                List<PortalMenuVO> sortedMenus = sortMenu(m.getChildren());
                m.setChildren(sortedMenus);
            });
            return vos;
        }
        return menus;
    }

    public PortalVO detailUrl(String url) {
        Portal portal = portalService.getOne(Wrappers.<Portal>lambdaQuery().eq(Portal::getUrl, url));
        if (portal == null) {
            throw IndicatorParamNotValidException.error("门户不存在");
        }
        return detail(portal.getId());
    }

    public PortalVO detail(Long portalId) {
        Portal portal = portalService.getById(portalId);
        if (portal == null) {
            throw IndicatorParamNotValidException.error("门户不存在,ID:" + portalId);
        }
        List<PortalMenu> menus = portalMenuService.list(Wrappers.<PortalMenu>lambdaQuery().eq(PortalMenu::getPortalId, portalId));
        if (!portalAuthManager.isManager(portal.getSpaceId())) {
            PortalVO vo = listGrantedPortal(IndicatorAuthType.READ_ONLY).stream().filter(portalVO -> Objects.equals(portalVO.getId(), portalId)).findFirst().orElse(null);
            if(null == vo){
                throw IndicatorParamNotValidException.error("无该门户权限,请联系空间管理员,ID:" + portalId);
            }
            sort(vo);
            List<CustomerVo> customerVos = new ArrayList<>();
            List <Customer> customers = customerService.list(Wrappers.<Customer>lambdaQuery().eq(Customer ::getPortalId, vo.getId()));
            for(Customer customer : customers){
                customerVos.add(convertCustomerVo(customer));
            }
            vo.setCustomers(customerVos);
            return vo;
        }
        PortalVO convert = convert(portal, menus);
        List<CustomerVo> customerVos = new ArrayList<>();
        List <Customer> customers = customerService.list(Wrappers.<Customer>lambdaQuery().eq(Customer ::getPortalId, convert.getId()));
        for(Customer customer : customers){
            customerVos.add(convertCustomerVo(customer));
        }
        convert.setCustomers(customerVos);
        convert.setAuthTypes(IndicatorAuthType.MANAGER_AUTH_SET);
        setManagerAuth(convert.getMenus());
        return convert;
    }

    private void setManagerAuth(List<PortalMenuVO> portalMenuVOS) {
        if (!CollectionUtils.isEmpty(portalMenuVOS)) {
            portalMenuVOS.forEach(vo -> {
                vo.setAuthTypes(IndicatorAuthType.MANAGER_AUTH_SET);
                setManagerAuth(vo.getChildren());
            });
        }
    }

    private IndicatorAuthElement getAuthElement(Portal portal) {
        IndicatorAuthElement authElement = new IndicatorAuthElement();
        authElement.setModuleType(AuthMoudleType.PORTAL);
        authElement.setBizType(AuthBizType.PORTAL);
        authElement.setElementCode(portal.getId().toString());
        return authElement;
    }

    private IndicatorAuthElement getAuthElement(PortalMenu menu) {
        IndicatorAuthElement authElement = new IndicatorAuthElement();
        authElement.setModuleType(AuthMoudleType.PORTAL);
        authElement.setBizType(AuthBizType.MENU);
        authElement.setElementCode(menu.getId().toString());
        return authElement;
    }

    private PortalVO detailWithoutAuthCheck(Long portalId, List<IndicatorAuthType> portalAuths) {
        Portal portal = portalService.getById(portalId);
        if (portal == null) {
            return null;
        }
        List<PortalMenu> menus = portalMenuService.list(Wrappers.<PortalMenu>lambdaQuery().eq(PortalMenu::getPortalId, portalId));
        return convertWithPortalAuth(portal, menus, portalAuths);
    }

    private PortalVO detailWithoutAuthCheck(Long portalId) {
        Portal portal = portalService.getById(portalId);
        if (portal == null) {
            throw IndicatorParamNotValidException.error("门户不存在");
        }
        List<PortalMenu> menus = portalMenuService.list(Wrappers.<PortalMenu>lambdaQuery().eq(PortalMenu::getPortalId, portalId));
        return convert(portal, menus);
    }

    private PortalVO convertWithPortalAuth(Portal portal, List<PortalMenu> menus, List<IndicatorAuthType> portalAuths) {
        PortalVO portalVO = new PortalVO();
        BeanUtils.copyProperties(portal, portalVO);
        portalVO.setCreator(userManager.getUserByName(portal.getCreator()));
        portalVO.setUpdater(userManager.getUserByName(portal.getUpdater()));
        portalVO.setCreateTime(Timestamp.valueOf(portal.getCreateTime()).getTime());
        portalVO.setUpdateTime(Timestamp.valueOf(portal.getUpdateTime()).getTime());
        if (!CollectionUtils.isEmpty(menus)) {
            Set<String> menuIds = menus.stream().map(menu -> menu.getId().toString()).collect(Collectors.toSet());
            Map<String, List<IndicatorAuthType>> map = portalAuthManager.listAuthByElement(AuthMoudleType.PORTAL, AuthBizType.MENU, menuIds);
            menus.forEach(menu -> {
                if (!map.containsKey(menu.getId().toString())) {
                    // 菜单本身没有配置权限，就继承门户的权限
                    map.put(menu.getId().toString(), portalAuths);
                }
            });
            List<PortalMenuVO> vos = findSubMenus(menus, map);
            portalVO.setMenus(vos);
        }
        return portalVO;
    }


    private PortalVO convert(Portal portal, List<PortalMenu> menus) {
        PortalVO portalVO = new PortalVO();
        BeanUtils.copyProperties(portal, portalVO);
        portalVO.setCreator(userManager.getUserByName(portal.getCreator()));
        portalVO.setUpdater(userManager.getUserByName(portal.getUpdater()));
        portalVO.setCreateTime(Timestamp.valueOf(portal.getCreateTime()).getTime());
        portalVO.setUpdateTime(Timestamp.valueOf(portal.getUpdateTime()).getTime());
        if (!CollectionUtils.isEmpty(menus)) {
            Set<String> menuIds = menus.stream().map(menu -> menu.getId().toString()).collect(Collectors.toSet());
            Map<String, List<IndicatorAuthType>> map = portalAuthManager.listAuthByElement(AuthMoudleType.PORTAL, AuthBizType.MENU, menuIds);
            List<PortalMenuVO> vos = findSubMenus(menus, map);
            portalVO.setMenus(vos);
        }
        return portalVO;
    }

    private PortalVO convertVO(Portal portal, List<PortalMenuVO> menus) {
        PortalVO portalVO = new PortalVO();
        BeanUtils.copyProperties(portal, portalVO);
        portalVO.setCreator(userManager.getUserByName(portal.getCreator()));
        portalVO.setUpdater(userManager.getUserByName(portal.getUpdater()));
        portalVO.setCreateTime(Timestamp.valueOf(portal.getCreateTime()).getTime());
        portalVO.setUpdateTime(Timestamp.valueOf(portal.getUpdateTime()).getTime());
        if (!CollectionUtils.isEmpty(menus)) {
            List<PortalMenuVO> vos = findSubMenuVOS(menus);
            portalVO.setMenus(vos);
        }
        return portalVO;
    }

    private <T> TreeNode<T> convertTree(T t) {
        TreeNode<T> treeNode = new TreeNode<>();
        treeNode.setData(t);
        return treeNode;
    }

    private List<PortalMenuVO> findSubMenus(List<PortalMenu> menus, Map<String, List<IndicatorAuthType>> map) {
        if (CollectionUtils.isEmpty(menus)) {
            return Collections.EMPTY_LIST;
        }
        List<PortalMenu> rootMenus = menus.stream().filter(m -> Objects.isNull(m.getParentId())).collect(Collectors.toList());
        List<PortalMenuVO> roots = rootMenus.stream().map(menu -> convert(menu, map)).sorted(Comparator.comparing(c -> c.getSeq())).collect(Collectors.toList());
        List<PortalMenuVO> vos = menus.stream().map(m -> convert(m, map)).collect(Collectors.toList());
        roots.forEach(r -> findChildren(r, vos));
        return roots;
    }


    private List<PortalMenuVO> findSubMenuVOS(List<PortalMenuVO> menus) {
        if (CollectionUtils.isEmpty(menus)) {
            return Collections.EMPTY_LIST;
        }
        List<PortalMenuVO> rootMenus = menus.stream().filter(m -> Objects.isNull(m.getParentId())).collect(Collectors.toList());
        List<PortalMenuVO> roots = rootMenus.stream().collect(Collectors.toList());
        roots.forEach(r -> findChildren(r, menus));
        return roots;
    }

    private void findChildren(PortalMenuVO parent, List<PortalMenuVO> menus) {
        List<PortalMenuVO> children = menus.stream()
                .filter(folder -> Objects.equals(folder.getParentId(), parent.getId()))
                .sorted(Comparator.comparing(c -> c.getSeq()))
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(children)) {
            children.forEach(c -> findChildren(c, menus));
        }
        parent.getChildren().addAll(children);
    }

    private PortalMenuVO convert(PortalMenu menu, Map<String, List<IndicatorAuthType>> map) {
        PortalMenuVO portalMenuVO = new PortalMenuVO();
        BeanUtils.copyProperties(menu, portalMenuVO);
        if (map != null) {
            portalMenuVO.setAuthTypes(map.get(menu.getId().toString()));
        }
        return portalMenuVO;
    }

    /**
     * 保存/更新门户
     */
    @Transactional(rollbackFor = Exception.class)
    public PortalVO saveOrUpdate(PortalVO portalVO) {
        boolean nameRepeat = nameRepeat(portalVO);
        if (nameRepeat) {
            throw IndicatorParamNotValidException.error("名称【" + portalVO.getName() + "】重复");
        }
        if (StringUtils.hasLength(portalVO.getUrl())) {
            boolean urlRepeat = urlRepeat(portalVO);
            if (urlRepeat) {
                throw IndicatorParamNotValidException.error("URL【" + portalVO.getUrl() + "】重复");
            }
        } else {
            portalVO.setUrl(null);
        }
        PortalVO vo = null;
        if (portalVO.getId() == null) {
            // 创建
            vo = savePortal(portalVO);
        } else {
            // 更新
            vo = updatePortal(portalVO);
        }
        return vo;
    }

    private PortalVO savePortal(PortalVO portalVO) {
        // 创建
        Portal portal = new Portal();
        portal.initCreate();
        BeanUtils.copyProperties(portalVO, portal);
        portal.setStatus(1);
        portalService.save(portal);
        saveMenus(portalVO.getMenus(), portal.getId(), null);
        saveCustomer(portalVO.getCustomers(), portal.getId());
        // 给创建人赋管理权限
        if (!portalAuthManager.isManager(portalVO.getSpaceId())) {
            IndicatorAuth manageAuth = getManageAuth(portal);
            indicatorAuthService.save(manageAuth);
        }
        PortalVO vo = detail(portal.getId());
        return vo;
    }

    private IndicatorAuth getManageAuth(Portal portal) {
        IndicatorAuth indicatorAuth = new IndicatorAuth();
        indicatorAuth.initCreate();
        indicatorAuth.setElementCode(portal.getId().toString());
        indicatorAuth.setBizType(AuthBizType.PORTAL.getCode());
        indicatorAuth.setModuleType(AuthMoudleType.PORTAL.getCode());
        indicatorAuth.setAuthType(IndicatorAuthType.MANAGER_AUTH_SET_STR);
        indicatorAuth.setObjType(AuthObjectType.EMPLOYEE.getValue());
        indicatorAuth.setObjCode(UserThreadLocalUtil.getUserName());
        indicatorAuth.setSpaceId(portal.getSpaceId());
        return indicatorAuth;
    }

    private PortalVO updatePortal(PortalVO portalVO) {
        Portal portal = portalService.getById(portalVO.getId());
        IndicatorAssert.indicatorAssert(portal == null, "门户不存在");
        PortalVO old = detailWithoutAuthCheck(portalVO.getId());
        Set<Long> oldMenuIds = new HashSet<>();
        findSubMenus(old.getMenus(), oldMenuIds);

        flatSaveMenus(portalVO.getMenus(), null, portal.getId());
        Set<Long> newMenuIds = new HashSet<>();
        findSubMenus(portalVO.getMenus(), newMenuIds);

        // old有 new没有 是删除操作
        Set<Long> deleteSet = removeSet(oldMenuIds, newMenuIds);
        if (!CollectionUtils.isEmpty(deleteSet)) {
            List<String> ids = deleteSet.stream().map(id -> id.toString()).collect(Collectors.toList());
            // 删除菜单
            portalMenuService.removeByIds(deleteSet);
            // 删除权限
            indicatorAuthService.remove(Wrappers.<IndicatorAuth>lambdaQuery()
                    .eq(IndicatorAuth::getModuleType, AuthMoudleType.PORTAL.getCode())
                    .eq(IndicatorAuth::getBizType, AuthBizType.MENU.getCode())
                    .in(IndicatorAuth::getElementCode, ids));
        }
        customerService.remove(Wrappers.<Customer>lambdaQuery().eq(Customer ::getPortalId, portalVO.getId()));
        List<Customer> customerList = new ArrayList<>();
        List<CustomerVo> customers = portalVO.getCustomers();
        for (CustomerVo customerVo : customers){
            Customer customer = convertCustomer(customerVo);
            customer.setPortalId(portalVO.getId());
            customerList.add(customer);
        }
        customerService.saveBatch(customerList);
        // 更新门户
        portal.initUpdate();
        BeanUtils.copyProperties(portalVO, portal);
        portalService.updateById(portal);
        return portalVO;
    }

    @Resource
    IIndicatorAuthService indicatorAuthService;

    private void flatSaveMenus(List<PortalMenuVO> menuVOS, Long parentId, Long portalId) {
        if (!CollectionUtils.isEmpty(menuVOS)) {
            menuVOS.stream().forEach(vo -> {
                PortalMenu menu = convert(vo);
                menu.setPortalId(portalId);
                menu.setParentId(parentId);
                portalMenuService.saveOrUpdate(menu);
                vo.setId(menu.getId());
            });
            menuVOS.forEach(vo -> {
                flatSaveMenus(vo.getChildren(), vo.getId(), portalId);
            });
        }
    }

    private Set<Long> commenSet(Set<Long> oldMenuIds, Set<Long> newMenuIds) {
        Set<Long> oldIds = new HashSet<>();
        Set<Long> newIds = new HashSet<>();
        oldIds.addAll(oldMenuIds);
        newIds.addAll(newMenuIds);
        oldIds.retainAll(newIds);
        return oldIds;
    }

    private Set<Long> removeSet(Set<Long> oldMenuIds, Set<Long> newMenuIds) {
        Set<Long> oldIds = new HashSet<>();
        Set<Long> newIds = new HashSet<>();
        oldIds.addAll(oldMenuIds);
        newIds.addAll(newMenuIds);
        oldIds.removeAll(newIds);
        return oldIds;
    }

    private Set<Long> insertSet(Set<Long> oldMenuIds, Set<Long> newMenuIds) {
        Set<Long> oldIds = new HashSet<>();
        Set<Long> newIds = new HashSet<>();
        oldIds.addAll(oldMenuIds);
        newIds.addAll(newMenuIds);
        newIds.removeAll(oldIds);
        return oldIds;
    }


    /**
     * 保存/更新门户
     */
    // @Transactional(rollbackFor = Exception.class)
    // public PortalVO saveOrUpdate(PortalVO portalVO) {
    //     boolean nameRepeat = nameRepeat(portalVO);
    //     if (nameRepeat) {
    //         throw IndicatorParamNotValidException.error("名称【" + portalVO.getName() + "】重复");
    //     }
    //     Portal portal = null;
    //     if (portalVO.getId() == null) {
    //         // 创建
    //         portal = new Portal();
    //         portal.initCreate();
    //         BeanUtils.copyProperties(portalVO, portal);
    //     } else {
    //         // 更新
    //         portal = portalService.getById(portalVO.getId());
    //         IndicatorAssert.indicatorAssert(portal == null, "门户不存在");
    //         portal.initUpdate();
    //         BeanUtils.copyProperties(portalVO, portal);
    //         // 删除原有菜单
    //         removeMenus(portal.getId());
    //     }
    //     // 保存门户
    //     //TODO 保存即生效,后续可能补发布流程
    //     portal.setStatus(1);
    //     portalService.saveOrUpdate(portal);
    //     saveMenus(portalVO.getMenus(), portal.getId(), null);
    //     PortalVO vo = detail(portal.getId());
    //     return vo;
    // }
    private boolean nameRepeat(PortalVO portalVO) {
        List<Portal> portals = portalService.list(Wrappers.<Portal>lambdaQuery()
                .eq(Portal::getName, portalVO.getName())
                .eq(Portal::getSpaceId, portalVO.getSpaceId())
                .eq(Portal::getIsDelete, YesNoType.NO.getCode())
                .ne(Objects.nonNull(portalVO.getId()), Portal::getId, portalVO.getId()));
        return !CollectionUtils.isEmpty(portals);
    }

    private boolean urlRepeat(PortalVO portalVO) {
        List<Portal> portals = portalService.list(Wrappers.<Portal>lambdaQuery()
                .eq(Portal::getUrl, portalVO.getUrl())
                .ne(Objects.nonNull(portalVO.getId()), Portal::getId, portalVO.getId()));
        return !CollectionUtils.isEmpty(portals);
    }

    private void saveMenus(List<PortalMenuVO> menuVOS, Long portalId, Long parentId) {
        if (!CollectionUtils.isEmpty(menuVOS)) {
            for (int i = 0; i < menuVOS.size(); i++) {
                PortalMenuVO vo = menuVOS.get(i);
                PortalMenu portalMenu = convert(vo);
                portalMenu.setParentId(parentId);
                portalMenu.setPortalId(portalId);
                portalMenu.setSeq(i);
                portalMenuService.save(portalMenu);
                saveMenus(vo.getChildren(), portalId, portalMenu.getId());
            }
        }
    }

    private PortalMenu convert(PortalMenuVO portalMenuVO) {
        PortalMenu menu = new PortalMenu();
        BeanUtils.copyProperties(portalMenuVO, menu);
        return menu;
    }

    private IndicatorAuthElement getByMenuId(Long menuId) {
        IndicatorAuthElement authElement = new IndicatorAuthElement();
        authElement.setElementCode(menuId.toString());
        authElement.setModuleType(AuthMoudleType.PORTAL);
        authElement.setBizType(AuthBizType.MENU);
        return authElement;
    }

    /**
     * 删除所有的菜单
     */
    private void removeMenus(Long portalId) {
        List<IndicatorAuthElement> authElements = portalMenuService.list(Wrappers.<PortalMenu>lambdaQuery()
                .eq(PortalMenu::getPortalId, portalId))
                .stream()
                .map(menu -> getByMenuId(menu.getId()))
                .collect(Collectors.toList());
        portalMenuService.remove(Wrappers.<PortalMenu>lambdaQuery().eq(PortalMenu::getPortalId, portalId));
        portalAuthManager.removeElement(authElements);
    }


    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Portal portal = portalService.getById(id);
        if (portal == null) {
            throw IndicatorParamNotValidException.error("门户不存在");
        }
        if (!portalAuthManager.isManager(portal.getSpaceId())) {
            List<GrantAuth> grantAuths = listGrantAuth(IndicatorAuthType.EDIT);
            List<Long> authIds = grantAuths.stream().map(auth -> convertPortalId(auth)).filter(portaId -> Objects.nonNull(portaId)).collect(Collectors.toList());
            if (!authIds.contains(id)) {
                throw NoAuthorizationException.error("无权限");
            }
        }
        portal.setIsDelete(YesNoType.YES.getCode());
        portalService.updateById(portal);
    }

    private void saveCustomer(List<CustomerVo> customerVos, Long portalId) {
        if (!CollectionUtils.isEmpty(customerVos)) {
            for (int i = 0; i < customerVos.size(); i++) {
                CustomerVo vo = customerVos.get(i);
                Customer customer = convertCustomer(vo);
                customer.setPortalId(portalId);
                customerService.save(customer);
            }
        }
    }

    private Customer convertCustomer(CustomerVo customerVo) {
        Customer customer = new Customer();
        BeanUtils.copyProperties(customerVo, customer);
        return customer;
    }

    private CustomerVo convertCustomerVo(Customer customer) {
        CustomerVo customerVo = new CustomerVo();
        BeanUtils.copyProperties(customer, customerVo);
        return customerVo;
    }
}

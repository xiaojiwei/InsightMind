package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.IndicatorAuth;
import com.graphinsight.indicator.auto.entity.Organization;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.DepartmentMapper;
import com.graphinsight.indicator.auto.service.IDepartmentService;
import com.graphinsight.indicator.auto.service.IIndicatorAuthService;
import com.graphinsight.indicator.auto.service.IOrganizationService;
import com.graphinsight.indicator.enums.AuthBizType;
import com.graphinsight.indicator.enums.AuthMoudleType;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.IndicatorAuthObjectType;
import com.graphinsight.indicator.enums.IndicatorAuthType;
import com.graphinsight.indicator.enums.RoleType;
import com.graphinsight.indicator.manager.DepartmentManager;
import com.graphinsight.indicator.manager.PostManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.post.Post;
import com.graphinsight.indicator.model.post.PostEmp;
import com.graphinsight.indicator.model.vo.AuthObject;
import com.graphinsight.indicator.model.vo.AuthQuery;
import com.graphinsight.indicator.model.vo.Grant;
import com.graphinsight.indicator.model.vo.GrantAuth;
import com.graphinsight.indicator.model.vo.IndicatorAuthElement;
import com.graphinsight.indicator.model.vo.PageVO;
import com.graphinsight.indicator.service.AuthService;
import com.graphinsight.indicator.service.IndicatorAuthService;
import com.graphinsight.indicator.service.SpaceService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Date: 2022/11/28
 * Desc:
 */
@Service
@Slf4j
public class BaseIndicatorAuthService<T> implements IndicatorAuthService {

    @Resource
    IIndicatorAuthService iIndicatorAuthService;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private IOrganizationService organizationService;

    @Autowired
    private PostManager postManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendGrant(Grant grant) {
        List<GrantAuth> authList = grant.getGrantAuths();
        List<IndicatorAuth> indicatorAuths = authList.stream().map(auth -> convert(auth, grant.getSpaceId())).collect(Collectors.toList());
        List<IndicatorAuth> updateSet = indicatorAuths.stream().filter(auth -> StringUtils.hasLength(auth.getAuthType())).collect(Collectors.toList());
        List<IndicatorAuth> deleteSet = indicatorAuths.stream().filter(auth -> !StringUtils.hasLength(auth.getAuthType())).collect(Collectors.toList());
        updateSet.forEach(auth -> iIndicatorAuthService.saveOrUpdate(auth, Wrappers.<IndicatorAuth>lambdaQuery()
                .eq(IndicatorAuth::getModuleType, auth.getModuleType())
                .eq(IndicatorAuth::getBizType, auth.getBizType())
                .eq(IndicatorAuth::getObjType, auth.getObjType())
                .eq(IndicatorAuth::getObjCode, auth.getObjCode())
                .eq(IndicatorAuth::getSpaceId, grant.getSpaceId())
                .eq(IndicatorAuth::getElementCode, auth.getElementCode())
        ));

        deleteSet.forEach(auth -> iIndicatorAuthService.remove(Wrappers.<IndicatorAuth>lambdaQuery()
                .eq(IndicatorAuth::getModuleType, auth.getModuleType())
                .eq(IndicatorAuth::getBizType, auth.getBizType())
                .eq(IndicatorAuth::getObjType, auth.getObjType())
                .eq(IndicatorAuth::getObjCode, auth.getObjCode())
                .eq(IndicatorAuth::getSpaceId, grant.getSpaceId())
                .eq(IndicatorAuth::getElementCode, auth.getElementCode())
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void coverGrant(Grant grant) {
        /**
         * 先删除改资源的权限
         */
        List<GrantAuth> authList = grant.getGrantAuths();
        authList.forEach(auth -> {
            iIndicatorAuthService.remove(Wrappers.<IndicatorAuth>lambdaQuery()
                    .eq(IndicatorAuth::getModuleType, auth.getAuthElement().getModuleType().getCode())
                    .eq(IndicatorAuth::getBizType, auth.getAuthElement().getBizType().getCode())
                    .eq(IndicatorAuth::getSpaceId, grant.getSpaceId())
                    .eq(IndicatorAuth::getObjType, auth.getAuthObject().getAuthObjectType().getCode())
                    .eq(IndicatorAuth::getObjCode, auth.getAuthObject().getCode())
                    .eq(IndicatorAuth::getElementCode, auth.getAuthElement().getElementCode()));
        });
        /**
         * 插入新的权限
         */
        List<IndicatorAuth> indicatorAuths = authList.stream().map(auth -> convert(auth, grant.getSpaceId())).collect(Collectors.toList());
        List<IndicatorAuth> updateSet = indicatorAuths.stream().filter(auth -> StringUtils.hasLength(auth.getAuthType())).collect(Collectors.toList());
        List<IndicatorAuth> deleteSet = indicatorAuths.stream().filter(auth -> !StringUtils.hasLength(auth.getAuthType())).collect(Collectors.toList());
        iIndicatorAuthService.saveBatch(updateSet);
        deleteSet.forEach(auth -> iIndicatorAuthService.remove(Wrappers.<IndicatorAuth>lambdaQuery()
                .eq(IndicatorAuth::getModuleType, auth.getModuleType())
                .eq(IndicatorAuth::getBizType, auth.getBizType())
                .eq(IndicatorAuth::getObjType, auth.getObjType())
                .eq(IndicatorAuth::getObjCode, auth.getObjCode())
                .eq(IndicatorAuth::getSpaceId, grant.getSpaceId())
                .eq(IndicatorAuth::getElementCode, auth.getElementCode())
        ));
    }

    @Resource
    UserManager userManager;
    @Resource
    IDepartmentService departmentService;


    private void fillPost(List<Post> posts, AuthObject authObject){
        Post post = posts.stream().filter(p -> Objects.equals(p.getPostCode(), authObject.getCode())).findFirst().orElse(new Post());
        authObject.setCode(post.getPostCode());
        authObject.setName(post.getPostName());
    }


    @Override
    public PageVO<GrantAuth> pageObjectByElement(AuthQuery query) {
        IndicatorAuthElement element = query.getAuthElement();
        Page<IndicatorAuth> page = iIndicatorAuthService.page(new Page<>(query.getPageNo(), query.getPageSize()), Wrappers.<IndicatorAuth>lambdaQuery()
                .eq(IndicatorAuth::getModuleType, element.getModuleType().getCode())
                .in(CollectionUtils.isNotEmpty(query.getObjectCodes()), IndicatorAuth::getObjCode, query.getObjectCodes())
                .eq(IndicatorAuth::getSpaceId, element.getSpaceId())
                .in(IndicatorAuth::getElementCode, query.getElementCodes()));
        List<IndicatorAuth> records = page.getRecords();
        List<Post> posts = postManager.listAllPost();
        List<GrantAuth> grantAuths = records.stream().map(auth -> convert(auth)).collect(Collectors.toList());
        grantAuths.forEach(auth -> {
            AuthObject authObject = auth.getAuthObject();
            if (Objects.equals(authObject.getAuthObjectType(), IndicatorAuthObjectType.EMPLOYEE.getCode())) {
                User user = userManager.getUserByName(authObject.getCode());
                BeanUtils.copyProperties(user, authObject);
            } else if (Objects.equals(authObject.getAuthObjectType(), IndicatorAuthObjectType.ORG.getCode())) {
                // TODO 获取组织信息
                Department department = departmentService.getOne(Wrappers.<Department>lambdaQuery().eq(Department::getDepartmentId, Integer.valueOf(authObject.getCode())));
                fillDept(department, authObject);
            } else if (Objects.equals(authObject.getAuthObjectType(), IndicatorAuthObjectType.OPERATE.getCode())) {
                // TODO 获取运营架构信息
            } else if (Objects.equals(authObject.getAuthObjectType(), IndicatorAuthObjectType.POST.getCode())) {
                // 获取岗位信息
                fillPost(posts, authObject);
            }
        });
        PageVO<GrantAuth> pageVO = new PageVO<>();
        pageVO.setTotal(page.getTotal());
        pageVO.setData(grantAuths);
        return pageVO;
    }

    private void fillDept(Department department, AuthObject authObject) {
        if (department != null) {
            authObject.setNamepath(department.getNamePath());
            authObject.setName(department.getFullname());
            authObject.setCode(department.getDepartmentId().toString());
        }
    }

    @Override
    public List<IndicatorAuthType> listAuthByElement(IndicatorAuthElement authElement) {
        if (authElement == null) {
            return Collections.EMPTY_LIST;
        }
        IndicatorAuth auth = iIndicatorAuthService.getOne(Wrappers.<IndicatorAuth>lambdaQuery()
                .eq(IndicatorAuth::getModuleType, authElement.getModuleType().getCode())
                .eq(IndicatorAuth::getObjType, AuthObjectType.EMPLOYEE.getValue())
                .eq(IndicatorAuth::getBizType, authElement.getBizType().getCode())
                .eq(IndicatorAuth::getElementCode, authElement.getElementCode()));
        if (auth == null) {
            return Collections.EMPTY_LIST;
        }
        return IndicatorAuthType.listByCodes(auth.getAuthType());
    }

    @Override
    public Map<String, List<IndicatorAuthType>> listAuthByElement(AuthMoudleType moudleType, AuthBizType authBizType, Set<String> codes) {
        if (moudleType == null || authBizType == null || CollectionUtils.isEmpty(codes)) {
            return Collections.EMPTY_MAP;
        }
        List<IndicatorAuth> indicatorAuths = iIndicatorAuthService.list(Wrappers.<IndicatorAuth>lambdaQuery()
                .eq(IndicatorAuth::getModuleType, moudleType.getCode())
                .eq(IndicatorAuth::getBizType, authBizType.getCode())
                .in(IndicatorAuth::getElementCode, codes));
        Map<String, List<IndicatorAuthType>> map = new HashMap<>();
        indicatorAuths.forEach(auth -> {
            String elementCode = auth.getElementCode();
            List<IndicatorAuthType> indicatorAuthTypes = map.get(elementCode);
            if (indicatorAuthTypes == null) {
                indicatorAuthTypes = new ArrayList<>();
                map.put(elementCode, indicatorAuthTypes);
            }
            indicatorAuthTypes.addAll(IndicatorAuthType.listByCodes(auth.getAuthType()));
        });
        return map;
    }

//    @Override
//    public List<GrantAuth> listManageElements(AuthObject object) {
//        List<IndicatorAuth> indicatorAuths = iIndicatorAuthService.list(Wrappers.<IndicatorAuth>lambdaQuery()
//                .eq(IndicatorAuth::getSpaceId, object.getSpaceId())
//                .eq(IndicatorAuth::getObjType, object.getAuthObjectType().getCode())
//                .eq(IndicatorAuth::getAuthType, IndicatorAuthType.MANAGE.getCode())
//                .eq(IndicatorAuth::getObjCode, object.getCode()));
//        List<GrantAuth> grantAuths = indicatorAuths.stream().map(auth -> convert(auth)).collect(Collectors.toList());
//        return grantAuths;
//    }

    @Override
    public List<GrantAuth> listElements(List<AuthObject> objects, Long spaceId) {
        Set<String> objCodes = objects.stream().map(AuthObject::getCode).collect(Collectors.toSet());
        List<IndicatorAuth> indicatorAuths = iIndicatorAuthService.list(Wrappers.<IndicatorAuth>lambdaQuery()
                .eq(Objects.nonNull(spaceId), IndicatorAuth::getSpaceId, spaceId)
                .in(IndicatorAuth::getObjCode, objCodes));
        List<GrantAuth> grantAuths = indicatorAuths.stream().map(auth -> convert(auth)).collect(Collectors.toList());
        return grantAuths;
    }

    @Override
    public void removeElement(List<IndicatorAuthElement> authElements) {
        authElements.forEach(element -> {
            iIndicatorAuthService.remove(Wrappers.<IndicatorAuth>lambdaQuery()
                    .eq(IndicatorAuth::getModuleType, element.getModuleType().getCode())
                    .eq(IndicatorAuth::getBizType, element.getBizType().getCode())
                    .eq(IndicatorAuth::getElementCode, element.getElementCode()));
        });
    }

    @Override
    public List<AuthObject> currentAuthObject() {
        List<AuthObject> authObjects = new ArrayList<>();
        authObjects.add(currentUser());
        authObjects.addAll(currentDepts());
        authObjects.addAll(currentPost());
        return authObjects;
    }

    public List<AuthObject> currentPost(){
        List<AuthObject> authObjects = new ArrayList<>();
        UserThreadLocalUtil.get().getEmail();

        PostEmp postInfo = postManager.getPostInfo(UserThreadLocalUtil.get().getJobNumber());
        if (postInfo != null && StringUtils.hasLength(postInfo.getPostCode()) && "0".equals(postInfo.getStatus())){
            AuthObject authObject = new AuthObject();
            authObject.setCode(postInfo.getPostCode());
            authObjects.add(authObject);
            authObject.setAuthObjectType(IndicatorAuthObjectType.POST);
        }
        return authObjects;
    }

    private List<AuthObject> currentDepts() {
        User user = userManager.getUserByName(UserThreadLocalUtil.getUserName());
        if (user == null || user.getDepartmentId() == null) {
            return Collections.EMPTY_LIST;
        }
        Integer departmentId = user.getDepartmentId();
        List<Department> departments = departmentManager.listAllDepartmentByChildId(Long.valueOf(departmentId));
        List<AuthObject> authObjectList = departments.stream().map(department -> {
            AuthObject authObject = new AuthObject();
            authObject.setCode(department.getDepartmentId().toString());
            authObject.setAuthObjectType(IndicatorAuthObjectType.ORG);
            return authObject;
        }).collect(Collectors.toList());

        return authObjectList;
    }

    private AuthObject currentUser() {
        AuthObject authObject = new AuthObject();
        authObject.setCode(UserThreadLocalUtil.getUserName());
        authObject.setAuthObjectType(IndicatorAuthObjectType.EMPLOYEE);
        return authObject;
    }

    @Resource
    DepartmentManager departmentManager;
    @Resource
    AuthService authService;
    @Resource
    SpaceService spaceService;

    @Override
    public boolean isManager(Long spaceId) {
        if (spaceId == null) {
            return false;
        }
        if (authService.isSuperAdmin()) {
            return true;
        }
        Set<RoleType> roleSet = spaceService.getRoleSet(spaceId);
        if (CollectionUtils.isEmpty(roleSet)) {
            return false;
        }
        if (roleSet.contains(RoleType.ADMIN) || roleSet.contains(RoleType.OWNER)) {
            return true;
        }
        return false;
    }

    private IndicatorAuth convert(GrantAuth grantAuth, Long spaceId) {
        String authTypes = grantAuth.getAuthTypes().stream().map(auth -> auth.getCode().toString()).collect(Collectors.joining(IndicatorAuthType.COMMA));
        IndicatorAuth indicatorAuth = new IndicatorAuth();
        indicatorAuth.initCreate();
        indicatorAuth.setAuthType(authTypes);
        indicatorAuth.setObjType(grantAuth.getAuthObject().getAuthObjectType().getCode());
        indicatorAuth.setObjCode(grantAuth.getAuthObject().getCode());
        indicatorAuth.setBizType(grantAuth.getAuthElement().getBizType().getCode());
        indicatorAuth.setModuleType(grantAuth.getAuthElement().getModuleType().getCode());
        indicatorAuth.setElementCode(grantAuth.getAuthElement().getElementCode());
        indicatorAuth.setSpaceId(spaceId);
        return indicatorAuth;
    }

    private GrantAuth convert(IndicatorAuth indicatorAuth) {
        GrantAuth grantAuth = new GrantAuth();
        IndicatorAuthElement authElement = new IndicatorAuthElement();
        authElement.setModuleType(AuthMoudleType.getByCode(indicatorAuth.getModuleType()));
        authElement.setBizType(AuthBizType.getByCode(indicatorAuth.getBizType()));
        authElement.setElementCode(indicatorAuth.getElementCode());
        grantAuth.setAuthObject(getAuthObject(indicatorAuth));
        grantAuth.setAuthElement(authElement);
        grantAuth.setAuthTypes(IndicatorAuthType.listByCodes(indicatorAuth.getAuthType()));
        return grantAuth;
    }

    private AuthObject getAuthObject(IndicatorAuth indicatorAuth) {
        AuthObject authObject = new AuthObject();
        authObject.setAuthObjectType(IndicatorAuthObjectType.getByCode(indicatorAuth.getObjType()));
        authObject.setCode(indicatorAuth.getObjCode());
        if (Objects.equals(indicatorAuth.getObjType(), AuthObjectType.ORG.getValue())) {
            Department department = departmentMapper.selectById(indicatorAuth.getObjCode());
            if (department != null) {
                authObject.setName(department.getFullname());
                authObject.setNamepath(department.getNamePath());
            }
        } else if (Objects.equals(indicatorAuth.getObjType(), AuthObjectType.EMPLOYEE.getValue())) {
            User user = userManager.getUserByName(indicatorAuth.getObjCode());
            BeanUtils.copyProperties(user, authObject);
            authObject.setName(user.getNickname());
        } else if (Objects.equals(indicatorAuth.getObjType(), AuthObjectType.OPERATE.getValue())) {
            Organization organization = organizationService.getOne(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgCode, indicatorAuth.getObjCode()));
            if (organization != null) {
                authObject.setName(organization.getOrgName());
                authObject.setNamepath(organization.getOrgName());
            }
        } else if (Objects.equals(indicatorAuth.getObjType(), AuthObjectType.POST.getValue())) {
            Post post = postManager.getPostByCode(indicatorAuth.getObjCode());
            authObject.setName(post.getPostName());
            authObject.setNamepath("-");
        }

        return authObject;
    }


    public <T> T convertT(GrantAuth grantAuth) {

        return null;
    }


}

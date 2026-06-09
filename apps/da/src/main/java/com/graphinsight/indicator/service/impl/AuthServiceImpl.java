package com.graphinsight.indicator.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.Organization;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.DepartmentMapper;
import com.graphinsight.indicator.auto.service.IOrganizationService;
import com.graphinsight.indicator.dao.AuthDao;
import com.graphinsight.indicator.dao.SpaceDao;
import com.graphinsight.indicator.dao.SuperAdminDao;
import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.RoleType;
import com.graphinsight.indicator.manager.PostManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.dto.BaseInfoDTO;
import com.graphinsight.indicator.model.dto.CategoryDTO;
import com.graphinsight.indicator.model.dto.UserDTO;
import com.graphinsight.indicator.model.post.Post;
import com.graphinsight.indicator.service.AuthService;
import com.graphinsight.indicator.service.IndicatorService;
import com.graphinsight.indicator.service.IndicatorUserService;
import com.graphinsight.indicator.service.SpaceEmployeeService;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@DS("mysql")
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private SpaceDao spaceDao;

    @Autowired
    private AuthDao authDao;

    @Autowired
    private SuperAdminDao superAdminDao;

    @Autowired
    private IndicatorUserService indicatorUserService;

    @Autowired
    private IndicatorService indicatorService;

    @Autowired
    private SpaceEmployeeService spaceEmployeeService;

    @Autowired
    private UserManager userManager;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private IOrganizationService organizationService;

    @Autowired
    private PostManager postManager;

    public Space removeBlacklist(String employeeCode, Space space) {

        //空间黑名单删除
        Set<AuthBlacklist> authBlacklistSet = space.getAuthBlacklistSet();
        AuthBlacklist del = null;
        for (AuthBlacklist authBlack : authBlacklistSet) {
            if (authBlack.getEmployeeCode().equalsIgnoreCase(employeeCode)) {
                del = authBlack;
                break;
            }
        }

        space.getAuthBlacklistSet().remove(del);

        return space;

    }

    @Override
    public Long save(Space spaceParam) {

        Space beforSpace = this.spaceDao.getById(spaceParam.getId());
        beforSpace.initUpdate();

        boolean isAppend = spaceParam.isAppend();

        /**
         * 授权管理，页面提交上来的数据。
         */
        Set<Auth> paramAuthSet = spaceParam.getAuthSet();
        //有效期
        Date authDate = spaceParam.getAuthDate();
        //资源类型组织或人员
//        AuthObjectType authObjectType = spaceParam.getAuthObjectType();
        //授权类型
        AuthElementType authElementType = spaceParam.getAuthElementType();
        //授权资源（维度、指标）
        Set<AuthElement> authElementSet = spaceParam.getAuthElementSet();

        for (Auth auth : paramAuthSet) {

            //新增人员先移除黑名单
            beforSpace = this.removeBlacklist(auth.getEmployeeCode(), beforSpace);

            auth.setAuthDate(authDate);
//            auth.setAuthObjectType(authObjectType);
            auth.setAuthElementType(authElementType);
            auth.setSearchIndex(auth.getName()+ ";" + auth.getEmployeeCode());
            Set<AuthElement> cloneAuthElementSet = new HashSet<>();
            for (AuthElement authElement : authElementSet) {

                try {
                    AuthElement cloneAuthElement = (AuthElement) authElement.deepClone();
                    cloneAuthElementSet.add(cloneAuthElement);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

            }

            auth.setAuthElementSet(cloneAuthElementSet);
            auth.initUpdate();

            for (AuthElement authElement : auth.getAuthElementSet()) {
                authElement.setAuthElementType(authElementType);
            }

        }

        if (CollectionUtils.isEmpty(paramAuthSet)) {

            Set<SpaceEmployee> spaceEmpSet = beforSpace.getSpaceEmpSet();
            for (SpaceEmployee spaceEmployee : spaceEmpSet) {

                Auth auth = new Auth();
                auth.setAuthDate(authDate);
                auth.setEmployeeCode(spaceEmployee.getEmployeeCode());
                auth.setAuthObjectType(spaceEmployee.getAuthObjectType());
                auth.setAuthElementType(authElementType);
                auth.setAuthElementSet(authElementSet);
                auth.initUpdate();

                paramAuthSet.add(auth);

            }

        }

        //存在的指标
        Set<Auth> existAuthSet = beforSpace.getAuthSet();
        for (Auth auth : paramAuthSet) {

            boolean isExist = false;
            for (Auth existAuth : existAuthSet) {

                String exitUserName = existAuth.getEmployeeCode();
                String userName = auth.getEmployeeCode();

                AuthObjectType exitAuthObjectType = existAuth.getAuthObjectType();
                AuthObjectType authObjectType = auth.getAuthObjectType();
                AuthElementType exitAuthElementType = existAuth.getAuthElementType();

                if (exitUserName.equalsIgnoreCase(userName)
                        && exitAuthObjectType.equals(authObjectType)
                        && exitAuthElementType.equals(authElementType)) {

                    Auth cloneAuth = null;

                    try {

                        cloneAuth = (Auth) auth.deepClone();
                        cloneAuth.initUpdate();

//                        existAuth.getAuthElementSet().clear();
                        Set<AuthElement> delSet = new HashSet<>();
                        for (AuthElement authElement : cloneAuth.getAuthElementSet()) {

                            AuthElement cloneAuthElement = (AuthElement) authElement.deepClone();

                            for (AuthElement existAuthElement : existAuth.getAuthElementSet()) {

                                if (existAuthElement.getCode().equalsIgnoreCase(cloneAuthElement.getCode())) {
                                    delSet.add(existAuthElement);
                                }

                            }

                        }

                        //已经存在的code需要删掉
                        existAuth.getAuthElementSet().removeAll(delSet);

                        if (!CollectionUtils.isEmpty(paramAuthSet)) {
                            for (Auth paramAuth : paramAuthSet) {
                                //以页面为首
                                if (paramAuth.getEmployeeCode().equalsIgnoreCase(existAuth.getEmployeeCode()) && !isAppend) {
                                    existAuth.getAuthElementSet().clear();
                                }

                            }

                        }

                        for (AuthElement authElement : cloneAuth.getAuthElementSet()) {

                            AuthElement cloneAuthElement = (AuthElement) authElement.deepClone();

                            cloneAuthElement.setAuth(existAuth);
                            existAuth.setAuthDate(authDate);
                            existAuth.getAuthElementSet().add(cloneAuthElement);

                        }

                        existAuth.initUpdate();
                        existAuth.setSearchIndex(auth.getSearchIndex() + ";" + auth.getEmployeeCode());

                        isExist = true;

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                }

                //对存在的授权进行处理
                if (!AuthObjectType.EMPLOYEE.equals(authObjectType)) {

                    //当传入授权是部门时，需要对下级部门和人员进行权限追加。
                    boolean isBelong = this.indicatorUserService.belongDept(exitUserName, auth.getEmployeeCode(), authObjectType);

                    if (isBelong) {
                        Auth cloneAuth = null;
                        try {
                            cloneAuth = (Auth) auth.deepClone();
                            cloneAuth.initUpdate();

                            for (AuthElement authElement : cloneAuth.getAuthElementSet()) {

                                if (existAuth.getAuthElementType().equals(authElement.getAuthElementType())) {
                                    AuthElement cloneAuthElement = (AuthElement) authElement.deepClone();
                                    cloneAuthElement.setAuth(existAuth);
                                    existAuth.setAuthDate(authDate);
                                    existAuth.getAuthElementSet().add(cloneAuthElement);
                                }

                            }

                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }

                    }

                }

            }

            if (!isExist) {
                //如果不存在，则添加到现有值中。
                existAuthSet.add(auth);
            }

        }

        //当前授权追加到组内
        Space newSpace = spaceDao.save(beforSpace);

        return newSpace.getId();

    }

    @Override
    public List<AuthElement> get(Long spaceId, String employeeCode) {
        return null;
    }

    @Override
    public Auth get(Long spaceId, String employeeCode, Long authId) {

        Auth paramAuth = this.authDao.getById(authId);
        Auth auth = null;

        if (Objects.equals(paramAuth.getAuthObjectType(), AuthObjectType.ORG)) {
            Department department = departmentMapper.selectOne(Wrappers.<Department>lambdaQuery().eq(Department::getDepartmentId, employeeCode));
            auth = Auth.builder()
                    .name(department.getFullname())
                    .roleType(spaceEmployeeService.getRoleType(spaceId, employeeCode))
                    .departmentNamePath(department.getNamePath())
                    .authElementType(paramAuth.getAuthElementType())
                    .authElementSet(paramAuth.getAuthElementSet())
                    .mail("")
                    .spaceRoleSet(paramAuth.getSpaceRoleSet())
                    .employeeCode(paramAuth.getEmployeeCode())
                    .authId(paramAuth.getId())
                    .authObjectType(AuthObjectType.ORG)
                    .authDate(paramAuth.getAuthDate())
                    .build();
            auth.setCreateDate(paramAuth.getCreateDate());
        } else if (Objects.equals(paramAuth.getAuthObjectType(), AuthObjectType.EMPLOYEE)) {
            // 查具体员工
            User user = userManager.getUserByName(employeeCode);
            auth = Auth.builder()
                    .name(user.getNickname())
                    .roleType(spaceEmployeeService.getRoleType(spaceId, employeeCode))
                    .departmentNamePath(user.getDepartmentNamePath())
                    .authElementType(paramAuth.getAuthElementType())
                    .authElementSet(paramAuth.getAuthElementSet())
                    .mail(user.getEmail())
                    .spaceRoleSet(paramAuth.getSpaceRoleSet())
                    .employeeCode(paramAuth.getEmployeeCode())
                    .authId(paramAuth.getId())
                    .authObjectType(AuthObjectType.EMPLOYEE)
                    .avatar(user.getAvatar())
                    .authDate(paramAuth.getAuthDate())
                    .build();
            auth.setCreateDate(paramAuth.getCreateDate());
        } else if (Objects.equals(paramAuth.getAuthObjectType(), AuthObjectType.OPERATE)) {
            // 查组运营架构下部门的所有员工
            Organization organization = organizationService.getOne(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgCode, employeeCode));
            auth = Auth.builder()
                    .name(organization.getOrgName())
                    .roleType(spaceEmployeeService.getRoleType(spaceId, employeeCode))
                    .departmentNamePath(organization.getOrgName())
                    .authElementType(paramAuth.getAuthElementType())
                    .authElementSet(paramAuth.getAuthElementSet())
                    .mail("")
                    .spaceRoleSet(paramAuth.getSpaceRoleSet())
                    .employeeCode(paramAuth.getEmployeeCode())
                    .authId(paramAuth.getId())
                    .authObjectType(AuthObjectType.OPERATE)
                    .authDate(paramAuth.getAuthDate())
                    .build();
            auth.setCreateDate(paramAuth.getCreateDate());
        } else if (Objects.equals(paramAuth.getAuthObjectType(), AuthObjectType.POST)) {
            Post post = postManager.getPostByCode(employeeCode);
            auth = Auth.builder()
                    .name(post.getPostName())
                    .roleType(spaceEmployeeService.getRoleType(spaceId, employeeCode))
                    .departmentNamePath("-")
                    .authElementType(paramAuth.getAuthElementType())
                    .authElementSet(paramAuth.getAuthElementSet())
                    .mail("")
                    .spaceRoleSet(paramAuth.getSpaceRoleSet())
                    .employeeCode(paramAuth.getEmployeeCode())
                    .authId(paramAuth.getId())
                    .authObjectType(AuthObjectType.POST)
                    .authDate(paramAuth.getAuthDate())
                    .build();
            auth.setCreateDate(paramAuth.getCreateDate());
        }

        auth = this.applyInfo(auth, spaceId);

        return auth;
    }

    @Override
    public boolean delete(Long spaceId, String delEmployeeCode, AuthElementType delAuthElementType) {

        try {

            Space space = this.spaceDao.getById(spaceId);

            Set<Auth> authSet = space.getAuthSet();
            boolean hasAuth = false;

            for (Auth auth : authSet) {
                AuthElementType authElementType = auth.getAuthElementType();
                String employeeCode = auth.getEmployeeCode();
                if (authElementType.equals(delAuthElementType) && employeeCode.equalsIgnoreCase(delEmployeeCode)) {
                    hasAuth = true;
                    authSet.remove(auth);
                    break;
                }

            }

            space.setAuthSet(authSet);

            if (!hasAuth) {
                //空间黑名单删除
                AuthBlacklist authBlacklist = new AuthBlacklist();
                authBlacklist.setEmployeeCode(delEmployeeCode);
                authBlacklist.setAuthElementType(delAuthElementType);
                space.getAuthBlacklistSet().add(authBlacklist);
            }

            this.spaceDao.save(space);

        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }

        return true;
    }

    @Override
    public boolean isSuperAdmin() {

        String empCode = UserThreadLocalUtil.getUserName();
        Boolean isSuperAdmin = this.isSuperAdmin(empCode);

        return isSuperAdmin;

    }

    @Override
    public boolean isSuperAdmin(String employeeCode) {

        List<SuperAdmin> superAdminList = this.superAdminDao.findByEmpCode(employeeCode);
        return superAdminList.size() > 0;

    }

    @Override
    @Transactional
    public void authPeopleApply(Set<String> authMeasCodes, Long spaceId, String userCode, String userNickname, Long userId) {


        Space space = new Space();

        Set<Auth> authSet = new HashSet<>();

        Auth auth = new Auth();
        SpaceEmployee spaceEmployee = new SpaceEmployee();
        spaceEmployee.setEmployeeCode(userCode);
        spaceEmployee.setName(userNickname);
        spaceEmployee.setAuthObjectType(AuthObjectType.EMPLOYEE);
        space.getSpaceEmpSet().add(spaceEmployee);
        auth.setEmployeeCode(userCode);
        auth.setName(userNickname);
        auth.setId(userId);
        auth.setAuthObjectType(AuthObjectType.EMPLOYEE);
        authSet.add(auth);


        SpaceRole spaceRole = new SpaceRole();
        spaceRole.setRoleType(RoleType.OWNER);
        space.getSpaceRoleSet().add(spaceRole);
        space.setId(spaceId);
        spaceEmployeeService.save(space);


        for (String authMeasCode : authMeasCodes) {
            AuthElement authElement = new AuthElement();
            authElement.setCode(authMeasCode);
            space.getAuthElementSet().add(authElement);
        }

        space.setAuthElementType(AuthElementType.MEASURE);
        space.setAppend(true);
        space.getAuthSet().addAll(authSet);
        save(space);

    }

//    @Override
//    public Page list(AuthSearchText searchText) {
//
//        Long spaceId = searchText.getSpaceId();
//        AuthElementType authElementType = searchText.getAuthElementType();
//        Space space = this.spaceDao.getById(spaceId);
//        Set<Auth> authSet = space.getAuthSet();
//        List<Auth> resultAuth = new LinkedList<>();
//
//        for (Auth auth : authSet) {
//
//            AuthElementType authEleType = auth.getAuthElementType();
//            if (!authEleType.equals(authElementType)) {
//                //非查询项直接跳过
//                continue;
//            }
//
//            String employeeCode = auth.getEmployeeCode();
//            RoleType roleType = RoleType.VISITOR;
////            RoleType roleType = spaceEmployeeService.getRoleType(spaceId, employeeCode);
////            auth.setRoleType(roleType);
//
//            AuthObjectType authObjectType = auth.getAuthObjectType();
//
//            List<UserDTO> userList = indicatorUserService.listUserByDeptId(authObjectType, employeeCode);
//            for (UserDTO user : userList) {
//
//                String code = user.getUsername();
//                if (!this.isExistBlackList(code, space)) {
//
//                    if (AuthObjectType.EMPLOYEE.equals(authObjectType)) {
//                        resultAuth.add(Auth.build(user, auth, roleType));
//                    } else {
//
//                        if (!this.isExistResultList(code, authSet, authElementType)) {
//                            resultAuth.add(Auth.build(user, auth, roleType));
//                        }
//
//                    }
//                }
//
//            }
//
//        }
//
//        Integer pageNo = searchText.getPageNo();
//        Integer pageSize = searchText.getPageSize();
//
//        Page page = new Page();
//
//        List<Auth> searchList = this.searchList(resultAuth, searchText, spaceId);
//        Long cnt = Long.valueOf(searchList.size());
//
//        PageInfo pageInfo = new PageInfo(pageSize);
//        pageInfo.setTotalRows(cnt.intValue());
//        pageInfo.calc();
//        pageInfo.calcRange(pageNo);
//        page.setPageInfo(pageInfo);
//
//        List<Auth> pageList = this.pageList(searchList, pageInfo);
//
//        pageList = this.applyInfo(pageList, spaceId);
//
//        page.setContent(pageList);
//
//        return page;
//    }

    private Set<Auth> pageSearchAuth(AuthSearchText searchText,Set<Auth> set,Page page){
        List<Auth> list = new LinkedList<>(set);
        List<Auth> searchList = new LinkedList<>();

        for (Auth auth : list){
            if (StringUtil.isEmpty(searchText.getText()) || auth.getSearchIndex().contains(searchText.getText())){
                searchList.add(auth);
            }
        }

        Integer pageNo = searchText.getPageNo();
        Integer pageSize = searchText.getPageSize();

        Long cnt = Long.valueOf(searchList.size());

        PageInfo pageInfo = new PageInfo(pageSize);
        pageInfo.setTotalRows(cnt.intValue());
        pageInfo.calc();
        pageInfo.calcRange(pageNo);
        page.setPageInfo(pageInfo);

        return new HashSet<>(searchList.subList(pageInfo.getPageStartRow(),pageInfo.getPageEndRow()));
    }


    @Override
    public Page list(AuthSearchText searchText) {
        Page page = new Page();
        Long spaceId = searchText.getSpaceId();
        AuthElementType authElementType = searchText.getAuthElementType();
        Space space = this.spaceDao.getById(spaceId);
        Set<Auth> authSet = space.getAuthSet();
        authSet = authSet.stream().filter(a -> authElementType != null && authElementType.equals(a.getAuthElementType())).collect(Collectors.toSet());
        authSet = pageSearchAuth(searchText,authSet,page);
        List<Auth> resultAuth = new LinkedList<>();

        Map<AuthObjectType, List<Auth>> authObjectTypeListMap = authSet.stream().collect(Collectors.groupingBy(Auth::getAuthObjectType));

        for (AuthObjectType aot : authObjectTypeListMap.keySet()) {
            List<Auth> auths = authObjectTypeListMap.get(aot);
            Map<String, Auth> authMap = auths.stream().collect(Collectors.toMap(Auth::getEmployeeCode, a -> a));
            List<String> codes = auths.stream().map(Auth::getEmployeeCode).collect(Collectors.toList());

            if (Objects.equals(aot, AuthObjectType.ORG)) {

                List<Department> departments = departmentMapper.selectList(null);
                departments = departments.stream().filter(a -> codes.contains(String.valueOf(a.getDepartmentId()))).collect(Collectors.toList());

                List<Auth> list = new ArrayList<>();
                for (Department a : departments) {
                    Auth originAuth = authMap.getOrDefault(String.valueOf(a.getDepartmentId()), new Auth());
                    Auth auth = Auth.builder()
                            .name(a.getFullname())
                            .roleType(spaceEmployeeService.getRoleType(spaceId, String.valueOf(a.getDepartmentId())))
                            .departmentNamePath(a.getNamePath())
                            .authElementType(originAuth.getAuthElementType())
                            .authElementSet(originAuth.getAuthElementSet())
                            .mail("")
                            .spaceRoleSet(originAuth.getSpaceRoleSet())
                            .employeeCode(originAuth.getEmployeeCode())
                            .authId(originAuth.getId())
                            .build();
                    auth.setCreateDate(originAuth.getCreateDate());
                    list.add(auth);
                }
                resultAuth.addAll(list);

            } else if (Objects.equals(aot, AuthObjectType.EMPLOYEE)) {
                // 查具体员工
                List<User> users = userManager.listUserByUsernames(codes);
                List<Auth> list = new ArrayList<>();
                for (User a : users) {
                    Auth originAuth = authMap.getOrDefault(a.getUsername(), new Auth());

                    Auth auth = Auth.builder()
                            .name(a.getNickname())
                            .roleType(spaceEmployeeService.getRoleType(spaceId, String.valueOf(a.getUsername())))
                            .departmentNamePath(a.getDepartmentNamePath())
                            .authElementType(originAuth.getAuthElementType())
                            .authElementSet(originAuth.getAuthElementSet())
                            .mail(a.getEmail())
                            .spaceRoleSet(originAuth.getSpaceRoleSet())
                            .employeeCode(originAuth.getEmployeeCode())
                            .authId(originAuth.getId())
                            .build();
                    auth.setCreateDate(originAuth.getCreateDate());
                    list.add(auth);
                }
                resultAuth.addAll(list);
            } else if (Objects.equals(aot, AuthObjectType.OPERATE)) {
                // 查组运营架构下部门的所有员工
                List<Organization> organizations = organizationService.list(null);
                organizations = organizations.stream().filter(a -> codes.contains(a.getOrgCode()) && a.getOrgType() == 1).collect(Collectors.toList());

                List<Auth> list = new ArrayList<>();
                for (Organization a : organizations) {
                    Auth originAuth = authMap.getOrDefault(a.getOrgCode(), new Auth());
                    Auth auth = Auth.builder()
                            .name(a.getOrgName())
                            .roleType(spaceEmployeeService.getRoleType(spaceId, String.valueOf(a.getOrgCode())))
                            .departmentNamePath(a.getOrgName())
                            .authElementType(originAuth.getAuthElementType())
                            .authElementSet(originAuth.getAuthElementSet())
                            .mail("")
                            .spaceRoleSet(originAuth.getSpaceRoleSet())
                            .employeeCode(originAuth.getEmployeeCode())
                            .authId(originAuth.getId())
                            .build();
                    auth.setCreateDate(originAuth.getCreateDate());
                    list.add(auth);
                }
                resultAuth.addAll(list);

            } else if (Objects.equals(aot, AuthObjectType.POST)) {
                List<Post> posts = postManager.listAllPost();
                posts = posts.stream().filter(a -> codes.contains(a.getPostCode())).collect(Collectors.toList());

                List<Auth> list = new ArrayList<>();
                for (Post a : posts) {
                    Auth originAuth = authMap.getOrDefault(a.getPostCode(), new Auth());

                    Auth auth = Auth.builder()
                            .name(a.getPostName())
                            .roleType(spaceEmployeeService.getRoleType(spaceId, String.valueOf(a.getPostCode())))
                            .departmentNamePath("-")
                            .authElementType(originAuth.getAuthElementType())
                            .authElementSet(originAuth.getAuthElementSet())
                            .mail("")
                            .spaceRoleSet(originAuth.getSpaceRoleSet())
                            .employeeCode(originAuth.getEmployeeCode())
                            .authId(originAuth.getId())
                            .build();
                    auth.setCreateDate(originAuth.getCreateDate());
                    list.add(auth);
                }
                resultAuth.addAll(list);
            }
        }


        resultAuth = this.applyInfo(resultAuth, spaceId);

        page.setContent(resultAuth);

        return page;
    }

    private Auth applyInfo(Auth auth, Long spaceId) {

        RoleType roleType = spaceEmployeeService.getRoleType(spaceId, auth.getEmployeeCode(), RoleType.VISITOR);
        auth.setRoleType(roleType);

        SpaceRole spaceRole = new SpaceRole();
        spaceRole.setRoleType(roleType);

        auth.getSpaceRoleSet().clear();
        auth.getSpaceRoleSet().add(spaceRole);

        Set<AuthElement> authElementSet = auth.getAuthElementSet();
        for (AuthElement authElement : authElementSet) {

            String measOrDimCode = authElement.getCode();
            if (this.isNumber(measOrDimCode)) {
                Long categoryId = Long.valueOf(measOrDimCode);
                CategoryDTO categoryDTO = this.indicatorService.getCategoryById(categoryId);
                authElement.setName(categoryDTO.getName());
            } else {
                try {
                    BaseInfoDTO baseInfoDTO = this.indicatorService.getByCode(measOrDimCode);
                    authElement.setName(baseInfoDTO.getCnName());
                } catch (Exception ex) {
                    log.error("调用异常:", ex);
                }

            }

        }
        return auth;
    }

    private boolean isNumber(String value) {
        try {
            Long number = Long.valueOf(value);
            return true;
        } catch (Exception ex) {
            return false;
        }

    }

    private List<Auth> applyInfo(List<Auth> pageList, Long spaceId) {

        for (Auth auth : pageList) {
            auth = this.applyInfo(auth, spaceId);
        }

        return pageList;

    }

    private boolean isExistResultList(String code, Set<Auth> authSet, AuthElementType authElementType) {

        for (Auth auth : authSet) {
            if (code.equalsIgnoreCase(auth.getEmployeeCode())
                    && authElementType.equals(auth.getAuthElementType())) {
                return true;
            }
        }

        return false;

    }

    private boolean isExistBlackList(String code, Space space) {


        Set<AuthBlacklist> authBlacklistSet = space.getAuthBlacklistSet();
        for (AuthBlacklist authBlacklist : authBlacklistSet) {
            if (code.equalsIgnoreCase(authBlacklist.getEmployeeCode())) {
                return true;
            }
        }

        return false;

    }

    private List<Auth> searchList(List<Auth> authList, SearchText searchText, Long spaceId) {

        Set<Auth> searchSet = new LinkedHashSet<>();
        String text = searchText.getText();

        for (int i = 0; i < authList.size(); i++) {
            Auth auth = authList.get(i);
            if ((StringUtils.isNotBlank(text) || StringUtil.isNotEmpty(searchText.getElementCode()))) {
                if (this.isSearch(auth, searchText) && this.isSearchEle(auth, searchText)) {
                    searchSet.add(auth);
                }
            } else {
                searchSet.add(auth);
            }


        }

        return new LinkedList<>(searchSet);

    }

    private boolean isSearchEle(Auth self, SearchText searchText) {

        String elementCode = searchText.getElementCode();
        boolean isNull = StringUtils.isBlank(elementCode);
        if (isNull) {
            return isNull;
        }

        boolean isElement = false;

        Set<AuthElement> authElementSet = self.getAuthElementSet();

        if (!CollectionUtils.isEmpty(authElementSet)) {

            for (AuthElement authElement : authElementSet) {

                String code = authElement.getCode();
                if (code.equalsIgnoreCase(elementCode)) {
                    isElement = true;
                    break;
                } else if (this.isNumber(code)) {

                    if (this.indicatorService.belongToCategory(elementCode, code)) {
                        isElement = true;
                        break;
                    }

                }

            }
        }

        return isElement;
    }

    private boolean isSearch(Auth self, SearchText searchText) {

        String text = searchText.getText();
        boolean isNull = StringUtils.isBlank(text);
        if (isNull) {
            return isNull;
        }

        boolean isEmpName = StringUtils.isNotBlank(self.getName()) && self.getName().indexOf(text) >= 0;
        boolean isDeptName = StringUtils.isNotBlank(self.getDepartmentNamePath()) && self.getDepartmentNamePath().indexOf(text) >= 0;
        boolean isEmail = StringUtils.isNotBlank(self.getMail()) && self.getMail().indexOf(text) >= 0;
        boolean isCreate = StringUtils.isNotBlank(String.valueOf(self.getCreateDate())) && String.valueOf(self.getCreateDate()).indexOf(text) >= 0;
        boolean isRole = false;

        Set<SpaceRole> spaceRoleSet = self.getSpaceRoleSet();
        if (!CollectionUtils.isEmpty(spaceRoleSet)) {
            for (SpaceRole spaceRole : spaceRoleSet) {
                String type = spaceRole.getRoleType().getDesc();
                if (type.indexOf(text) >= 0) {
                    isRole = true;
                    break;
                }
            }
        }

        boolean isElement = false;

        return isEmpName || isDeptName || isEmail || isCreate || isRole || isElement;

    }

    private List<Auth> pageList(List<Auth> authList, PageInfo pageInfo) {

        List<Auth> pageList = new LinkedList<>();
        Integer start = pageInfo.getPageStartRow();
        Integer end = pageInfo.getPageEndRow();

        for (int i = 0; i < authList.size(); i++) {

            if (i >= start && i < end) {
                pageList.add(authList.get(i));
            }

        }

        return pageList;

    }

}

package com.graphinsight.indicator.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.graphinsight.indicator.dao.SpaceBlackListDao;
import com.graphinsight.indicator.dao.SpaceDao;
import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.RoleType;
import com.graphinsight.indicator.model.Auth;
import com.graphinsight.indicator.model.AuthElement;
import com.graphinsight.indicator.model.AuthElementMeasure;
import com.graphinsight.indicator.model.Classification;
import com.graphinsight.indicator.model.Dimension;
import com.graphinsight.indicator.model.Filter;
import com.graphinsight.indicator.model.IndicatorTuple;
import com.graphinsight.indicator.model.Operator;
import com.graphinsight.indicator.model.Page;
import com.graphinsight.indicator.model.SearchText;
import com.graphinsight.indicator.model.Space;
import com.graphinsight.indicator.model.SpaceAdmin;
import com.graphinsight.indicator.model.SpaceDepartment;
import com.graphinsight.indicator.model.SpaceEmployee;
import com.graphinsight.indicator.model.SpaceOwner;
import com.graphinsight.indicator.model.SpaceRole;
import com.graphinsight.indicator.model.vo.SpaceVO;
import com.graphinsight.indicator.service.AuthService;
import com.graphinsight.indicator.service.IndicatorUserService;
import com.graphinsight.indicator.service.SpaceEmployeeService;
import com.graphinsight.indicator.service.SpaceService;
import com.graphinsight.indicator.util.MemCacheUtils;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.codecs.MySQLCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@DS("mysql")
@Service
@Transactional
public class SpaceServiceImpl implements SpaceService {

    @Autowired
    private SpaceDao spaceDao;

    @Autowired
    private SpaceBlackListDao spaceBlackListDao;

    @Autowired
    private SpaceEmployeeService spaceEmployeeService;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AuthService authService;

    @Autowired
    private IndicatorUserService indicatorUserService;

    @Override
    public Boolean has(Long id) {

        Long idInt = this.spaceDao.findId(id);
        return null != idInt;

    }

    @Override
    public Long save(Space space) {

        Space beforSpace = null;
        if (space.getId() == null) {
            beforSpace = space;
            beforSpace.initCreate();
        } else {
            beforSpace = this.spaceDao.getById(space.getId());
            beforSpace.getDeptSet().clear();
            beforSpace.getClassificationSet().clear();
            beforSpace.getSpaceAdminSet().clear();
            beforSpace.getSpaceOwnerSet().clear();
//            beforSpace.getSpaceEmpSet().clear();
        }
        beforSpace.initUpdate();

        //空间名称
        beforSpace.setName(space.getName());
        //空间说明
        beforSpace.setRemarks(space.getRemarks());

        //部门
        beforSpace.getDeptSet().addAll(space.getDeptSet());
        //指标分类
        beforSpace.getClassificationSet().addAll(space.getClassificationSet());

        Set<SpaceAdmin> spaceAdminSet = space.getSpaceAdminSet();
        //空间管理员
        beforSpace.getSpaceAdminSet().addAll(spaceAdminSet);

        Set<SpaceOwner> spaceOwnerSet = space.getSpaceOwnerSet();
        //空间拥有者
        beforSpace.getSpaceOwnerSet().addAll(spaceOwnerSet);

        //管理员to人员
        Set<SpaceEmployee> allSpaceEmployee = beforSpace.getSpaceEmpSet();
        allSpaceEmployee = this.applyAdminToEmp(allSpaceEmployee, spaceAdminSet, beforSpace);

        //拥有者to人员
        allSpaceEmployee = this.applyOwnerToEmp(allSpaceEmployee, spaceOwnerSet, beforSpace);

        //空间人员
        beforSpace.getSpaceEmpSet().addAll(allSpaceEmployee);

        Space newSpace = spaceDao.save(beforSpace);

        return newSpace.getId();

    }



    /**
     * 将空间管理授予所辖人员中
     * @param allSpaceEmployee
     * @param spaceAdminSet
     * @return
     */
    private Set<SpaceEmployee> applyAdminToEmp(Set<SpaceEmployee> allSpaceEmployee, Set<SpaceAdmin> spaceAdminSet, Space space) {

        if (!CollectionUtils.isEmpty(spaceAdminSet)) {

            Set<SpaceEmployee> tempAdminSpaceEmpSet = new LinkedHashSet<>();

            for (SpaceAdmin spaceAdmin : spaceAdminSet) {

                //新增管理员先移除黑名单
                space = spaceEmployeeService.removeBlacklist(spaceAdmin.getEmployeeCode(), space);

                //现有职员中是否含有
                boolean hasAdmin = false;
                for (SpaceEmployee spaceEmployee : allSpaceEmployee) {

                    String empCode = spaceEmployee.getEmployeeCode();
                    String adminCode = spaceAdmin.getEmployeeCode();
                    AuthObjectType authObjectType = spaceAdmin.getAuthObjectType();
                    //职员中已经含有管理员
                    if (empCode.equalsIgnoreCase(adminCode)) {

                        hasAdmin = true;

                        boolean hasRoleAdmin = false;
                        //空间职员含有的角色类型
                        Set<SpaceRole> spaceRoleSet = spaceEmployee.getSpaceRoleSet();
                        for (SpaceRole spaceRole : spaceRoleSet) {

                            RoleType roleType = spaceRole.getRoleType();
                            if (RoleType.ADMIN.equals(roleType)) {
                                hasRoleAdmin = true;
                                break;
                            }

                        }
                        //含有人员，但不含有管理员角色
                        if (!hasRoleAdmin) {
                            SpaceRole adminSpaceRole = new SpaceRole();
                            adminSpaceRole.setRoleType(RoleType.ADMIN);
                            spaceEmployee.getSpaceRoleSet().add(adminSpaceRole);
                        }

                    }

                }

                //不含有管理员人员
                if (!hasAdmin) {
                    //空间管理员
                    SpaceEmployee adminSpaceEmployee = new SpaceEmployee();
                    SpaceRole adminSpaceRole = new SpaceRole();
                    adminSpaceRole.setRoleType(RoleType.ADMIN);
                    //权限
                    adminSpaceEmployee.getSpaceRoleSet().add(adminSpaceRole);
                    //空间
                    adminSpaceEmployee.setSpace(space);
                    //职员code
                    adminSpaceEmployee.setEmployeeCode(spaceAdmin.getEmployeeCode());
                    //职员名称
                    adminSpaceEmployee.setName(spaceAdmin.getName());
                    //邮件
                    adminSpaceEmployee.setMail(spaceAdmin.getMail());
                    //职员类型
                    adminSpaceEmployee.setAuthObjectType(spaceAdmin.getAuthObjectType());
                    tempAdminSpaceEmpSet.add(adminSpaceEmployee);

                }

            }

            //所有新增信息全部挂到所有员工上
            allSpaceEmployee.addAll(tempAdminSpaceEmpSet);

        }

        return allSpaceEmployee;

    }

    /**
     * 将空间拥有者授予所辖人员中
     * @param allSpaceEmployee
     * @param spaceOwnerSet
     * @return
     */
    private Set<SpaceEmployee> applyOwnerToEmp(Set<SpaceEmployee> allSpaceEmployee, Set<SpaceOwner> spaceOwnerSet, Space space) {

        if (!CollectionUtils.isEmpty(spaceOwnerSet)) {

            Set<SpaceEmployee> tempOwnerSpaceEmpSet = new LinkedHashSet<>();

            for (SpaceOwner spaceOwner : spaceOwnerSet) {

                //新增管理员先移除黑名单
                space = spaceEmployeeService.removeBlacklist(spaceOwner.getEmployeeCode(), space);

                //现有职员中是否含有
                boolean hasAdmin = false;
                for (SpaceEmployee spaceEmployee : allSpaceEmployee) {

                    String empCode = spaceEmployee.getEmployeeCode();
                    String adminCode = spaceOwner.getEmployeeCode();
                    //职员中已经含有管理员
                    if (empCode.equalsIgnoreCase(adminCode)) {

                        hasAdmin = true;

                        boolean hasRoleAdmin = false;
                        //空间职员含有的角色类型
                        Set<SpaceRole> spaceRoleSet = spaceEmployee.getSpaceRoleSet();
                        for (SpaceRole spaceRole : spaceRoleSet) {

                            RoleType roleType = spaceRole.getRoleType();
                            if (RoleType.OWNER.equals(roleType)) {
                                hasRoleAdmin = true;
                                break;
                            }

                        }
                        //含有人员，但不含有管理员角色
                        if (!hasRoleAdmin) {
                            SpaceRole adminSpaceRole = new SpaceRole();
                            adminSpaceRole.setRoleType(RoleType.OWNER);
                            spaceEmployee.getSpaceRoleSet().add(adminSpaceRole);
                        }

                    }

                }

                //不含有管理员人员
                if (!hasAdmin) {
                    //空间管理员
                    SpaceEmployee ownerSpaceEmployee = new SpaceEmployee();
                    SpaceRole adminSpaceRole = new SpaceRole();
                    adminSpaceRole.setRoleType(RoleType.ADMIN);
                    //权限
                    ownerSpaceEmployee.getSpaceRoleSet().add(adminSpaceRole);
                    //空间
                    ownerSpaceEmployee.setSpace(space);
                    //职员code
                    ownerSpaceEmployee.setEmployeeCode(spaceOwner.getEmployeeCode());
                    //职员名称
                    ownerSpaceEmployee.setName(spaceOwner.getName());
                    //邮件
                    ownerSpaceEmployee.setMail(spaceOwner.getMail());

                    //职员类型
                    ownerSpaceEmployee.setAuthObjectType(spaceOwner.getAuthObjectType());

                    tempOwnerSpaceEmpSet.add(ownerSpaceEmployee);

                }

            }

            //所有新增信息全部挂到所有员工上
            allSpaceEmployee.addAll(tempOwnerSpaceEmpSet);

        }

        return allSpaceEmployee;

    }

    @Override
    public Space get(Long id) {
        Space space = this.spaceDao.getById(id);
        space = spaceEmployeeService.applyUserNumAndAvatar(space);
        return space;
    }

    @Override
    public boolean delete(Long id) {

        try {
            this.spaceDao.deleteById(id);
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }

        return true;
    }

    @Override
    public Set<RoleType> getRoleSet(Long spaceId) {

        Space space = this.spaceDao.getById(spaceId);
        boolean isSuperAdmin = this.authService.isSuperAdmin();

        //对空间设置当前登录人所拥有的权限。
        Set<RoleType> roleTypeSet = this.getRoleBySpaceAndLogin(space);
        if (roleTypeSet.size() > 0 && !isSuperAdmin) {
            space.getRoleTypeSet().addAll(roleTypeSet);
        } else if (isSuperAdmin) {
            space.getRoleTypeSet().add(RoleType.ADMIN);
        }

        return space.getRoleTypeSet();

    }

    @Override
    public Page listAll(SearchText searchText) {

        Integer pageSize = searchText.getPageSize();

        String baseHql = this.buildBaseSql(searchText);
        String hql = "select distinct s " + baseHql;

        Query query = this.entityManager.createQuery(hql);

        query.setFirstResult(0);
        query.setMaxResults(pageSize);
        //当前页数据
        List<Space> list = query.getResultList();
        List<Space> queryResultList = new LinkedList<>();

        //对空间设置当前登录人所拥有的权限。
        for (Space space : list) {
            space.getDeptSet();
        }

        //分页信息
        Page page = new Page();

        //补充相关信息
        queryResultList = spaceEmployeeService.applyUserNumAndAvatarAndCategoryName(list);

        //筛选
        List<Space> searchList = this.searchList(queryResultList, searchText);
        page.setContent(transSpaceVO(searchList));

        return page;
    }

    @Override
    public Page list(SearchText searchText) {

        Integer pageSize = searchText.getPageSize();

        String baseHql = this.buildBaseSql(searchText);
        String hql = "select distinct s " + baseHql;

        Query query = this.entityManager.createQuery(hql);

        query.setFirstResult(0);
        query.setMaxResults(pageSize);
        //当前页数据
        List<Space> list = query.getResultList();
        List<Space> queryResultList = new LinkedList<>();
        boolean isSuperAdmin = this.authService.isSuperAdmin();

        //对空间设置当前登录人所拥有的权限。
        for (Space space : list) {
            Set<RoleType> roleTypeSet = this.getRoleBySpaceAndLogin(space);
            if (roleTypeSet.size() > 0 && !isSuperAdmin) {
                space.getRoleTypeSet().addAll(roleTypeSet);
                queryResultList.add(space);
            } else if (isSuperAdmin) {
                space.getRoleTypeSet().add(RoleType.ADMIN);
                queryResultList.add(space);
            }

            space.getDeptSet();

        }

        //分页信息
        Page page = new Page();

        //补充相关信息
        queryResultList = spaceEmployeeService.applyUserNumAndAvatarAndCategoryName(queryResultList);

        //筛选
        List<Space> searchList = this.searchList(queryResultList, searchText);
        page.setContent(transSpaceVO(searchList));

        return page;

    }

    private List<SpaceVO> transSpaceVO(List<Space> searchList) {

        List<SpaceVO> spaceVOList = new LinkedList<>();
        for (Space space : searchList) {
            spaceVOList.add(SpaceVO.build(space));
        }
        return spaceVOList;

    }

    private List<Space> searchList(List<Space> spaceList, SearchText searchText) {

        List<Space> searchList = new LinkedList<>();
        String text = searchText.getText();

        for (int i = 0; i < spaceList.size(); i++) {
            Space space = spaceList.get(i);
            if (StringUtils.isNotBlank(text)) {
                if (this.isSearch(space, text)) {
                    searchList.add(spaceList.get(i));
                }
            } else {
                searchList.add(spaceList.get(i));
            }


        }

        return searchList;

    }

    private boolean isSearch(Space self, String text) {

        boolean isNull = StringUtils.isBlank(text);
        if (isNull) {
            return isNull;
        }

        boolean isEmpName = StringUtils.isNotBlank(self.getName()) && self.getName().indexOf(text) >= 0;
        boolean isRemarks = StringUtils.isNotBlank(self.getRemarks()) && self.getRemarks().indexOf(text) >= 0;
        boolean isLine = false;

        Set<SpaceDepartment> spaceDeptSet = self.getDeptSet();
        if (!CollectionUtils.isEmpty(spaceDeptSet)) {
            for (SpaceDepartment spaceDept : spaceDeptSet) {
                String name = spaceDept.getName();
                if (name.indexOf(text) >= 0) {
                    isLine = true;
                    break;
                }

            }
        }

        boolean isClassification = false;

        Set<Classification> classSet = self.getClassificationSet();
        if (!CollectionUtils.isEmpty(classSet)) {
            for (Classification classification : classSet) {
                String name = classification.getName();
                if (name.indexOf(text) >= 0) {
                    isClassification = true;
                    break;
                }
            }
        }

        boolean isOwner = false;

        Set<SpaceOwner> spaceOwnerSet = self.getSpaceOwnerSet();
        if (!CollectionUtils.isEmpty(spaceOwnerSet)) {
            for (SpaceOwner spaceOwner : spaceOwnerSet) {
                String name = spaceOwner.getName();
                if (name.indexOf(text) >= 0) {
                    isOwner = true;
                    break;
                }
            }
        }

        String createDateStr = String.valueOf(self.getCreateDate());
        boolean isCreateTime = StringUtils.isNotBlank(createDateStr) && createDateStr.indexOf(text) >= 0;

        return isEmpName || isRemarks || isLine || isClassification || isOwner || isCreateTime;

    }

    /**
     * 根据空间Id获取当前登录人所用有的角色。
     * @param spaceId
     * @return
     */
    private Set<RoleType> getRoleBySpaceIdAndLogin(Long spaceId) {
        Space space = this.spaceDao.getById(spaceId);
        return this.getRoleBySpaceAndLogin(space);
    }


    /**
     * 根据当前登录人、空间获取空间下所有角色列表。
     * @param space
     * @return
     */
    private Set<RoleType> getRoleBySpaceAndLogin(Space space) {

        Set<RoleType> roleTypeSet = new HashSet<>();
        String userName = UserThreadLocalUtil.getUserName();
        RoleType roleType = this.spaceEmployeeService.getRoleType(space.getId(), userName);
        if (null != roleType) {
            roleTypeSet.add(roleType);
        }

        return roleTypeSet;

    }

    public static Set<Dimension> getDimSetBy(String measureCode, Set<AuthElement> authElementSet, IndicatorTuple indicatorTuple) {

        Set<Dimension> dimSet = new HashSet<>();
        List<Filter> filterList = getFilterByAuthMeas(measureCode, authElementSet);

        for (Filter filter : filterList) {
            String dimCode = filter.getCode();
            Set<Dimension> indDimSet = indicatorTuple.getDimensionSet();
            for (Dimension dimension : indDimSet) {
                if (dimCode.equalsIgnoreCase(dimension.getCode())) {
                    dimSet.add(dimension);
                }
            }

        }

        return dimSet;

    }


    public static List<Filter> getFilterByAuthMeas(String measureCode, Set<AuthElement> authElementSet) {

        List<Filter> measFilter = new ArrayList<>();

        for (AuthElement authElement : authElementSet) {

            Set<AuthElementMeasure> authElementMeasureSet = authElement.getAuthElementMeasureSet();
            for (AuthElementMeasure authElementMeasure : authElementMeasureSet) {

                if (authElementMeasure.getMeasCode().equalsIgnoreCase(measureCode)) {
                    measFilter.add(authElement.getFilter());
                }

            }

        }

        return measFilter;

    }

    @Override
    public Set<AuthElement> getAuthElementBySpaceId(Long spaceId, String employeeCode) {
        return getAuthElementBySpaceId(spaceId, employeeCode, false);
    }

    @Override
    @Transactional
    public Set<AuthElement> getAuthElementBySpaceId(Long spaceId, String employeeCode, Boolean isDetail) {

        Set<AuthElement> authElementSet = new HashSet<>();
        Integer isDelFlag = this.spaceBlackListDao.findByEmpCodeAndSpaceId(employeeCode, spaceId);
        if (isDelFlag > 0) {
            return authElementSet;
        }

        if (null != spaceId) {

            Space space = this.spaceDao.getById(spaceId);
            Set<Auth> authSet = space.getAuthSet();
            for (Auth auth : authSet) {

                //授权日期必须小于当前时间
                Date authDate = auth.getAuthDate();
                boolean isPass = true;
                Date nowDate = new Date();
                if (null != authDate && authDate.getTime() <= nowDate.getTime()) {
                    isPass = false;
                }

                //此处只需要根据当前用户唯一标识，增加所拥有的角色。
                if (this.isExist(employeeCode, auth) && isPass) {
                    AuthElementType authElementType = auth.getAuthElementType();
                    Set<AuthElement> authEleSet = auth.getAuthElementSet();
                    for (AuthElement authEle : authEleSet) {
                        authEle.setAuthElementType(authElementType);
//                        if (!this.hasNullOperData(authEle, isDetail)) {
                        authElementSet.add(authEle);
//                        }
                    }
                }
            }
        }

        return authElementSet;

    }
    
    private boolean hasNullOperData(AuthElement authEle, boolean isDetail) {

        Filter filter = null;
        if (isDetail) {
            filter = authEle.getDetailFilter();
        } else {
            filter = authEle.getFilter();
        }
        if (null != filter && null != filter.getOperatorList()) {

            List<Operator> operatorList = filter.getOperatorList();
            if (!CollectionUtils.isEmpty(operatorList)) {
                for (Operator operator : operatorList) {
                    if (CollectionUtils.isEmpty(operator.getDataList())) {
                        return true;
                    }
                }
            }
        }

        return false;

    }

    /**
     * 根据空间Id，获取当前用户在空间内的所有指标维度
     * @param spaceId
     * @return
     */
    public Set<AuthElement> getAuthElementBySpaceId(Long spaceId) {
        String employeeCode = UserThreadLocalUtil.getUserName();
        return this.getAuthElementBySpaceId(spaceId, employeeCode);

    }

    /**
     * 根据用户名称，空间所拥有的信息判断是否一致。
     * @param userName
     * @param auth 用户或部门
     * @return
     */
    private boolean isExist(String userName, Auth auth) {

        boolean isExist = false;
        //授权类型
        AuthObjectType authObjectType = auth.getAuthObjectType();
        String employeeCode = auth.getEmployeeCode();
        if (AuthObjectType.EMPLOYEE.equals(authObjectType) || null == authObjectType) {
            isExist = userName.equalsIgnoreCase(employeeCode);
        } else {
            //to do 判断是部门id则看是否是当前部门的ID

//            isExist = this.indicatorUserService.belongDept(userName, employeeCode, authObjectType);
            isExist = MemCacheUtils.getIsExist(userName, employeeCode, authObjectType, this.indicatorUserService);

        }

        return isExist;

    }

    /**
     * 根据用户名称，空间所拥有的信息判断是否一致。
     * @param userName
     * @param spaceEmployee 用户或部门
     * @return
     */
    private boolean isExist(String userName, SpaceEmployee spaceEmployee) {

        boolean isExist = false;
        //授权类型
        AuthObjectType authObjectType = spaceEmployee.getAuthObjectType();
        String employeeCode = spaceEmployee.getEmployeeCode();
        if (AuthObjectType.EMPLOYEE.equals(authObjectType) || null == authObjectType) {
            isExist = userName.equalsIgnoreCase(employeeCode);
        } else {
            //to do 判断职员是否是存在于部门
            isExist = this.indicatorUserService.belongDept(userName, employeeCode,authObjectType);

        }

        return isExist;

    }

    /**
     * Build Sql
     * @param searchText
     * @return
     */
    private String buildBaseSql(final SearchText searchText) {

        String text = searchText.getText();
        String enters = ESAPI.encoder().encodeForSQL(new MySQLCodec(MySQLCodec.Mode.ANSI), text);
        String hql = "From Space as s "
                + " where 1=1 ";

//        if (!StringUtil.isEmpty(text)) {
//
//            hql += " and (s.name like '%" + enters + "%'"
//                    + " or s.remarks like '%" + enters + "%'"
//                    + " or s.creator like '%" + enters + "%'"
//                    + " or s.updater like '%" + enters + "%'"
//
//                    + " or s.name like '%" + enters + "%'"
//                    + " or s.creator like '%" + enters + "%'"
//                    + " or s.updater like '%" + enters + "%')";
//
//        }

        boolean isMine = searchText.isMine();

        if (isMine) {

            String userName = UserThreadLocalUtil.getUserName();
            hql +=  " and (s.creator = '" + userName + "'"
                    + " or s.creator = '" + userName + "')";

        }

        hql += " order by s.createDate desc";

        return hql;

    }

    /**
     * Build Sql
     * @param hql
     * @return
     */
    private String buildCountSql(String hql) {

        String cntHql = "select count(distinct s) " + hql;
        return cntHql;

    }

}

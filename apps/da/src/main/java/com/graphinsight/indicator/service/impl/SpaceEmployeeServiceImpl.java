package com.graphinsight.indicator.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.Organization;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.DepartmentMapper;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.auto.service.IOrganizationService;
import com.graphinsight.indicator.dao.SpaceDao;
import com.graphinsight.indicator.dao.SpaceEmployeeDao;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.OrganizationType;
import com.graphinsight.indicator.enums.RoleType;
import com.graphinsight.indicator.manager.DepartmentManager;
import com.graphinsight.indicator.manager.PostManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.dto.CategoryDTO;
import com.graphinsight.indicator.model.dto.DepartmentDTO;
import com.graphinsight.indicator.model.dto.UserDTO;
import com.graphinsight.indicator.model.post.Post;
import com.graphinsight.indicator.model.vo.DepartmentVO;
import com.graphinsight.indicator.model.vo.OrganizationVO;
import com.graphinsight.indicator.service.IndicatorService;
import com.graphinsight.indicator.service.IndicatorUserService;
import com.graphinsight.indicator.service.SpaceEmployeeService;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.*;
import java.util.stream.Collectors;

@DS("mysql")
@Service
public class SpaceEmployeeServiceImpl implements SpaceEmployeeService {

    @Autowired
    private SpaceDao spaceDao;

    @Autowired
    private SpaceEmployeeDao spaceEmployeeDao;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private IndicatorUserService indicatorUserService;

    @Autowired
    private DepartmentManager departmentManager;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserManager userManager;

    @Autowired
    private IndicatorService indicatorService;

    @Autowired
    private IOrganizationService organizationService;

    @Autowired
    private SpaceEmployeeService spaceEmployeeService;

    @Autowired
    private PostManager postManager;

    @Override
    public List<Space> applyUserNumAndAvatarAndCategoryName(List<Space> spaceList) {

        for (Space tmpSpace : spaceList) {
//            tmpSpace = this.applyUserNumAndAvatar(tmpSpace);
            tmpSpace = this.applyOwnerNumAndAvatar(tmpSpace);
            Set<Classification> classificationSet = tmpSpace.getClassificationSet();
            for (Classification classification : classificationSet) {

                String classCode = classification.getClassCode();
                try {
                    CategoryDTO dto = indicatorService.getCategoryById(Long.valueOf(classCode));
                    classification.setName(dto.getName());
                } catch (Exception ex) {
                    classification.setName(ex.getMessage());
                }


            }
        }

        return spaceList;
    }

    @Override
    public Space applyOwnerNumAndAvatar(Space space) {

        Set<SpaceDepartment> deptSet = space.getDeptSet();
        for (SpaceDepartment spaceDepartment : deptSet) {

            DepartmentDTO dto = indicatorUserService.getDepartmentByCode(spaceDepartment.getDeptCode(), spaceDepartment.getAuthObjectType());
            spaceDepartment.setUserNum(dto.getUserNum());

        }

        Set<SpaceOwner> spaceOwnerSet = space.getSpaceOwnerSet();
        for (SpaceOwner spaceOwner : spaceOwnerSet) {
            AuthObjectType authObjectType = spaceOwner.getAuthObjectType();
            if (AuthObjectType.EMPLOYEE.equals(authObjectType)) {

                List<UserDTO> userDTOList = indicatorUserService.listUserByDeptId(authObjectType, spaceOwner.getEmployeeCode());
                if (!CollectionUtils.isEmpty(userDTOList)) {
                    UserDTO dto = userDTOList.get(0);
                    spaceOwner.setAvatar(dto.getAvatar());
                }

            } else {
                DepartmentDTO dto = indicatorUserService.getDepartmentByCode(spaceOwner.getEmployeeCode(),authObjectType);
                spaceOwner.setUserNum(dto.getUserNum());
            }
        }

        return space;
    }

    @Override
    public Space applyUserNumAndAvatar(Space space) {

        Set<SpaceDepartment> deptSet = space.getDeptSet();
        for (SpaceDepartment spaceDepartment : deptSet) {

            DepartmentDTO dto = indicatorUserService.getDepartmentByCode(spaceDepartment.getDeptCode(), spaceDepartment.getAuthObjectType());
            spaceDepartment.setUserNum(dto.getUserNum());

        }

        Set<Auth> authSet = space.getAuthSet();
        for (Auth auth : authSet) {
            AuthObjectType authObjectType = auth.getAuthObjectType();
            if (AuthObjectType.EMPLOYEE.equals(authObjectType)) {

                List<UserDTO> userDTOList = indicatorUserService.listUserByDeptId(authObjectType, auth.getEmployeeCode());
                if (!CollectionUtils.isEmpty(userDTOList)) {
                    UserDTO dto = userDTOList.get(0);
                    auth.setAvatar(dto.getAvatar());
                }

            } else {
                DepartmentDTO dto = indicatorUserService.getDepartmentByCode(auth.getEmployeeCode(),authObjectType);
                auth.setUserNum(dto.getUserNum());
            }
        }

        Set<SpaceOwner> spaceOwnerSet = space.getSpaceOwnerSet();
        for (SpaceOwner spaceOwner : spaceOwnerSet) {
            AuthObjectType authObjectType = spaceOwner.getAuthObjectType();
            if (AuthObjectType.EMPLOYEE.equals(authObjectType)) {

                List<UserDTO> userDTOList = indicatorUserService.listUserByDeptId(authObjectType, spaceOwner.getEmployeeCode());
                if (!CollectionUtils.isEmpty(userDTOList)) {
                    UserDTO dto = userDTOList.get(0);
                    spaceOwner.setAvatar(dto.getAvatar());
                }

            } else {
                DepartmentDTO dto = indicatorUserService.getDepartmentByCode(spaceOwner.getEmployeeCode(),authObjectType);
                spaceOwner.setUserNum(dto.getUserNum());
            }
        }

        Set<SpaceAdmin> spaceAdminSet = space.getSpaceAdminSet();
        for (SpaceAdmin spaceAdmin : spaceAdminSet) {
            AuthObjectType authObjectType = spaceAdmin.getAuthObjectType();
            if (AuthObjectType.EMPLOYEE.equals(authObjectType)) {

                List<UserDTO> userDTOList = indicatorUserService.listUserByDeptId(authObjectType, spaceAdmin.getEmployeeCode());
                if (!CollectionUtils.isEmpty(userDTOList)) {
                    UserDTO dto = userDTOList.get(0);
                    spaceAdmin.setAvatar(dto.getAvatar());
                }

            } else {
                DepartmentDTO dto = indicatorUserService.getDepartmentByCode(spaceAdmin.getEmployeeCode(),authObjectType);
                spaceAdmin.setUserNum(dto.getUserNum());
            }
        }

        Set<SpaceEmployee> spaceEmployeeSet = space.getSpaceEmpSet();
        for (SpaceEmployee spaceEmployee : spaceEmployeeSet) {
            AuthObjectType authObjectType = spaceEmployee.getAuthObjectType();
            if (AuthObjectType.EMPLOYEE.equals(authObjectType)) {

                List<UserDTO> userDTOList = indicatorUserService.listUserByDeptId(authObjectType, spaceEmployee.getEmployeeCode());
                if (!CollectionUtils.isEmpty(userDTOList)) {
                    UserDTO dto = userDTOList.get(0);
                    spaceEmployee.setAvatar(dto.getAvatar());
                }

            } else {
                DepartmentDTO dto = indicatorUserService.getDepartmentByCode(spaceEmployee.getEmployeeCode(),spaceEmployee.getAuthObjectType());
                spaceEmployee.setUserNum(dto.getUserNum());
            }
        }

        return space;
    }

    @Override
    public List<OrganizationVO> get(Integer parentId, Integer orgType) {

        List<OrganizationVO> result = new ArrayList<>();
        if (Objects.equals(OrganizationType.ORG.getValue(), orgType)) {
            List<DepartmentVO> departments = departmentManager.listDepartmentByParentId(parentId);
            result.addAll(departments.stream().map(d -> {
                OrganizationVO o = new OrganizationVO();
                o.setAuthObjectType(AuthObjectType.ORG);
                o.setCode(d.getDepartmentId().toString());
                o.setUserNum(d.getUserNum());
                o.setName(d.getFullname());
                return o;
            }).collect(Collectors.toList()));

            List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery().eq(User::getDepartmentId, parentId));

            if (!CollectionUtils.isEmpty(users)) {
                result.addAll(users.stream().map(u -> {
                    OrganizationVO o = new OrganizationVO();
                    o.setAuthObjectType(AuthObjectType.EMPLOYEE);
                    o.setAvatar(u.getAvatar());
                    o.setCode(u.getUsername());
                    o.setName(u.getNickname());
                    return o;
                }).collect(Collectors.toList()));
            }
        }

        return result;
    }

    @Override
    public RoleType getRoleType(Long spaceId, String employeId, RoleType defRoleType) {
        RoleType roleType = this.getRoleType(spaceId, employeId);
        if (null == roleType) {
            roleType = defRoleType;
        }
        return roleType;
    }

    private boolean isReplace(RoleType befor, RoleType after) {

        boolean isReplace = false;

        if (null == befor) {
            return true;
        }

        if (RoleType.ADMIN.equals(after)) {
            isReplace = true;
        } else if (RoleType.OWNER.equals(after) && !RoleType.ADMIN.equals(befor)) {
            isReplace = true;
        } else if (RoleType.ANALYST.equals(after)
                && !RoleType.ADMIN.equals(befor)
                    && !RoleType.OWNER.equals(befor)){
            isReplace = true;
        }

        return isReplace;

    }

    @Override
    public RoleType getRoleType(Long spaceId, String employeCode) {

        RoleType roleType = null;
        Space space = this.spaceDao.getById(spaceId);
        Set<SpaceEmployee> spaceEmployeeSet = space.getSpaceEmpSet();

        Set<SpaceAdmin> spaceAdminSet = space.getSpaceAdminSet();
        boolean isExist = false;
        for (SpaceAdmin spaceAdmin : spaceAdminSet) {

            String spaceAdminEmpCode = spaceAdmin.getEmployeeCode();

            if (spaceAdminEmpCode.equalsIgnoreCase(employeCode)) {
                roleType = RoleType.ADMIN;
                return roleType;
            }

            AuthObjectType authObjectType = spaceAdmin.getAuthObjectType();
            if (!AuthObjectType.EMPLOYEE.equals(authObjectType)) {
                if (indicatorUserService.belongDept(employeCode, spaceAdminEmpCode, authObjectType)) {
                    roleType = RoleType.ADMIN;
                    return roleType;
                }
            }

        }

        Set<SpaceOwner> spaceOwnerSet = space.getSpaceOwnerSet();
        for (SpaceOwner spaceOwner : spaceOwnerSet) {

            String spaceOwnerEmpCode = spaceOwner.getEmployeeCode();
            if (spaceOwnerEmpCode.equalsIgnoreCase(employeCode)) {
                roleType = RoleType.OWNER;
            }

            AuthObjectType authObjectType = spaceOwner.getAuthObjectType();
            if (!AuthObjectType.EMPLOYEE.equals(authObjectType)) {
                if (indicatorUserService.belongDept(employeCode, spaceOwnerEmpCode, authObjectType)) {
                    roleType = RoleType.OWNER;
                }
            }

        }

        for (SpaceEmployee spaceEmployee : spaceEmployeeSet) {

            String spaceEmpCode = spaceEmployee.getEmployeeCode();
            if (spaceEmpCode.equalsIgnoreCase(employeCode)) {

                //强一致单独给某人的授权优先执行。
                for (SpaceRole spaceRole : spaceEmployee.getSpaceRoleSet()) {
                    if (this.isReplace(roleType, spaceRole.getRoleType())) {
                        roleType = spaceRole.getRoleType();
                    }
                }

            } else {
                AuthObjectType authObjectType = spaceEmployee.getAuthObjectType();
                if (!AuthObjectType.EMPLOYEE.equals(authObjectType)) {
                    Set<SpaceBlacklist> spaceBlacklistSet = space.getSpaceBlacklistSet();
                    Boolean inBlack = this.inBlackList(spaceBlacklistSet, employeCode);

                    if (!inBlack && indicatorUserService.belongDept(employeCode, spaceEmpCode, authObjectType)) {
                        for (SpaceRole spaceRole : spaceEmployee.getSpaceRoleSet()) {
                            if (this.isReplace(roleType, spaceRole.getRoleType())) {
                                roleType = spaceRole.getRoleType();
                            }
                        }
                    }
                }
            }

        }

        return roleType;
    }

    private boolean inBlackList(Set<SpaceBlacklist> spaceBlacklistSet, String empCode) {

        boolean has = false;
        if (!CollectionUtils.isEmpty(spaceBlacklistSet) && StringUtil.isNotEmpty(empCode)) {
            for (SpaceBlacklist spaceBlacklist : spaceBlacklistSet) {
                if (empCode.equalsIgnoreCase(spaceBlacklist.getEmployeeCode())) {
                    has = true;
                    break;
                }
            }
        }

        return has;

    }

    @Override
    public Long save(Space spaceParam) {

        Space space = this.spaceDao.getById(spaceParam.getId());

        //人员to管理员，并设置空间与用户、管理员的关联关系。
        space = this.applyEmpToAdmin(spaceParam, space);

        Space newSpace = spaceDao.save(space);

        return newSpace.getId();

    }


    /**
     * 将空间管理授予所辖人员中
     * @param spaceParam
     * @param space
     * @return
     */
    private Space applyEmpToAdmin(Space spaceParam, Space space) {

        //空间管理员
        Set<SpaceAdmin> spaceAdminSet = space.getSpaceAdminSet();

        Set<SpaceEmployee> allSpaceEmployeeSet = space.getSpaceEmpSet();

        //页面上传的人员信息
        Set<SpaceEmployee> pageSpaceEmployeeSet = spaceParam.getSpaceEmpSet();

        if (!CollectionUtils.isEmpty(pageSpaceEmployeeSet)) {

            //页面传入信息
            for (SpaceEmployee spaceEmployee : pageSpaceEmployeeSet) {

                //新增人员先移除黑名单
                space = this.removeBlacklist(spaceEmployee.getEmployeeCode(), space);

                boolean isRoleAdmin = false;
                //空间职员含有的角色类型
                Set<SpaceRole> spaceRoleSet = spaceParam.getSpaceRoleSet();
                for (SpaceRole spaceRole : spaceRoleSet) {

                    RoleType roleType = spaceRole.getRoleType();
                    if (RoleType.ADMIN.equals(roleType)) {
                        isRoleAdmin = true;
                    }

                    //需要重新创建对象否则jpa会保留到最后一个。
                    SpaceRole copySpaceRole = new SpaceRole();
                    copySpaceRole.setRoleType(roleType);
                    //设置两边到关联关系
                    copySpaceRole.setSpaceEmployee(spaceEmployee);
                    spaceEmployee.getSpaceRoleSet().clear();
                    spaceEmployee.getSpaceRoleSet().add(copySpaceRole);
                    spaceEmployee.initUpdate();

                }

                //是管理员
                if (isRoleAdmin) {

                    //现有管理员中是否含有
                    boolean hasAdmin = false;
                    for (SpaceAdmin spaceAdmin : spaceAdminSet) {

                        String empCode = spaceEmployee.getEmployeeCode();
                        String adminCode = spaceAdmin.getEmployeeCode();

                        //职员中已经含有管理员
                        if (empCode.equalsIgnoreCase(adminCode)) {

                            hasAdmin = true;
                            break;

                        }
                    }

                    AuthObjectType authObjectType = spaceEmployee.getAuthObjectType();

                    //如果不含有管理员信息,并且页面元素类型为职员的则添加。
                    if (!hasAdmin) {

                        SpaceAdmin spaceAdmin = new SpaceAdmin();
                        spaceAdmin.setEmployeeCode(spaceEmployee.getEmployeeCode());
                        spaceAdmin.setName(spaceEmployee.getName());
                        spaceAdmin.setMail(spaceEmployee.getMail());
                        spaceAdmin.setSpace(space);
                        spaceAdmin.setAuthObjectType(authObjectType);

                        spaceAdminSet.add(spaceAdmin);

                    }

                } else {

                    //现有管理员中是否含有
                    SpaceAdmin delSpaceAdmin = null;
                    for (SpaceAdmin spaceAdmin : spaceAdminSet) {

                        String empCode = spaceEmployee.getEmployeeCode();
                        String adminCode = spaceAdmin.getEmployeeCode();

                        //职员中已经含有管理员
                        if (empCode.equalsIgnoreCase(adminCode)) {

                            delSpaceAdmin = spaceAdmin;
                            break;

                        }
                    }

                    //原管理员已经移除关系
                    spaceAdminSet.remove(delSpaceAdmin);

                }

                //增加到所有人员中。
                if (!allSpaceEmployeeSet.contains(spaceEmployee)) {
                    allSpaceEmployeeSet.add(spaceEmployee);
                } else {
                    for (SpaceEmployee employee : allSpaceEmployeeSet) {
                        if (employee.getEmployeeCode().equalsIgnoreCase(spaceEmployee.getEmployeeCode())) {
                            employee.getSpaceRoleSet().clear();
                            for (SpaceRole spaceRole : spaceEmployee.getSpaceRoleSet()) {
                                spaceRole.setSpaceEmployee(employee);
                            }
                            employee.getSpaceRoleSet().addAll(spaceEmployee.getSpaceRoleSet());
                            employee.initUpdate();
                            this.spaceEmployeeDao.save(employee);
                        }
                    }
                }

            }

            //设置管理员、空间人员关联关系
//            space.getSpaceAdminSet().addAll(spaceAdminSet);
//            space.getSpaceEmpSet().addAll(allSpaceEmployeeSet);

        }

        return space;

    }

    @Override
    public SpaceEmployee get(Long spaceId, String employeeCode, Long spaceEmpId) {

        SpaceEmployee paramSpaceEmployee = spaceEmployeeDao.getById(spaceEmpId);
        AuthObjectType aot = paramSpaceEmployee.getAuthObjectType();
        SpaceEmployee spaceEmployee = new SpaceEmployee();
        BeanUtils.copyProperties(paramSpaceEmployee, spaceEmployee);
        spaceEmployee.setSpaceEmpId(paramSpaceEmployee.getId());
        spaceEmployee.setMail("");
        spaceEmployee.setAvatar("");

        if (Objects.equals(aot, AuthObjectType.ORG)) {
            Department department = departmentMapper.selectById(employeeCode);
            if (department != null) {
                spaceEmployee.setDepartmentNamePath(department.getNamePath());
            }
        } else if (Objects.equals(aot, AuthObjectType.EMPLOYEE)) {
            User user = userManager.getUserByName(employeeCode);
            if (user != null) {
                spaceEmployee.setMail(user.getEmail());
                spaceEmployee.setDepartmentNamePath(user.getDepartmentNamePath());
                spaceEmployee.setAvatar(user.getAvatar());
            }
        } else if (Objects.equals(aot, AuthObjectType.OPERATE)) {
            Organization organization = organizationService.getOne(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgCode, employeeCode));
            if (organization != null) {
                spaceEmployee.setDepartmentNamePath(organization.getOrgName());
            }
        } else if (Objects.equals(aot, AuthObjectType.POST)) {
            spaceEmployee.setDepartmentNamePath("-");
            spaceEmployee.setMail("-");
        }

        return spaceEmployee;
    }

    /**
     * 移除职员
     * @param spaceEmpSet
     * @param employeeCode
     * @return
     */
    private Set<SpaceEmployee> delEmp(Set<SpaceEmployee> spaceEmpSet, String employeeCode) {

        SpaceEmployee del = null;
        for (SpaceEmployee spaceEmployee : spaceEmpSet) {

            if (spaceEmployee.getEmployeeCode().equalsIgnoreCase(employeeCode)) {
                del = spaceEmployee;
                break;
            }

        }

        spaceEmpSet.remove(del);

        return spaceEmpSet;
    }

    /**
     * 移除管理员
     * @param spaceAdminSet
     * @param employeeCode
     * @return
     */
    private Set<SpaceAdmin> delAdmin(Set<SpaceAdmin> spaceAdminSet, String employeeCode) {

        SpaceAdmin del = null;
        for (SpaceAdmin spaceAdmin : spaceAdminSet) {

            if (spaceAdmin.getEmployeeCode().equalsIgnoreCase(employeeCode)) {
                del = spaceAdmin;
                break;
            }

        }

        spaceAdminSet.remove(del);

        return spaceAdminSet;
    }

    /**
     * 移除auth
     * @param authSet
     * @param employeeCode
     * @return
     */
    private Set<Auth> delSpaceAuth(Set<Auth> authSet, String employeeCode) {

        Auth del = null;
        for (Auth auth : authSet) {

            if (auth.getEmployeeCode().equalsIgnoreCase(employeeCode)) {
                del = auth;
                break;
            }

        }

        authSet.remove(del);

        return authSet;
    }

    /**
     * 移除owner
     * @param spaceOwnerSet
     * @param employeeCode
     * @return
     */
    private Set<SpaceOwner> delSpaceOwner(Set<SpaceOwner> spaceOwnerSet, String employeeCode) {

        SpaceOwner del = null;
        for (SpaceOwner spaceOwner : spaceOwnerSet) {

            if (spaceOwner.getEmployeeCode().equalsIgnoreCase(employeeCode)) {
                del = spaceOwner;
                break;
            }

        }

        spaceOwnerSet.remove(del);

        return spaceOwnerSet;
    }

    @Override
    public boolean delete(Long id, String employeeCode) {

        try {

            Space space = this.spaceDao.getById(id);

            //空间人员删除
            Set<SpaceEmployee> spaceEmpSet = space.getSpaceEmpSet();
            spaceEmpSet = this.delEmp(spaceEmpSet, employeeCode);
            space.setSpaceEmpSet(spaceEmpSet);

            //空间管理员删除
            Set<SpaceAdmin> spaceAdminSet = space.getSpaceAdminSet();
            spaceAdminSet = this.delAdmin(spaceAdminSet, employeeCode);
            space.setSpaceAdminSet(spaceAdminSet);

            //空间拥有者删除
            Set<SpaceOwner> spaceOwnerSet = space.getSpaceOwnerSet();
            spaceOwnerSet = this.delSpaceOwner(spaceOwnerSet, employeeCode);
            space.setSpaceOwnerSet(spaceOwnerSet);

            //空间删除授权用户
            Set<Auth> authSet = space.getAuthSet();
            authSet = this.delSpaceAuth(authSet, employeeCode);
            space.setAuthSet(authSet);

            //空间黑名单删除
//            SpaceBlacklist spaceBlacklist = new SpaceBlacklist();
//            spaceBlacklist.setEmployeeCode(employeeCode);
//            space.getSpaceBlacklistSet().add(spaceBlacklist);

            this.spaceDao.save(space);

        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }

        return true;
    }

    @Override
    public Space removeBlacklist(String employeeCode, Space space) {

        //空间黑名单删除
        Set<SpaceBlacklist> spaceBlacklistSet = space.getSpaceBlacklistSet();
        SpaceBlacklist del = null;
        for (SpaceBlacklist spaceBlacklist : spaceBlacklistSet) {
            if (spaceBlacklist.getEmployeeCode().equalsIgnoreCase(employeeCode)) {
                del = spaceBlacklist;
                break;
            }
        }

        space.getSpaceBlacklistSet().remove(del);

        return space;

    }

    private boolean isExistResultList(String code, List<SpaceEmployee> employeeList) {

        for (SpaceEmployee spaceEmployee : employeeList) {
            if (code.equalsIgnoreCase(spaceEmployee.getEmployeeCode())) {
                return true;
            }
        }

        return false;

    }

    private boolean isExistBlackList(String code, Space space) {


        Set<SpaceBlacklist> spaceBlacklistSet = space.getSpaceBlacklistSet();
        for (SpaceBlacklist spaceBlacklist : spaceBlacklistSet) {
            if (code.equalsIgnoreCase(spaceBlacklist.getEmployeeCode())) {
                return true;
            }
        }

        return false;

    }

    @Override
    public Page list(SearchText searchText) {

        Long spaceId = searchText.getSpaceId();
        Integer pageNo = searchText.getPageNo();
        Integer pageSize = searchText.getPageSize();

        String hql = this.buildBaseSql(searchText);
        Query query = this.entityManager.createQuery("select distinct s " + hql);
//        query.setFirstResult(1);
//        query.setMaxResults(200000);
//        Space space = this.spaceDao.getById(searchText.getSpaceId());
        //当前页数据
        List<SpaceEmployee> list = query.getResultList();
        List<SpaceEmployee> employeeList = new LinkedList<>();
        List<String> codes = list.stream().map(SpaceEmployee::getEmployeeCode).collect(Collectors.toList());

        List<Department> departments = departmentMapper.selectList(null);
        Map<String, Department> departmentMap = departments.stream().collect(Collectors.toMap(a->String.valueOf(a.getDepartmentId()), a -> a));

        List<User> users = userManager.listUserByUsernames(codes);
        Map<String, User> userMap = users.stream().collect(Collectors.toMap(User::getUsername, a -> a));

        List<Organization> organizations = organizationService.list(null);
        Map<String, Organization> organizationMap = organizations.stream().filter(a -> a.getOrgType() == 1).collect(Collectors.toMap(Organization::getOrgCode, a -> a));

        for (SpaceEmployee paramSpaceEmployee : list) {
            AuthObjectType aot = paramSpaceEmployee.getAuthObjectType();
            String employeeCode = paramSpaceEmployee.getEmployeeCode();
            SpaceEmployee spaceEmployee = new SpaceEmployee();
            BeanUtils.copyProperties(paramSpaceEmployee, spaceEmployee);
            spaceEmployee.setSpaceEmpId(paramSpaceEmployee.getId());
            spaceEmployee.setMail("");
            spaceEmployee.setAvatar("");

            if (Objects.equals(aot, AuthObjectType.ORG)) {
//                Department department = departmentMapper.selectById(employeeCode);
                Department department = departmentMap.getOrDefault(employeeCode, null);
                if (department != null) {
                    spaceEmployee.setDepartmentNamePath(department.getNamePath());
                    spaceEmployee.setMail("-");

                } else {
                    spaceEmployee = null;
                }
            } else if (Objects.equals(aot, AuthObjectType.EMPLOYEE)) {
//                User user = userManager.getUserByName(employeeCode);
                User user = userMap.getOrDefault(employeeCode, null);
                if (user != null) {
                    spaceEmployee.setMail(user.getEmail());
                    spaceEmployee.setDepartmentNamePath(StringUtils.isBlank(user.getDepartmentNamePath()) ? "-" : user.getDepartmentNamePath());
                    spaceEmployee.setAvatar(user.getAvatar());
                } else {
                    spaceEmployee = null;
                }

            } else if (Objects.equals(aot, AuthObjectType.OPERATE)) {
                // 查组运营架构下部门的所有员工
//                Organization organization = organizationService.getOne(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgCode, employeeCode));
                Organization organization = organizationMap.getOrDefault(employeeCode, null);
                if (organization != null) {
                    spaceEmployee.setDepartmentNamePath(organization.getOrgName());
                    spaceEmployee.setMail("-");
                } else {
                    spaceEmployee = null;
                }
            } else if (Objects.equals(aot, AuthObjectType.POST)) {
//                Post post = postManager.getPostByCode(employeeCode);
                spaceEmployee.setDepartmentNamePath("-");
                spaceEmployee.setMail("-");
            }
            if (spaceEmployee != null) {
                employeeList.add(spaceEmployee);
            }
        }

//        //分页总数
//        String cntHql = this.buildCountSql(hql);
//        Query cntQuery = this.entityManager.createQuery(cntHql);
//        Long cnt = (Long)cntQuery.getSingleResult();

        //分页信息
        Page page = new Page();

        List<SpaceEmployee> searchList = this.searchList(employeeList, searchText, searchText.getSpaceId());
        Long cnt = Long.valueOf(searchList.size());

        PageInfo pageInfo = new PageInfo(pageSize);
        pageInfo.setTotalRows(cnt.intValue());
        pageInfo.calc();
        pageInfo.calcRange(pageNo);
        page.setPageInfo(pageInfo);

        List<SpaceEmployee> pageList = this.pageList(searchList, pageInfo, spaceId);

        page.setContent(pageList);

        return page;

    }

    private boolean isSearch(SpaceEmployee self, String text) {

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

        return isEmpName || isDeptName || isEmail || isCreate || isRole;

    }


    private List<SpaceEmployee> searchList(List<SpaceEmployee> employeeList, SearchText searchText, Long spaceId) {

        Set<SpaceEmployee> searchSet = new LinkedHashSet<>();
        String text = searchText.getText();

        for (int i = 0; i < employeeList.size(); i++) {
            SpaceEmployee employee = employeeList.get(i);
            if (StringUtils.isNotBlank(text)) {
                if (this.isSearch(employee, text)) {
                    searchSet.add(employee);
                }
            } else {
                searchSet.add(employee);

            }
        }

        return new LinkedList<>(searchSet);

    }

    private List<SpaceEmployee> pageList(List<SpaceEmployee> employeeList, PageInfo pageInfo, Long spaceId) {

        List<SpaceEmployee> pageList = new LinkedList<>();
        Integer start = pageInfo.getPageStartRow();
        Integer end = pageInfo.getPageEndRow();

        for (int i = 0; i < employeeList.size(); i++) {

            if (i >= start && i < end) {

                SpaceEmployee employee = employeeList.get(i);

                RoleType roleType = spaceEmployeeService.getRoleType(spaceId, employee.getEmployeeCode(), RoleType.VISITOR);

                employee.setRoleType(roleType);
                SpaceRole spaceRole = new SpaceRole();
                spaceRole.setRoleType(roleType);

                employee.getSpaceRoleSet().clear();
                employee.getSpaceRoleSet().add(spaceRole);

                pageList.add(employee);
            }

        }

        return pageList;

    }


    /**
     * Build Sql
     * @param searchText
     * @return
     */
    private String buildBaseSql(final SearchText searchText) {

//        String text = searchText.getText();

//        String enters = ESAPI.encoder().encodeForSQL(new MySQLCodec(MySQLCodec.Mode.ANSI), text);
        String hql = "From SpaceEmployee as s left join s.space as space"
                + " where 1=1 ";

//        if (!StringUtil.isEmpty(text)) {
//
//            hql += " and (s.name like '%" + enters + "%'"
//                    + " or s.creator like '%" + enters + "%'"
//                    + " or s.updater like '%" + enters + "%'"
//
//                    + " or s.name like '%" + enters + "%'"
//                    + " or s.creator like '%" + enters + "%'"
//                    + " or s.updater like '%" + enters + "%')";
//
//        }

        Long spaceId = searchText.getSpaceId();
        hql += " and space.id='" + spaceId + "'";


        boolean isMine = searchText.isMine();

        if (isMine) {

            String userName = UserThreadLocalUtil.getUserName();
            hql +=  " and (s.creator = '" + userName + "'"
                    + " or s.creator = '" + userName + "')";

        }

        hql += " order by s.updateDate desc";

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

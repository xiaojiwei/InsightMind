package com.graphinsight.indicator.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.base.Joiner;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.Employee;
import com.graphinsight.indicator.auto.entity.Organization;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.DepartmentMapper;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.auto.service.IEmployeeService;
import com.graphinsight.indicator.auto.service.IOrganizationService;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.OrganizationType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.manager.*;
import com.graphinsight.indicator.model.dto.DepartmentDTO;
import com.graphinsight.indicator.model.dto.OperateGrantValue;
import com.graphinsight.indicator.model.dto.UserContext;
import com.graphinsight.indicator.model.dto.UserDTO;
import com.graphinsight.indicator.model.post.Post;
import com.graphinsight.indicator.model.post.PostEmp;
import com.graphinsight.indicator.service.IndicatorUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/5/17
 * Desc:
 */
@Service
@DS("mysql")
@Slf4j
public class IndicatorUserServiceImpl implements IndicatorUserService {

    @Autowired
    UserManager userManager;
    @Autowired
    UserMapper userMapper;
    @Autowired
    DepartmentManager departmentManager;
    @Autowired
    DepartmentMapper departmentMapper;
    @Autowired
    OrganizationManager organizationManager;
    @Autowired
    IOrganizationService organizationService;
    @Autowired
    IEmployeeService employeeService;
    @Autowired
    UserGrantContextManager userGrantContextManager;
    @Autowired
    private PostManager postManager;

    @Override
    public OperateGrantValue getOperateGrantValue(String username, Long operateGrantConfigId) {
        return userGrantContextManager.getOperateGrantValue(username,operateGrantConfigId);
    }

    @Override
    public UserContext getUserContext(Long spaceId, String username) {
        UserContext userContext = userManager.getUserContext(spaceId, username);
        return userContext;
    }

    @Override
    public List<UserDTO> listUserByDeptId(AuthObjectType authObjectType, String identCode) {
        if (Objects.equals(authObjectType,AuthObjectType.ORG)){
            // 查组飞书架构下部门的所有员工
            List<User> users = userManager.listUserByDeptId(Long.valueOf(identCode));
            List<Department> departments = departmentMapper.selectList(null);
            Map<Integer, Department> departmentMap = departments.stream().collect(Collectors.toMap(Department::getDepartmentId, d -> d));
            return users.stream().map(user -> convert(user,departmentMap)).collect(Collectors.toList());
        } else if (Objects.equals(authObjectType,AuthObjectType.EMPLOYEE)){
            // 查具体员工
            String username = identCode;
            List<Department> departments = departmentMapper.selectList(null);
            Map<Integer, Department> departmentMap = departments.stream().collect(Collectors.toMap(Department::getDepartmentId, d -> d));
            List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
            return users.stream().map(user -> convert(user,departmentMap)).collect(Collectors.toList());
        } else if (Objects.equals(authObjectType,AuthObjectType.OPERATE)) {
            // 查组运营架构下部门的所有员工
            List<Employee> employees = organizationManager.listAllEmpByOrgCode(identCode, OrganizationType.OPERATE.getValue());
            List<Organization> organizations = organizationService.list(null);
            Map<String, Organization> organizationMap = organizations.stream().filter(a->a.getOrgType() == 1).collect(Collectors.toMap(Organization::getOrgCode, o -> o));
            return employees.stream().map(e -> convert(e,organizationMap)).collect(Collectors.toList());
        } else if (Objects.equals(authObjectType,AuthObjectType.POST)) {
            List<PostEmp> postEmps = this.postManager.listPostEmpInfo(identCode);
            List<Department> departments = departmentMapper.selectList(null);
            Map<Integer, Department> departmentMap = departments.stream().collect(Collectors.toMap(Department::getDepartmentId, d -> d));
            List<UserDTO> users = postEmps.stream().map(a -> convert(a, departmentMap)).collect(Collectors.toList());
            return users;
        } else {
            throw IndicatorParamNotValidException.error("authObjectType 不合法");
        }
    }

    private UserDTO convert(PostEmp user, Map<Integer, Department> departmentMap) {
        UserDTO userDTO = UserDTO.builder().username(user.getEmployeeName()).email(user.getEmployeeEmail()).jobNumber(user.getEmployeeNo()).build();

        Department department = departmentMap.get(user.getDeptId());
        if (department != null){
            DepartmentDTO departmentDTO = new DepartmentDTO();
            BeanUtils.copyProperties(department,departmentDTO);
            userDTO.setDepartment(departmentDTO);
        }
        return userDTO;

    }


    private UserDTO convert(User user,Map<Integer, Department> departmentMap){
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        Department department = departmentMap.get(user.getDepartmentId());
        if (department != null){
            DepartmentDTO departmentDTO = new DepartmentDTO();
            BeanUtils.copyProperties(department,departmentDTO);
            userDTO.setDepartment(departmentDTO);
        }
        return userDTO;
    }

    private UserDTO convert(Employee employee,Map<String, Organization> organizationMap){
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(employee,userDTO);
        Organization org = organizationMap.get(employee.getOrgCode());
        if (org != null){
            DepartmentDTO departmentDTO = new DepartmentDTO();
            LinkedList<Organization> organizations = organizationManager.listSuperiorOrg(org.getOrgCode(), OrganizationType.OPERATE);
            if (! CollectionUtils.isEmpty(organizations)){
                List<String> superiorOrgNames = organizations.stream().map(Organization::getOrgName).collect(Collectors.toList());
                Joiner joiner = Joiner.on("-");
                String namePath = joiner.join(superiorOrgNames);
                userDTO.setDepartmentNamePath(namePath);
            }
            departmentDTO.setCode(org.getOrgCode());
            departmentDTO.setFullname(org.getOrgName());
            userDTO.setDepartment(departmentDTO);
        }
        return userDTO;
    }

    @Override
    public DepartmentDTO getDepartmentByCode(String deptCode, AuthObjectType authObjectType) {
        DepartmentDTO result = new DepartmentDTO();
        if (Objects.equals(AuthObjectType.ORG,authObjectType)){
            Department department = departmentMapper.selectOne(Wrappers.<Department>lambdaQuery().eq(Department::getDepartmentId, deptCode));
            if (department == null){
                return result;
                // throw IndicatorParamNotValidException.error("部门不存在,deptCode:" + deptCode);
            }
            BeanUtils.copyProperties(department,result);
            return result;
        } else if(Objects.equals(AuthObjectType.OPERATE,authObjectType)){
            Organization organization = organizationService.getOne(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgCode, deptCode));
            if (organization == null){
                throw IndicatorParamNotValidException.error("部门不存在,deptCode:" + deptCode);
            }
            result.setFullname(organization.getOrgName());
            result.setCode(organization.getOrgCode());
            return result;
        } else if (Objects.equals(AuthObjectType.POST,authObjectType)) {
            Post postByCode = this.postManager.getPostByCode(deptCode);
            if (postByCode == null) {
                return result;
            }
            result.setFullname(postByCode.getPostName());
            result.setCode(postByCode.getPostName());
            return result;
        } else {
            throw IndicatorParamNotValidException.error("部门类型不合法,authObjectType:" + authObjectType);
        }
    }

    @Override
    public boolean belongDept(String username, String targetCode, AuthObjectType authObjectType) {
        if (targetCode == null){
            return false;
        }
        if (Objects.equals(AuthObjectType.ORG,authObjectType)){
            List<Department> departments = departmentManager.listAllDepartmentByParentId(Long.valueOf(targetCode));
            Set<String> deptIds = departments.stream().map(d -> d.getDepartmentId().toString()).collect(Collectors.toSet());
            User user = userManager.getUserByName(username);
            return user != null && null != user.getDepartmentId() && !CollectionUtils.isEmpty(deptIds) && deptIds.contains(user.getDepartmentId().toString());
        } else if(Objects.equals(AuthObjectType.OPERATE,authObjectType)){
            List<Organization> organizations = organizationManager.listAllOrgsByParentCode(targetCode,OrganizationType.OPERATE.getValue());
            Set<String> orgsCodes = organizations.stream().map(Organization::getOrgCode).collect(Collectors.toSet());
            List<Employee> employees = employeeService.list(Wrappers.<Employee>lambdaQuery().eq(Employee::getUsername, username));
            if (CollectionUtils.isEmpty(employees)){
                return false;
            }
            for (Employee employee : employees) {
                if (orgsCodes.contains(employee.getOrgCode())){
                    return true;
                }
            }
            return false;
        } else if (Objects.equals(AuthObjectType.POST,authObjectType)) {
            User user = userManager.getUserByName(username);
            if (user == null) {
                return false;
            }
            PostEmp postEmp = this.postManager.getPostInfo(user.getJobNumber());
            if (postEmp == null) {
                return false;
            }
            return targetCode.equals(postEmp.getPostCode());
        } else {
            throw IndicatorParamNotValidException.error("部门类型不合法,authObjectType:" + authObjectType);
        }
    }
}

package com.graphinsight.indicator.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.graphinsight.indicator.auto.entity.Employee;
import com.graphinsight.indicator.auto.entity.OperateGrantConfig;
import com.graphinsight.indicator.auto.entity.Organization;
import com.graphinsight.indicator.auto.service.IEmployeeService;
import com.graphinsight.indicator.auto.service.IOrganizationService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.EmployeeOrgType;
import com.graphinsight.indicator.enums.EmployeeType;
import com.graphinsight.indicator.enums.OrganizationType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.model.vo.EmployeeVO;
import com.graphinsight.indicator.model.vo.IndicatorOperateTree;
import com.graphinsight.indicator.model.vo.OrganizationTree;
import com.graphinsight.indicator.model.vo.OrganizationVO;
import com.graphinsight.indicator.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/5/25
 * Desc:
 */
@Slf4j
@Service
public class OrganizationManager {

    @Autowired
    RedisCacheService redisCacheService;

    @Autowired
    IOrganizationService organizationService;
    @Autowired
    IEmployeeService employeeService;

    public EmployeeVO getByCode(String code) {
        List<Employee> employees = employeeService.list(Wrappers.<Employee>lambdaQuery()
                .eq(Employee::getUsername, code));
        if (CollectionUtils.isEmpty(employees)) {
            return null;
        }
        List<Organization> organizations = organizationService.list(null);
        EmployeeVO employeeVO = convertEmployee(employees.get(0), organizations);
        return employeeVO;
    }

    public List<EmployeeVO> searchEmployee(String searchText) {
        List<Employee> employees = employeeService.list(Wrappers.<Employee>lambdaQuery()
                .in(Employee::getOrgType, Arrays.asList(OrganizationType.ORG.getValue(), OrganizationType.OPERATE.getValue()))
                .eq(Employee::getAvailable, YesNoType.YES.getCode())
                .and(w -> w.like(Employee::getUsername, searchText)
                        .or()
                        .like(Employee::getNickname, searchText))
        );
        if (CollectionUtils.isEmpty(employees)) {
            return Collections.EMPTY_LIST;
        }
        List<Organization> organizations = organizationService.list(null);
        List<EmployeeVO> vos = employees.stream().map(e -> convertEmployee(e, organizations)).filter(vo -> StringUtils.hasLength(vo.getNamePath())).collect(Collectors.toList());
        List<EmployeeVO> result = new ArrayList<>();
        // 根据唯一标识去重
        Map<String, List<EmployeeVO>> map = vos.stream().collect(Collectors.groupingBy(EmployeeVO::getCode));
        map.forEach((code, list) -> {
            result.add(list.get(0));
        });
        return result;
    }


    public List<EmployeeVO> listEmployeeByOrgCode(String orgCode, OrganizationType organizationType) {
        List<Organization> organizations = organizationService.list(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgType, organizationType.getValue()));
        Organization rootOrg = organizations.stream().filter(o -> Objects.equals(o.getOrgCode(), orgCode)).findFirst().orElse(null);
        Set<String> children = new HashSet<>();
        if (rootOrg != null) {
            findChildren(rootOrg, organizations, children);
        }
        children.add(orgCode);
        List<Employee> employees = employeeService.list(Wrappers.<Employee>lambdaQuery().eq(Employee::getAvailable, YesNoType.YES.getCode()).in(Employee::getOrgCode, children));
        Map<String, Organization> organizationMap = organizations.stream().collect(Collectors.toMap(Organization::getOrgCode, o -> o));
        List<EmployeeVO> vos = employees.stream().map(e -> convertEmployee(e, organizationMap)).collect(Collectors.toList());
        return vos;
    }

    private EmployeeVO convertEmployee(Employee employee, List<Organization> organizations) {
        EmployeeVO vo = new EmployeeVO();
        vo.setAvatar(employee.getAvatar());
        vo.setCode(employee.getUsername());
        vo.setName(employee.getNickname());
        vo.setEmail(employee.getEmail());
        vo.setOrgCode(employee.getOrgCode());
        if (CollectionUtils.isNotEmpty(organizations)) {
            Organization target = organizations.stream().filter(o -> Objects.equals(o.getOrgCode(), employee.getOrgCode())).findFirst().orElse(null);
            if (target != null) {
                LinkedList<Organization> superiorOrg = listSuperiorOrg(target, organizations);
                if (CollectionUtils.isNotEmpty(superiorOrg)) {
                    if (!Objects.equals(EmployeeType.LIXIANG.getValue(), employee.getEmployeeType())) {
                        Organization first = superiorOrg.get(0);
                        first.setOrgName("运营架构");
                    }
                    String namePath = superiorOrg.stream().map(Organization::getOrgName).collect(Collectors.joining("-"));
                    vo.setNamePath(namePath);
                }
            }
        }
        vo.setEmployeeType(employee.getEmployeeType());
        return vo;
    }


    private EmployeeVO convertEmployee(Employee employee, Map<String, Organization> organizationMap) {
        EmployeeVO vo = new EmployeeVO();
        vo.setAvatar(employee.getAvatar());
        vo.setCode(employee.getUsername());
        vo.setName(employee.getNickname());
        Organization organization = organizationMap.get(employee.getOrgCode());
        OrganizationTree organizationTree = convertTree(organization);
        vo.setOrganization(organizationTree);
        vo.setEmployeeType(employee.getEmployeeType());
        return vo;
    }

    public void findChildren(Organization parent, List<Organization> all, Set<String> children) {
        children.add(parent.getOrgCode());
        List<Organization> organizations = all.stream().filter(tree -> Objects.equals(tree.getParentCode(), parent.getOrgCode())).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(organizations)) {
            organizations.forEach(o -> {
                children.add(o.getOrgCode());
                findChildren(o, all, children);
            });
        }

    }

    private static Cache<Object, Object> MEM_CACHE = CacheBuilder.newBuilder()
            .initialCapacity(10000)
            .concurrencyLevel(20)
            .expireAfterWrite(8, TimeUnit.HOURS)
            .build();


    public OrganizationTree getTree(OrganizationType organizationType) {

        String orgKey = "ORG_TYPE" + organizationType.getValue();
        Object rootObj = MEM_CACHE.getIfPresent(orgKey);
        OrganizationTree root = new OrganizationTree();
        if (null == rootObj) {

            List<Organization> organizations = organizationService.list(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgType, organizationType.getValue()));
            Organization rootOrg = organizations.stream().filter(o -> Objects.equals(o.getParentCode(), IndicatorConstant.OPERATE_LIXIANG_DEPT_ID)).findFirst().orElse(null);
            if (rootOrg == null) {
                return root;
            }
            root = convertTree(rootOrg, organizations);
            List<OrganizationTree> organizationTrees = organizations.stream().map(o -> convertTree(o, organizations)).collect(Collectors.toList());
            findChildren(root, organizationTrees);
            MEM_CACHE.put(orgKey, root);

        } else {
            root = (OrganizationTree)rootObj;
        }

        return root;
    }

    private void findChildren(OrganizationTree parent, List<OrganizationTree> organizationTrees) {
        List<OrganizationTree> children = organizationTrees.stream().filter(tree -> Objects.equals(tree.getParentCode(), parent.getCode())).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(children)) {
            parent.setChildren(children);
            children.forEach(c -> findChildren(c, organizationTrees));
        }

    }

    private OrganizationTree convertTree(Organization organization) {
        OrganizationTree tree = new OrganizationTree();
        tree.setCode(organization.getOrgCode());
        tree.setParentCode(organization.getParentCode());
        tree.setName(organization.getOrgName());
        return tree;
    }

    private OrganizationTree convertTree(Organization organization, List<Organization> all) {
        OrganizationTree tree = new OrganizationTree();
        LinkedList<Organization> organizations = listSuperiorOrg(organization, all);
        if (CollectionUtils.isNotEmpty(organizations)) {
            Organization o = organizations.get(0);
            o.setOrgName("运营架构");
            String namePath = organizations.stream().map(Organization::getOrgName).collect(Collectors.joining("-"));
            tree.setNamePath(namePath);
        }
        tree.setCode(organization.getOrgCode());
        tree.setParentCode(organization.getParentCode());
        tree.setName(organization.getOrgName());
        tree.setDeptType(organization.getDeptType());
        return tree;
    }

    /**
     * 获取某个部门下的员工数量
     *
     * @param orgCode
     * @param organizationType
     * @return
     */
    public Integer getUserNum(String orgCode, Integer organizationType) {
        List<Organization> organizations = listAllOrgsByParentCode(orgCode, organizationType);
        if (CollectionUtils.isEmpty(organizations)) {
            return 0;
        }
        Set<String> orgCodes = organizations.stream().map(Organization::getOrgCode).collect(Collectors.toSet());
        int count = employeeService.count(Wrappers.<Employee>lambdaQuery().in(Employee::getOrgCode, orgCodes).eq(Employee::getOrgType, organizationType));
        return count;
    }

    /**
     * 获取当前部门的所有上级部门(包含本身)
     *
     * @return
     */
    public LinkedList<Organization> listSuperiorOrg(Organization target, List<Organization> all) {
        LinkedList<Organization> result = new LinkedList<>();
        if (target == null) {
            return result;
        }
        result.add(target);
        findParent(all, result, target);
        Collections.reverse(result);
        return result;
    }


    /**
     * 获取当前部门的所有上级部门(包含本身)
     *
     * @param orgCode
     * @return
     */
    public LinkedList<Organization> listSuperiorOrg(String orgCode, OrganizationType organizationType) {

        List<Organization> organizations = organizationService.list(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgType, organizationType.getValue()));
        List<Organization> target = organizationService.list(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgCode, orgCode).eq(Organization::getOrgType, organizationType.getValue()));
        LinkedList<Organization> result = new LinkedList<>();
        if (org.springframework.util.CollectionUtils.isEmpty(target)) {
            return result;
        }
        result.add(target.get(0));
        findParent(organizations, result, target.get(0));
        Collections.reverse(result);
        return result;
    }

    private void findParent(List<Organization> allOrgs, LinkedList<Organization> superiorOrgs, Organization targetOrg) {
        Organization parent = allOrgs.stream().filter(o -> Objects.equals(o.getOrgCode(), targetOrg.getParentCode())).findFirst().orElse(null);
        if (Objects.nonNull(parent)) {
            superiorOrgs.add(parent);
            findParent(allOrgs, superiorOrgs, parent);
        }
    }

    public List<IndicatorOperateTree> getOperateOrgTree(String username, Integer orgType) {
        EmployeeOrgType employeeOrgType = EmployeeOrgType.findByInt(orgType).orElse(null);
        OrganizationType organizationType = EmployeeOrgType.getOrganizationType(employeeOrgType);
        List<Organization> organizations = organizationService
                .list(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgType, organizationType.getValue()));
        List<Employee> employeeList = employeeService.list(Wrappers.<Employee>lambdaQuery()
                .eq(Employee::getOrgType, orgType)
                .eq(Employee::getAvailable, YesNoType.YES.getCode())
                .eq(Employee::getOffduty, YesNoType.NO.getCode())
                .eq(Employee::getUsername, username));

        if (org.springframework.util.CollectionUtils.isEmpty(employeeList) || org.springframework.util.CollectionUtils.isEmpty(organizations)) {
            return Collections.EMPTY_LIST;
        }
        Set<String> topOrgCode = employeeList.stream().map(Employee::getOrgCode).collect(Collectors.toSet());
        List<Organization> topOrgs = organizations.stream().filter(org -> topOrgCode.contains(org.getOrgCode())).collect(Collectors.toList());
        if (org.springframework.util.CollectionUtils.isEmpty(topOrgs)) {
            return Collections.EMPTY_LIST;
        }
        List<IndicatorOperateTree> treeList = topOrgs.stream().map(o -> convert2Tree(o)).collect(Collectors.toList());
        List<IndicatorOperateTree> allTrees = organizations.stream().map(o -> convert2Tree(o)).collect(Collectors.toList());
        HashSet<String> set1 = new HashSet<>();
        treeList.forEach(t -> findChild(t, allTrees, set1));
        return treeList;
    }

    public IndicatorOperateTree getIndicatorOperateTree(Organization organization, OperateGrantConfig config) {
        EmployeeOrgType employeeOrgType = EmployeeOrgType.findByInt(config.getOrgType()).orElse(null);
        OrganizationType organizationType = EmployeeOrgType.getOrganizationType(employeeOrgType);
        List<Organization> organizations = organizationService
                .list(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgType, organizationType.getValue()));
        IndicatorOperateTree res = convert2Tree(organization);
        List<IndicatorOperateTree> allOrg = organizations.stream().map(e -> convert2Tree(e)).collect(Collectors.toList());
        findChild(res, allOrg, new HashSet<>());
        return res;
    }

    private IndicatorOperateTree convert2Tree(Organization organization) {
        IndicatorOperateTree tree = new IndicatorOperateTree();
        tree.setName(organization.getOrgName());
        tree.setParentCode(organization.getParentCode());
        tree.setCode(organization.getOrgCode());
        tree.setDeptType(organization.getDeptType());
        return tree;
    }

    private void findChild(IndicatorOperateTree target, List<IndicatorOperateTree> allTreeList, HashSet<String> set1) {
        List<IndicatorOperateTree> childList = allTreeList.stream().filter((c) -> Objects.equals(c.getParentCode(), target.getCode())).collect(Collectors.toList());
        childList.forEach(c -> {
                    if (!set1.contains(c.getCode())) {
                        set1.add(c.getCode());
                        findChild(c, allTreeList, set1);
                    } else {
                        log.info("数据充分；；；{}", c.getCode());
                    }
                }
        );
        if (!org.springframework.util.CollectionUtils.isEmpty(childList)) {
            target.setChildren(childList);
        }
    }

    /**
     * 获取部门下的所有员工，包括所有子部门和目标部门
     *
     * @param orgCode
     * @param orgType
     * @return
     */
    public List<Employee> listAllEmpByOrgCode(String orgCode, Integer orgType) {
        List<Organization> organizations = listAllOrgsByParentCode(orgCode, orgType);
        if (CollectionUtils.isEmpty(organizations)) {
            return Collections.EMPTY_LIST;
        }
        Set<String> orgCodes = organizations.stream().map(Organization::getOrgCode).collect(Collectors.toSet());
        List<Employee> employees = employeeService.list(Wrappers.<Employee>lambdaQuery().in(Employee::getOrgCode, orgCodes));
        return employees;
    }

    /**
     * 获取所有子部门(包含其本身)
     *
     * @param parentCode
     * @param orgType
     * @return
     */
    public List<Organization> listAllOrgsByParentCode(String parentCode, Integer orgType) {
        List<Organization> orgs = organizationService.list(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgType, orgType));
        return listAllChildren(orgs, parentCode);
    }

    private List<Organization> listAllChildren(List<Organization> orgs, String parentCode) {
        if (CollectionUtils.isEmpty(orgs)) {
            return Collections.emptyList();
        }
        Map<String, Organization> organizationMap = orgs.stream().collect(Collectors.toMap(Organization::getOrgCode, o -> o));
        List<Organization> result = new ArrayList<>();
        findChildren(parentCode, organizationMap, result);
        return result;
    }

    /**
     * 获取所有子部门(包含其本身)
     *
     * @param parentCode
     * @return
     */
    public List<Organization> listAllOrgsByParentCode(String parentCode) {
        List<Organization> orgs = organizationService.list(null);
        return listAllChildren(orgs, parentCode);
    }


    /**
     * 获取当前用户管理的组织
     *
     * @param username
     * @return
     */
    public List<Organization> listAllAuthOrganization(String username, EmployeeOrgType orgType) {
        if (orgType == null) {
            return Collections.emptyList();
        }
        List<Employee> employeeList = employeeService.list(Wrappers.<Employee>lambdaQuery()
                .eq(Employee::getOrgType, orgType.getValue())
                .eq(Employee::getAvailable, YesNoType.YES.getCode())
                .eq(Employee::getOffduty, YesNoType.NO.getCode())
                .eq(Employee::getUsername, username));
        if (CollectionUtils.isEmpty(employeeList)) {
            return Collections.emptyList();
        }
        Set<String> orgCodes = employeeList.stream().map(Employee::getOrgCode).collect(Collectors.toSet());
        OrganizationType organizationType = EmployeeOrgType.getOrganizationType(orgType);
        List<Organization> orgs = organizationService.list(Wrappers.<Organization>lambdaQuery().eq(Organization::getOrgType, organizationType.getValue()));
        if (CollectionUtils.isEmpty(orgs)) {
            return Collections.emptyList();
        }
        Map<String, Organization> organizationMap = orgs.stream().collect(Collectors.toMap(Organization::getOrgCode, o -> o));
        List<Organization> result = new ArrayList<>();
        orgCodes.forEach(code -> findChildren(code, organizationMap, result));
        return result;
    }

    /**
     * 查找某一个组织的所有子组织(包含其自身)
     *
     * @param code
     * @param organizationMap
     * @param children
     */
    private void findChildren(String code, Map<String, Organization> organizationMap, List<Organization> children) {
        Organization organization = organizationMap.get(code);
        if (Objects.nonNull(organization)) {
            children.add(organization);
            List<Organization> subOrgs = organizationMap.values().stream().filter(o -> Objects.equals(o.getParentCode(), code)).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(subOrgs)) {
                children.addAll(subOrgs);
                subOrgs.forEach(org -> findChildren(org.getOrgCode(), organizationMap, children));
            }
        }
    }

    /**
     * 获取部门下的子部门和员工
     *
     * @param parentCode
     * @return
     */
    public List<OrganizationVO> listOrgByParentId(String parentCode) {
        List<Organization> orgs;
        if (Objects.equals(parentCode, IndicatorConstant.TOP_DEPT_ID.toString())) {
            orgs = organizationService.list(Wrappers.<Organization>lambdaQuery()
                    .eq(Organization::getParentCode, IndicatorConstant.OPERATE_LIXIANG_DEPT_ID));
        } else {
            orgs = organizationService.list(Wrappers.<Organization>lambdaQuery()
                    .eq(Organization::getParentCode, parentCode));
        }
        List<OrganizationVO> orgList = orgs.stream().map(o -> convert(o)).collect(Collectors.toList());
        List<Employee> employeeList = employeeService.list(Wrappers.<Employee>lambdaQuery()
                .eq(Employee::getOrgCode, parentCode)
                .eq(Employee::getOrgType, EmployeeOrgType.OPERATE.getValue())
                .eq(Employee::getAvailable, YesNoType.YES.getCode()));
        if (!org.springframework.util.CollectionUtils.isEmpty(employeeList)) {
            List<OrganizationVO> emps = employeeList.stream().map(e -> convert(e)).collect(Collectors.toList());
            orgList.addAll(emps);
        }
        return orgList;
    }


    public List<OrganizationVO> searchOrganization(Integer orgType, String searchText, Integer pageSize) {
        List<OrganizationVO> result = new ArrayList<>();
        Page<Organization> orgPage = organizationService.page(new Page(0, pageSize), Wrappers.<Organization>lambdaQuery()
                .eq(Organization::getOrgType, orgType)
                .like(Organization::getOrgName, searchText));

        QueryWrapper<Employee> employeeWrapper = new QueryWrapper();
        Page<Employee> empPage = employeeService.page(new Page(0, pageSize), Wrappers.<Employee>lambdaQuery()
                .eq(Employee::getOrgType, orgType)
                .and(wrapper -> wrapper
                        .like(Employee::getOrgCode, searchText)
                        .or()
                        .like(Employee::getUsername, searchText)
                        .or()
                        .like(Employee::getJobNum, searchText)));
        List<OrganizationVO> orgList = orgPage.getRecords().stream().map(o -> convert(o)).collect(Collectors.toList());
        List<OrganizationVO> empList = empPage.getRecords().stream().map(o -> convert(o)).collect(Collectors.toList());

        result.addAll(orgList);
        result.addAll(empList);
        List<OrganizationVO> organizationVOS = result.subList(0, result.size() > pageSize ? pageSize : result.size());
        return organizationVOS;
    }

    /**
     * 同步部门下的员工数量
     */
    public void syncUserNum() {
        List<Organization> organizations = organizationService.list();
        Map<Integer, List<Organization>> orgMap = organizations.stream().collect(Collectors.groupingBy(Organization::getOrgType));
        orgMap.forEach((orgType, orgs) -> {
            orgs.forEach(o -> {
                List<Organization> orgsByParentCode = listAllOrgsByParentCode(o.getOrgCode(), orgType);
                Set<String> orgCodes = orgsByParentCode.stream().map(Organization::getOrgCode).collect(Collectors.toSet());
                if (!CollectionUtils.isEmpty(orgCodes)) {
                    QueryWrapper<Employee> queryWrapper = new QueryWrapper();
                    queryWrapper.in("org_code", orgCodes);
                    queryWrapper.eq("org_type", orgType);
                    Integer userNum = employeeService.count(queryWrapper);
                    o.setUserNum(userNum);
                    organizationService.updateById(o);
                }
            });
        });
    }

    private OrganizationVO convert(Organization organization) {
        OrganizationVO organizationVO = new OrganizationVO();
        organizationVO.setCode(organization.getOrgCode());
        organizationVO.setName(organization.getOrgName());
        organizationVO.setAuthObjectType(AuthObjectType.getTypeByOrgType(organization.getOrgType()));
        organizationVO.setUserNum(getUserNum(organization.getOrgCode(), organization.getOrgType()));
        return organizationVO;
    }

    private OrganizationVO convert(Employee employee) {
        OrganizationVO organizationVO = new OrganizationVO();
        organizationVO.setCode(employee.getUsername());
        organizationVO.setName(employee.getNickname());
        organizationVO.setAvatar(employee.getAvatar());
        organizationVO.setAuthObjectType(AuthObjectType.getTypeByOrgType(null));
        return organizationVO;
    }

}

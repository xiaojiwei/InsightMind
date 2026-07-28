package com.graphinsight.indicator.manager;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.DepartmentMapper;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.auto.service.IDepartmentService;
import com.graphinsight.indicator.auto.service.IUserService;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.model.vo.DepartmentTree;
import com.graphinsight.indicator.model.vo.DepartmentVO;
import com.graphinsight.indicator.model.vo.OrganizationVO;
import com.graphinsight.indicator.util.MemCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Date: 2022/3/4
 * Desc:
 */
@Slf4j
@Service
@DS("mysql")
public class DepartmentManager {

    @Autowired
    private COALoginManager coaLoginManager;

    @Autowired
    private IDepartmentService departmentService;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IUserService userService;

    public List<OrganizationVO> searchDepartment(String searchText, Integer pageSize){
        List<OrganizationVO> result = new ArrayList<>();
        Page<Department> orgPage = departmentService.page(new Page(0, pageSize), Wrappers.<Department>lambdaQuery()
                .like(Department::getFullname, searchText));

        Page<User> empPage = userService.page(new Page(0, pageSize), Wrappers.<User>lambdaQuery()
                .like(User::getUsername,searchText)
                .or()
                .like(User::getNickname,searchText)
                .or()
                .like(User::getJobNumber,searchText));
        List<OrganizationVO> orgList = orgPage.getRecords().stream().map(o -> convert(o)).collect(Collectors.toList());
        List<OrganizationVO> empList = empPage.getRecords().stream().map(o -> convert(o)).collect(Collectors.toList());

        result.addAll(orgList);
        result.addAll(empList);
        List<OrganizationVO> organizationVOS = result.subList(0, result.size() > pageSize ? pageSize : result.size());
        return organizationVOS;
    }

    private OrganizationVO convert(Department department){
        OrganizationVO organizationVO = new OrganizationVO();
        organizationVO.setCode(department.getDepartmentId().toString());
        organizationVO.setName(department.getFullname());
        organizationVO.setUserNum(department.getUserNum());
        organizationVO.setAuthObjectType(AuthObjectType.ORG);
        return organizationVO;
    }
    private OrganizationVO convert(User user){
        OrganizationVO organizationVO = new OrganizationVO();
        organizationVO.setCode(user.getUsername());
        organizationVO.setName(user.getNickname());
        organizationVO.setAvatar(user.getAvatar());
        organizationVO.setAuthObjectType(AuthObjectType.EMPLOYEE);
        return organizationVO;
    }

    public List<Department> listDeptsByText(String searchText){
        List<Department> deptList = departmentService.list(Wrappers.<Department>lambdaQuery()
                .like(Department::getFullname, searchText)
        );
        if (CollectionUtils.isEmpty(deptList)){
            return Collections.EMPTY_LIST;
        }
        return deptList;

    }


    /**
     * 同步部门下的员工数量
     */
    public void syncUserNum(){
        List<Department> departments = departmentMapper.selectList(null);
        Map<Long, Department> departmentMap = departments.stream().collect(Collectors.toMap(d -> Long.valueOf(d.getDepartmentId()), d -> d));
        departments.forEach(d -> {
            List<Department> subDepts = listAllDepartmentByParentId(Long.valueOf(d.getDepartmentId()),departmentMap);
            Set<Integer> subDeptIds = subDepts.stream().map(Department::getDepartmentId).collect(Collectors.toSet());
            QueryWrapper<User> queryWrapper = new QueryWrapper();
            queryWrapper.in("department_id",subDeptIds);
            queryWrapper.eq("available", YesNoType.YES.getCode());
            Integer userNum = userMapper.selectCount(queryWrapper);
            d.setUserNum(userNum);
            departmentMapper.updateById(d);
        });
    }

    /**
     * 获取部门下的所有父部门列表
     * @return
     */
    public List<Department> listAllDepartmentByChildId(Long deptId){
        List<Department> departments = departmentMapper.selectList(null);
        if(CollectionUtils.isEmpty(departments)){
            return Collections.emptyList();
        }
        Map<Long, Department> organizationMap = departments.stream().collect(Collectors.toMap(d -> Long.valueOf(d.getDepartmentId()), o -> o));
        return listAllDepartmentByChildId(deptId,organizationMap);
    }

    /**
     * 获取部门下的所有子部门列表
     * @return
     */
    public List<Department> listAllDepartmentByChildId(Long deptId,Map<Long, Department> departmentMap){
        Department department = departmentMapper.selectOne(Wrappers.<Department>lambdaQuery().eq(Department::getDepartmentId, deptId));
        if (department == null || CollectionUtils.isEmpty(departmentMap.values())){
            return Collections.EMPTY_LIST;
        }
        List<Department> result = new ArrayList<>();
        findParent(deptId,departmentMap,result);
        return result;
    }

    /**
     * 获取部门下的所有子部门列表
     * @return
     */
    public List<Department> listAllDepartmentByParentId(Long deptId){

//        List<Department> departments = departmentMapper.selectList(null);
        List<Department> departments = MemCacheUtils.getDepartment(departmentMapper);
        if(CollectionUtils.isEmpty(departments)){
            return Collections.emptyList();
        }
        Map<Long, Department> organizationMap = departments.stream().collect(Collectors.toMap(d -> Long.valueOf(d.getDepartmentId()), o -> o));
        return listAllDepartmentByParentId(deptId,organizationMap);
    }



    /**
     * 获取部门下的所有子部门列表
     * @return
     */
    public List<Department> listAllDepartmentByParentId(Long deptId,Map<Long, Department> departmentMap){
        Department department = departmentMapper.selectOne(Wrappers.<Department>lambdaQuery().eq(Department::getDepartmentId, deptId));
        if (department == null){
            return Collections.EMPTY_LIST;
        }

        List<Department> departments = MemCacheUtils.getDepartment(departmentMapper);
//        List<Department> departments = departmentMapper.selectList(null);
        if(CollectionUtils.isEmpty(departments)){
            return Collections.emptyList();
        }
        List<Department> result = new ArrayList<>();
        findChildren(deptId,departmentMap,result);
        return result;
    }
    /**
     * 查找某一个组织的所有子组织(包含其自身)
     * @param deptId
     * @param departmentMap
     * @param children
     */
    private void findChildren(Long deptId, Map<Long, Department> departmentMap, List<Department> children){
        Department department = departmentMap.get(deptId);
        children.add(department);
        List<Department> subOrgs = departmentMap.values().stream().filter(o -> Objects.nonNull(o.getParentId()) && Objects.equals(o.getParentId().intValue(), deptId.intValue())).collect(Collectors.toList());
        if (! com.baomidou.mybatisplus.core.toolkit.CollectionUtils.isEmpty(subOrgs)){
            children.addAll(subOrgs);
            subOrgs.forEach(org -> findChildren(Long.valueOf(org.getDepartmentId()),departmentMap,children));
        }
    }


    /**
     * 查找某一个组织的所有父组织(包含其自身)
     * @param deptId
     * @param departmentMap
     */
    private void findParent(Long deptId, Map<Long, Department> departmentMap, List<Department> parents){
        Department department = departmentMap.get(deptId);
        parents.add(department);
        List<Department> parOrgs = departmentMap.values().stream().filter(o -> Objects.nonNull(o.getDepartmentId()) && Objects.nonNull(o.getParentId()) && Objects.equals(o.getDepartmentId().intValue(), department.getParentId().intValue())).collect(Collectors.toList());
        if (! com.baomidou.mybatisplus.core.toolkit.CollectionUtils.isEmpty(parOrgs)){
            parents.addAll(parOrgs);
            parOrgs.forEach(org -> findParent(Long.valueOf(org.getDepartmentId()),departmentMap,parents));
        }
    }




    public List<DepartmentVO> listDepartmentByParentId(Integer parentId){
        List<Department> departments = departmentMapper.selectList(Wrappers.<Department>lambdaQuery().eq(Department::getParentId,parentId));
        if (!CollectionUtils.isEmpty(departments)){
            List<DepartmentVO> collect = departments.stream().map(d -> {
                DepartmentVO departmentVO = new DepartmentVO();
                BeanUtils.copyProperties(d, departmentVO);
                return departmentVO;
            }).collect(Collectors.toList());
            return collect;
        } else {
            return Collections.EMPTY_LIST;
        }
    }


    public List<DepartmentTree> listDepartmentTree(){
        List<Department> departments = departmentMapper.selectList(null);
        if (!CollectionUtils.isEmpty(departments)){
            List<DepartmentTree> treeList = departments.stream().map(c -> {
                DepartmentTree tree = new DepartmentTree();
                BeanUtils.copyProperties(c, tree);
                return tree;
            }).collect(Collectors.toList());
            List<DepartmentTree> resultList = treeList.stream().filter(c -> c.getParentId() == null).collect(Collectors.toList());
            resultList.forEach(c -> findChild(c,treeList));
            return resultList;
        }
        return Collections.EMPTY_LIST;
    }

    private void findChild(DepartmentTree target,List<DepartmentTree> departmentTrees){
        List<DepartmentTree> childList = departmentTrees.stream().filter((c) -> Objects.equals(c.getParentId(), target.getDepartmentId())).collect(Collectors.toList());
        childList.forEach(c -> findChild(c,departmentTrees));
        if(!CollectionUtils.isEmpty(childList)){
            target.setChildren(childList);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncDepartment(){
        List<Department> departments = coaLoginManager.getDepartments();
        if (!CollectionUtils.isEmpty(departments)){
            Set<String> currentAllIdPaths = departmentMapper.getAllIdPath();
            // 更新或保存最新数据
            departments.forEach(d -> {
                departmentService.saveOrUpdate(d, Wrappers.<Department>lambdaQuery().eq(Department::getIdPath,d.getIdPath()));
            });
            // 删除旧数据
            Set<String> newIdPaths = departments.stream().map(Department::getIdPath).collect(Collectors.toSet());
            Set<String> deletedIdPaths = new HashSet<>();
            currentAllIdPaths.forEach(c -> {
                if (!newIdPaths.contains(c)){
                    deletedIdPaths.add(c);
                }
            });
            if(!CollectionUtils.isEmpty(deletedIdPaths)){
                departmentMapper.delete(Wrappers.<Department>lambdaQuery().in(Department::getIdPath,deletedIdPaths));
            }
        } else {
            log.info("查询到新的部门信息为空");
        }



    }
}

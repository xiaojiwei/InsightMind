package com.graphinsight.indicator.manager;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Maps;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.auto.entity.Category;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.TSuperAdmin;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.DepartmentMapper;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.auto.service.IDimensionService;
import com.graphinsight.indicator.auto.service.IDwTableService;
import com.graphinsight.indicator.auto.service.IMeasureService;
import com.graphinsight.indicator.auto.service.ITSuperAdminService;
import com.graphinsight.indicator.auto.service.IUserService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.model.AuthElement;
import com.graphinsight.indicator.model.dto.CoaUserInfo;
import com.graphinsight.indicator.model.dto.UserContext;
import com.graphinsight.indicator.service.SpaceService;
import com.graphinsight.indicator.util.MemCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @Author: lixiaolong
 * @Description:
 * @Date: 2021/12/13
 */
@Slf4j
@Service
@DS("mysql")
public class UserManager {

    @Autowired
    UserMapper userMapper;
    @Autowired
    DepartmentMapper departmentMapper;
    @Autowired
    DepartmentManager departmentManager;
    @Autowired
    COALoginManager loginManager;
    @Autowired
    IUserService userService;
    @Autowired
    SpaceService spaceService;
    @Autowired
    CacheManager cacheManager;
    @Autowired
    CategoryManager categoryManager;
    @Autowired
    ITSuperAdminService superAdminService;

    @Autowired
    IDwTableService dwTableService;
    @Autowired
    IMeasureService measureService;
    @Autowired
    IDimensionService dimensionService;

    public Map<String,User> getAllUserMap(){
        List<User> users = userService.list();
        if (CollectionUtils.isEmpty(users)){
            return Maps.newHashMap();
        }
        Map<String,User> result = new HashMap<>();
        users.forEach(u -> {
            result.put(u.getUsername(),u);
        });
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void wash(){
        // List<User> users = userService.list();
        // Map<Integer, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        // List<DwTable> dwTables = dwTableService.list();
        // dwTables.forEach(table -> {
        //     table.setCreateUser(userMap.get(table.getCreator()) == null ? null : userMap.get(table.getCreator()).getUsername());
        //     table.setUpdateUser(userMap.get(table.getUpdater()) == null ? null : userMap.get(table.getUpdater()).getUsername());
        // });
        // dwTableService.updateBatchById(dwTables);
        //
        // List<Measure> measures = measureService.list();
        // measures.forEach(table -> {
        //     table.setCreateUser(userMap.get(table.getCreator()) == null ? null : userMap.get(table.getCreator()).getUsername());
        //     table.setUpdateUser(userMap.get(table.getUpdater()) == null ? null : userMap.get(table.getUpdater()).getUsername());
        //     table.setOwnerUser(userMap.get(table.getOwner()) == null ? null : userMap.get(table.getOwner()).getUsername());
        // });
        // measureService.updateBatchById(measures);
        //
        // List<Dimension> dimensions = dimensionService.list();
        // dimensions.forEach(table -> {
        //     table.setCreateUser(userMap.get(table.getCreator()) == null ? null : userMap.get(table.getCreator()).getUsername());
        //     table.setUpdateUser(userMap.get(table.getUpdater()) == null ? null : userMap.get(table.getUpdater()).getUsername());
        // });
        // dimensionService.updateBatchById(dimensions);
    }

    public List<User> getUserBySearchText(String searchText){
        List<User> userList = userService.list(Wrappers.<User>lambdaQuery()
                .like(User::getUsername, searchText)
                .or()
                .like(User::getNickname, searchText)
        );
        if (CollectionUtils.isEmpty(userList)){
            return Collections.EMPTY_LIST;
        }
        return userList;

    }

    public boolean isSuperAdmin(String username){
        List<TSuperAdmin> tSuperAdmins = superAdminService.list(Wrappers.<TSuperAdmin>lambdaQuery().eq(TSuperAdmin::getEmpCode, username));
        return !CollectionUtils.isEmpty(tSuperAdmins);
    }


    @CheckCacheVersion
    public UserContext getUserContext(Long spaceId,String username){
        Map<String, Measure> allMeasureCodeMap = cacheManager.getMetadataCache().getAllMeasureCodeMap();
        Map<String, Dimension> allDimensionCodeMap = cacheManager.getMetadataCache().getAllDimensionCodeMap();
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername,username));
        UserContext userContext = new UserContext();
        if (isSuperAdmin(username)){
            List<Measure> measureList = new ArrayList<>();
            measureList.addAll(allMeasureCodeMap.values());
            userContext.setAuthMeasures(measureList);
            userContext.setSuperAdmin(true);
        } else {
            if (Objects.nonNull(user) && Objects.nonNull(spaceId)){
                userContext.setUser(user);
                Set<AuthElement> authElementBySpaceId = spaceService.getAuthElementBySpaceId(spaceId, user.getUsername());
                List<Measure> measureList = new ArrayList<>();
                List<Measure> measureList1 = authElementBySpaceId.stream()
                        .filter(authElement -> Objects.equals(authElement.getAuthElementType(), AuthElementType.MEASURE))
                        .filter(authElement -> authElement.getCode().startsWith(IndicatorConstant.MEASURE_CODE_PREFIX))
                        .map(authElement -> allMeasureCodeMap.get(authElement.getCode()))
                        .collect(Collectors.toList());

                List<String> categoryCodes = authElementBySpaceId.stream()
                        .filter(authElement -> Objects.equals(authElement.getAuthElementType(), AuthElementType.MEASURE))
                        .filter(authElement -> !authElement.getCode().startsWith(IndicatorConstant.MEASURE_CODE_PREFIX))
                        .filter(authElement -> !authElement.getCode().startsWith(IndicatorConstant.DIMSENSION_CODE_PREFIX))
                        .map(AuthElement::getCode)
                        .collect(Collectors.toList());

                if (!CollectionUtils.isEmpty(categoryCodes)){
                    Set<Integer> childrenIds = new HashSet<>();
                    categoryCodes.forEach(code -> {
                        List<Category> children = categoryManager.findAllChildren(Integer.valueOf(code));
                        childrenIds.addAll(children.stream().map(Category::getId).collect(Collectors.toSet()));
                    });
                    List<Measure> measureList2 = allMeasureCodeMap.values().stream().filter(meas -> childrenIds.contains(meas.getLeafCategoryId())).collect(Collectors.toList());
                    measureList.addAll(measureList2);
                }

                List<Dimension> dimensionList = authElementBySpaceId.stream()
                        .filter(authElement -> Objects.equals(authElement.getAuthElementType(), AuthElementType.DIMENSION))
                        .map(authElement -> allDimensionCodeMap.get(authElement.getCode()))
                        .collect(Collectors.toList());
                measureList.addAll(measureList1);
                userContext.setAuthMeasures(measureList);
                userContext.setDimensionsWithFilter(dimensionList);
            }
        }

        return userContext;
    }

    /**
     * 获取当前部门下的所有用户(包含子部门下的员工)
     * @return
     */
    public List<User> listUserByDeptId(Long deptId){
        // 获取部门下的所有子部门(包含本身)
        List<Department> departments = departmentManager.listAllDepartmentByParentId(deptId);

        if (! CollectionUtils.isEmpty(departments)){
            Set<Integer> deptIds = new HashSet<>();
            Set<Integer> ids = departments.stream().map(Department::getDepartmentId).collect(Collectors.toSet());
            deptIds.addAll(ids);
            return userMapper.selectList(Wrappers.<User>lambdaQuery()
                    .in(User::getDepartmentId,deptIds)
                    .eq(User::getAvailable,YesNoType.YES.getCode()));
        }
        return Collections.EMPTY_LIST;
    }


    public void syncUser(){
        Set<String> allUsername = userMapper.getAllUsername();
        Set<String> syncedUsername = new HashSet<>();
        List<Department> departments = departmentMapper.selectList(Wrappers.<Department>lambdaQuery().eq(Department::getDeptLevel, 2));
        // List<Department> departments = departmentMapper.selectList(Wrappers.<Department>lambdaQuery().eq(Department::getDepartmentId, 3603));
        departments.forEach(d -> syncUserByDeptId(d.getDepartmentId().toString(),syncedUsername));
        // 没有同步到的用户改为离职状态
        allUsername.forEach(username -> {
            if (!syncedUsername.contains(username)){
                User user = new User();
                user.setUsername(username);
                user.setAvailable(YesNoType.NO.getCode());
                userMapper.update(user,Wrappers.<User>lambdaQuery().eq(User::getUsername,username));
            }
        });

    }

    public void syncUser(String deptId){
        Set<String> allUsername = userMapper.getAllUsername();
        Set<String> syncedUsername = new HashSet<>();
        syncUserByDeptId(deptId,syncedUsername);
        // 没有同步到的用户改为离职状态
        allUsername.forEach(username -> {
            if (!syncedUsername.contains(username)){
                User user = new User();
                user.setUsername(username);
                user.setAvailable(YesNoType.NO.getCode());
                userMapper.update(user,Wrappers.<User>lambdaQuery().eq(User::getUsername,username));
            }
        });

    }

    private void syncUserByDeptId(String deptId,Set<String> syncedUsername){
        List<User> users = loginManager.getUsersByDepartment(deptId);
        if (!CollectionUtils.isEmpty(users)){
            AtomicInteger count = new AtomicInteger();
            users.forEach(u -> {
                count.incrementAndGet();
                userService.saveOrUpdate(u,Wrappers.<User>lambdaQuery().eq(User::getUsername,u.getUsername()));
                syncedUsername.add(u.getUsername());
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            });
            // List<User> newUsers = users.stream().filter(u -> !allUsername.contains(u.getUsername())).collect(Collectors.toList());
            // int size = newUsers.size();
            // int cycleNum = (int) Math.ceil(size / Double.valueOf(1000.0));
            // int endIndex = 0;
            // int startIndex = 0;
            // int saveNum = 0;
            // for (int i = 0; i < cycleNum; i++) {
            //     List<User> partDepts = new ArrayList<>(1000);
            //     startIndex = i * 1000;
            //     endIndex = (i + 1) * 1000 >= newUsers.size() ? newUsers.size() : (i + 1) * 1000;
            //     partDepts.addAll(newUsers.subList(startIndex,endIndex));
            //     partDepts.forEach(u -> {
            //         userService.saveOrUpdate(u,Wrappers.<User>lambdaQuery().eq(User::getUsername,u.getUsername()));
            //     });
            //     saveNum += partDepts.size();
            // }
            log.info("查询到[{}]部门下[{}]条用户信息,成功保存:[{}]条",deptId,users.size(),count.get());
        } else {
            log.info("查询[]部门下的新用户信息为空",deptId);
        }
    }


    public User getUserByName(String username){
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername,username));
        return user;
    }

    /**
     * 存在返回
     * 不存在注册并返回
     */
    public User regist(CoaUserInfo userInfo){

        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, userInfo.getUsername()));

        if (Objects.isNull(user)){
            user = new User();
            BeanUtils.copyProperties(userInfo,user);
            user.setJobNumber(userInfo.getJobNumber());
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            user.setNickname(userInfo.getName());
            userMapper.insert(user);
        }
        return user;
    }

    public User getUserById(Integer id){
        if (id == null){
            return null;
        }
        return userMapper.selectById(id);
    }


    public Map<Integer,User> getUserMapByIds(Set<Integer> ids){
        if (CollectionUtils.isEmpty(ids)){
            return Collections.EMPTY_MAP;
        }
        List<User> users = userMapper.selectBatchIds(ids);
        if (users != null){
            return users.stream().collect(Collectors.toMap(User::getId,u->u));
        }
        return Collections.EMPTY_MAP;
    }


    public User getUserByUsername(String username){
        List<User> userList = userService.list(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (CollectionUtils.isEmpty(userList)){
            return null;
        }
        return userList.get(0);

    }


    public List<User> listUserByUsernames(Collection<String> usernames){
        if (CollectionUtils.isEmpty(usernames)) {
            return Collections.EMPTY_LIST;
        }
        List<User> userList = userService.list(Wrappers.<User>lambdaQuery().in(User::getUsername, usernames));
        if (CollectionUtils.isEmpty(userList)){
            return Collections.EMPTY_LIST;
        }
        return userList;
    }

    public Map<String,User> getUserMapByUsernames(Collection<String> usernames){
        List<User> userList =  listUserByUsernames(usernames);
        if (userList != null){
            return userList.stream().collect(Collectors.toMap(User::getUsername,u->u));
        }
        return Collections.EMPTY_MAP;
    }
}

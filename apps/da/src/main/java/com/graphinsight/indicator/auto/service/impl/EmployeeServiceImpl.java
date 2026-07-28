package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.Employee;
import com.graphinsight.indicator.auto.entity.Organization;
import com.graphinsight.indicator.auto.mapper.EmployeeMapper;
import com.graphinsight.indicator.auto.mapper.OrganizationMapper;
import com.graphinsight.indicator.auto.service.IEmployeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @since 2022-05-25
 */
@Slf4j
@DS("mysql")
@Service
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper,Employee> implements IEmployeeService {


    @Autowired
    EmployeeMapper employeeMapper;

    @Autowired
    OrganizationMapper organizationMapper;

    @Override
    @Transactional
    public void updateEmployee(List<String> oldInfos, List<Employee> newInfos){
        List<Employee> list = employeeMapper.selectList(Wrappers.<Employee>lambdaQuery().in(Employee::getUsername,oldInfos));
        backupSql(oldInfos,list);
        List<Integer> orgTypes = new LinkedList<>();
        orgTypes.add(1);orgTypes.add(2);orgTypes.add(3);
        employeeMapper.delete(Wrappers.<Employee>lambdaQuery().in(Employee::getUsername,oldInfos).in(Employee::getOrgType,orgTypes));
        newInfos.stream().forEach(e->employeeMapper.insert(e));
    }

    @Override
    public void updateOrganization(HashMap<String, Organization> orgs){
        Set<String> set = organizationMapper.selectList(Wrappers.<Organization>lambdaQuery().isNotNull(Organization::getOrgCode)).stream().map(e->e.getOrgCode()).collect(Collectors.toSet());
        for (String key:orgs.keySet()){
            try {
                if (!set.contains(key)) organizationMapper.insert(orgs.get(key));
            }catch (Exception e){
                log.error("插入组织数据失败，org：{}",orgs.get(key),e);
            }
        }
    }

    public void backupSql(List<String> oldInfos,List<Employee> list){
        String deleteSql = "delete from employee where username in (";
        for (String username:oldInfos){
            deleteSql += "'" + username + "'" + ",";
        }

        String insertSql = "insert into employee(username,id,job_num,nickname,avatar,org_code,biz_type,org_type,available,employee_type,email,offduty)values";
        for (Employee employee:list){
            insertSql += "("
                    + "'"+employee.getUsername()+"'" +","
                    +employee.getId() +","
                    + "'"+employee.getJobNum()+"'" +","
                    + "'"+employee.getNickname()+"'" +","
                    + "'"+employee.getAvatar()+"'" +","
                    + "'"+employee.getOrgCode()+"'" +","
                    +employee.getBizType()+","
                    +employee.getOrgType()+","
                    +employee.getAvailable()+","
                    +employee.getEmployeeType()+","
                    + "'"+employee.getEmail()+"'" +","
                    +employee.getOffduty()+"),";
        }
        log.info("deleteSql: "+deleteSql);
        log.info("insertSql: " + insertSql);
    }
}

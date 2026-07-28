package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.Employee;
import com.graphinsight.indicator.auto.entity.Organization;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @since 2022-05-25
 */
@DS("mysql")
public interface IEmployeeService extends IService<Employee> {

    @Transactional
    void updateEmployee(List<String> oldInfos, List<Employee> newInfos);

    void updateOrganization(HashMap<String, Organization> orgs);
}

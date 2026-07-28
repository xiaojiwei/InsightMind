package com.graphinsight.indicator.job;

import com.alibaba.fastjson.JSONObject;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.manager.*;
import com.graphinsight.indicator.schedule.MeasureMonitorJob;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mortbay.util.ajax.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

/**
 * Date: 2022/3/4
 * Desc:
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
@ActiveProfiles("dev")
public class JobTest {

    @Autowired
    DepartmentManager departmentManager;
    @Autowired
    UserManager userManager;
    @Autowired
    COALoginManager loginManager;
    @Autowired
    OrganizationManager organizationManager;

    @Test
    public void syncOrgUserNum(){
        organizationManager.syncUserNum();
    }

    @Test
    public void syncDeparmentUserNum(){
        departmentManager.syncUserNum();
    }


    @Test
    public void syncDeparment(){
        departmentManager.syncDepartment();
    }

    @Test
    public void syncUser(){
        userManager.syncUser();
    }

    @Test
    public void getUserByDeptId(){
        List<User> usersByDepartment = loginManager.getUsersByDepartment("3603");
        System.out.println(JSON.toString(usersByDepartment));
    }

    @Test
    public void getUserByJobNum(){
        JSONObject shiyanhui = loginManager.getUserInfoByEmailPrefix("shiyanhui");
        System.out.println(shiyanhui);
    }

    @Test
    public void getDept(){
        List<Department> departments = loginManager.getDepartments();
        System.out.println(departments);
    }

    @Autowired
    MeasureMonitorManager measureMonitorManager;
    @Test
    public void getMsg(){
        measureMonitorManager.sendTips2();
    }

    @Autowired
    MeasureMonitorJob measureMonitorJob;

}

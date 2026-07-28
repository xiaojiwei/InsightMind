package com.graphinsight.indicator.user;

import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.manager.COALoginManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

/**
 * Date: 2022/1/25
 * Desc:
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
@ActiveProfiles("dev")
public class CoaLoginManagerTest {

    @Autowired
    COALoginManager coaLoginManager;

    @Test
    public void listDepartmentsTest(){
        List<Department> departments = coaLoginManager.getDepartments();
        System.out.println(departments.size());
    }

    @Test
    public void listUserByDeptId(){
        List<User> users = coaLoginManager.getUsersByDepartment("10984");
        System.out.println(users);
    }


}

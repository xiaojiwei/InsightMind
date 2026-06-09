package com.graphinsight.indicator.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.DepartmentMapper;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.OrganizationType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.manager.DepartmentManager;
import com.graphinsight.indicator.manager.OrganizationManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.DepartmentTree;
import com.graphinsight.indicator.model.vo.DepartmentVO;
import com.graphinsight.indicator.model.vo.OrganizationQueryVO;
import com.graphinsight.indicator.model.vo.OrganizationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/3/7
 * Desc:
 */
@RestController
@RequestMapping("/dept")
public class DepartmentController {

    @Autowired
    private DepartmentMapper departmentMapper;
    @Autowired
    private DepartmentManager departmentManager;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OrganizationManager organizationManager;

    @PostMapping("/search/organization/")
    public Response<List<OrganizationVO>> searchOrgs(@RequestBody OrganizationQueryVO queryVO){
        List<OrganizationVO> organizationVOS = new ArrayList<>();
        if (Objects.equals(OrganizationType.ORG.getValue(),queryVO.getOrgType())){
            organizationVOS.addAll(departmentManager.searchDepartment(queryVO.getSearchText(),20));
        } else {
            organizationVOS.addAll(organizationManager.searchOrganization(queryVO.getOrgType(), queryVO.getSearchText(), 20));
        }
        return Response.ok(organizationVOS);
    }


    @GetMapping("/get/organization/{parentCode}/{orgType}")
    public Response<List<OrganizationVO>> getOrganization(@PathVariable("parentCode") String parentCode,
                                                          @PathVariable("orgType") Integer orgType){
        List<OrganizationVO> result = new ArrayList<>();
        if (Objects.equals(OrganizationType.ORG.getValue(),orgType)){
            List<DepartmentVO> departments = departmentManager.listDepartmentByParentId(Integer.valueOf(parentCode));
            result.addAll(departments.stream().map(d -> {
                OrganizationVO o = new OrganizationVO();
                o.setAuthObjectType(AuthObjectType.ORG);
                o.setCode(d.getDepartmentId().toString());
                o.setUserNum(d.getUserNum());
                o.setName(d.getFullname());
                return o;
            }).collect(Collectors.toList()));
            List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery().eq(User::getDepartmentId, parentCode).eq(User::getAvailable, YesNoType.YES.getCode()));
            if (!CollectionUtils.isEmpty(users)){
                result.addAll(users.stream().map(u -> {
                    OrganizationVO o = new OrganizationVO();
                    o.setAuthObjectType(AuthObjectType.EMPLOYEE);
                    o.setAvatar(u.getAvatar());
                    o.setCode(u.getUsername());
                    o.setName(u.getNickname());
                    return o;
                }).collect(Collectors.toList()));
            }
        } else {
            result.addAll(organizationManager.listOrgByParentId(parentCode));
        }
        return Response.ok(result);
    }



    @GetMapping("/list/children/{parentId}")
    public Response<List<DepartmentVO>> listDepartment(@PathVariable("parentId") String parentId){
        return Response.ok(departmentManager.listDepartmentByParentId(Integer.valueOf(parentId)));
    }


    @GetMapping("/list/tree")
    public Response<List<DepartmentTree>> listDepartmentTree(){
        return Response.ok(departmentManager.listDepartmentTree());
    }


}

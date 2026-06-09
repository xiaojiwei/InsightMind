package com.graphinsight.indicator.controller;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.enums.JdbcDataSourceType;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.AuthService;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.SpaceEmployeeService;
import com.graphinsight.indicator.service.SpaceService;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@DS("mysql")
@RestController
@RequestMapping(IndicatorConstant.API_V1)
public class AuthController extends BaseController {

    @Autowired
    private AuthService authService;

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private ChartQueryService chartQueryService;

    @GetMapping(value = "/space/auth/all")
    @ResponseBody
    @Transactional
    public Response<AuthElement> findAuthElement(@RequestParam("spaceId") Long spaceId, @RequestParam("username") String username) {

        Response response = null;
        Boolean isSuper = false;

        try {

            //当前登录人在空间下配置的所有维度、指标权限
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Set<AuthElement> authElementSet = chartQueryService.getAuthElementSet(spaceId, username);
            response = Response.ok("查询成功", authElementSet);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;
    }

    @GetMapping(value = "/space/admin/get")
    @ResponseBody
    @Transactional
    public Response<Boolean> get() {

        Response response = null;
        Boolean isSuper = false;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            isSuper = authService.isSuperAdmin();
            response = Response.ok("查询成功", isSuper);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;
    }

    @PostMapping(value = "/space/auth/list")
    @ResponseBody
    @Transactional
    public Response<Auth> listPage(@RequestBody AuthSearchText searchText) {

        Response response = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Page page = this.authService.list(searchText);
            response = Response.ok("查询成功", page);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/space/auth/saveOrUpdate")
    @ResponseBody
    @Transactional
    public Response<Space> save(@RequestBody Space space) {

        Response response = null;
        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Long spaceId = this.authService.save(space);
            Space saveSpace = this.spaceService.get(spaceId);
            response = Response.ok("保存成功", saveSpace);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/space/auth/delete")
    @ResponseBody
    @Transactional
    public Response<Boolean> delete(@RequestBody DeleteParam deleteParam) {

        Response response = null;
        try {

            Integer authElementType = deleteParam.getAuthElementType();
            Long spaceId = deleteParam.getSpaceId();
            String employeeCode = deleteParam.getEmployeeCode();

            AuthElementType authType = AuthElementType.build(authElementType);
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            boolean isDel = this.authService.delete(spaceId, employeeCode, authType);

            response = Response.ok("删除成功", isDel);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("删除失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }



    @GetMapping(value = "/space/auth/get")
    @ResponseBody
    @Transactional
    public Response<Auth> get(@RequestParam("spaceId") Long spaceId, @RequestParam("employeeCode") String employeeCode, @RequestParam("authId") Long authId) {

        Response response = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Auth space = this.authService.get(spaceId, employeeCode, authId);
            response = Response.ok("查询成功", space);
        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @GetMapping(value = "/space/auth/employee/get")
    @ResponseBody
    @Transactional
    public Response<Auth> get(@RequestParam("spaceId") Long spaceId, @RequestParam("employeeCode") String employeeCode) {

        Response response = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            List<AuthElement> authElementList = this.authService.get(spaceId, employeeCode);
            response = Response.ok("查询成功", authElementList);
        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

}

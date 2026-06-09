package com.graphinsight.indicator.controller;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.SpaceEmployeeService;
import com.graphinsight.indicator.service.SpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@DS("mysql")
@RestController
@RequestMapping(IndicatorConstant.API_V1)
public class SpaceEmployeeController extends BaseController {

    @Autowired
    private SpaceEmployeeService spaceEmployeeService;

    @Autowired
    private SpaceService spaceService;

    @PostMapping(value = "/space/employee/list")
    @ResponseBody
    @Transactional
    public Response<SpaceEmployee> listPage(@RequestBody SearchText searchText) {

        Response response = null;

        try {
            Page page = this.spaceEmployeeService.list(searchText);
            response = Response.ok("查询成功", page);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/space/employee/saveOrUpdate")
    @ResponseBody
    @Transactional
    public Response<Space> save(@RequestBody Space space) {

        Response response = null;
        try {
            Long spaceId = this.spaceEmployeeService.save(space);
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

    @PostMapping(value = "/space/employee/delete")
    @ResponseBody
    @Transactional
    public Response<Boolean> delete(@RequestBody DeleteParam deleteParam) {

        Response response = null;
        try {

            Long spaceId = deleteParam.getSpaceId();
            String employeeCode = deleteParam.getEmployeeCode();

            boolean isDel = this.spaceEmployeeService.delete(spaceId, employeeCode);
            response = Response.ok("删除成功", isDel);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("删除失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }



    @GetMapping(value = "/space/employee/get")
    @ResponseBody
    @Transactional
    public Response<SpaceEmployee> get(@RequestParam("spaceId") Long id, @RequestParam("employeeCode") String employeeCode, @RequestParam("spaceEmpId") Long spaceEmpId) {

        Response response = null;

        try {
            SpaceEmployee spaceEmployee = this.spaceEmployeeService.get(id, employeeCode, spaceEmpId);
            response = Response.ok("查询成功", spaceEmployee);
        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

}

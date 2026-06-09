package com.graphinsight.indicator.controller;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.JdbcDataSourceType;
import com.graphinsight.indicator.model.Folder;
import com.graphinsight.indicator.model.Page;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.SearchText;
import com.graphinsight.indicator.model.vo.FolderDataSourceVO;
import com.graphinsight.indicator.service.FolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@DS("mysql")
@RestController
@RequestMapping(IndicatorConstant.API_V1)
public class FolderController extends BaseController {

    @Autowired
    private FolderService floderService;

    @PostMapping(value = "/folder/list")
    @ResponseBody
    @Transactional
    public Response<FolderDataSourceVO> listFolderPage(@RequestBody SearchText searchText) {

        Response response = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Page page = this.floderService.listFolderPage(searchText);
            response = Response.ok("查询成功", page);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/datasource/folder/list")
    @ResponseBody
    @Transactional
    public Response<FolderDataSourceVO> listPage(@RequestBody SearchText searchText) {

        Response response = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Page page = this.floderService.allList(searchText);
            response = Response.ok("查询成功", page);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/folder/saveOrUpdate")
    @ResponseBody
    @Transactional
    public Response<Folder> save(@RequestBody Folder folder) {

        Response response = null;
        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Long dsId = this.floderService.save(folder);
            Folder saveFolder = this.floderService.get(dsId);
            response = Response.ok("保存成功", saveFolder);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/folder/delete")
    @ResponseBody
    @Transactional
    public Response<Boolean> delete(@RequestBody Folder folder) {

        Response response = null;
        try {
            Long id = folder.getId();
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            boolean isDel = this.floderService.delete(id);
            response = Response.ok("删除成功", isDel);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("删除失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @GetMapping(value = "/folder/children/get")
    @ResponseBody
    @Transactional
    public Response<FolderDataSourceVO> getChildren(@RequestParam("id") Long id) {

        Response response = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Page page = this.floderService.getChild(id);
            response = Response.ok("查询成功", page);
        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @GetMapping(value = "/folder/get")
    @ResponseBody
    @Transactional
    public Response<Folder> get(@RequestParam("id") Long id) {

        Response response = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Folder folder = this.floderService.get(id);
            response = Response.ok("查询成功", folder);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

}

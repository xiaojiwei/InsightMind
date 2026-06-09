package com.graphinsight.indicator.controller;

import com.graphinsight.indicator.annotation.IgnoreWebLog;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.dao.CacheReloadTaskDao;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.SearchText;
import com.graphinsight.indicator.service.CacheReloadScheduleTaskService;
import com.graphinsight.indicator.service.KeyWordService;
import com.graphinsight.indicator.service.impl.BuildSqlServiceImpl;
import com.graphinsight.indicator.service.impl.KeyWord2ServiceImpl;
import com.graphinsight.indicator.service.impl.SqliteSQLGeneratorReadServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(IndicatorConstant.API_V1)
public class CacheReloadTaskController extends BaseController {

    @Autowired
    private KeyWordService keyWordService;

    @Autowired
    private CacheReloadTaskDao cacheReloadTaskDao;

    @Autowired
    private CacheReloadScheduleTaskService cacheReloadScheduleTaskService;

    @Autowired
    private SqliteSQLGeneratorReadServiceImpl sqliteSQLGeneratorReadService;

    @GetMapping(value = "/test")
    @ResponseBody
    @Transactional
    public void test(@RequestHeader HttpHeaders headers, @CookieValue(value = "authorization") String cookie) {
        System.out.println(headers);
    }

    @PostMapping(value = "/cachetask")
    @ResponseBody
    @Transactional
    public Response get(@RequestBody SearchText searchText) {

        Response response = null;

        try {
            Integer page = this.cacheReloadTaskDao.findByLockKey("fff");
            response = Response.ok("查询成功", page);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/cachetask/dimvalue/build")
    @ResponseBody
    public Response buildAllDimData() {

        Response response = null;

        try {
            this.cacheReloadScheduleTaskService.buildAllDimData();
            response = Response.ok("查询成功");

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/cachetask/create")
    @ResponseBody
    public Response createCacheTask() {

        Response response = null;

        try {
            this.cacheReloadScheduleTaskService.createCacheTask();
            response = Response.ok("查询成功");

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/cachedata/flush")
    @ResponseBody
    public Response flushCacheData() {

        Response response = null;

        try {
            this.cacheReloadScheduleTaskService.flushCacheData();
            response = Response.ok("查询成功");

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/cachedata/snapshot")
    @ResponseBody
    public Response snapshot() {

        Response response = null;

        try {
            this.cacheReloadScheduleTaskService.flushCacheData();
            response = Response.ok("查询成功");

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @IgnoreWebLog
    @GetMapping(value = "/dbsql")
    public void download(HttpServletResponse response) {
        try {
            sqliteSQLGeneratorReadService.exportDB(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @IgnoreWebLog
    @GetMapping(value = "/word")
    public Response word(@RequestParam("key") String key) {

        Response<PageData> response = null;

        try {
            key = BuildSqlServiceImpl.formatSqlValue(key);
            PageData pageData = keyWordService.doAction(key);;
            response = Response.ok("查询成功", pageData);
            return response;

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }


    @Autowired
    private KeyWord2ServiceImpl keyWord2Service;

    @IgnoreWebLog
    @GetMapping(value = "/word2")
    public Response word2(@RequestParam("key") String key,@RequestParam("isData") Boolean isData) {
        Response<PageData> response = null;

        try {
            PageData pageData = keyWord2Service.doAction2(null, key,isData);
            response = Response.ok("查询成功", pageData);
            return response;

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;
    }


}

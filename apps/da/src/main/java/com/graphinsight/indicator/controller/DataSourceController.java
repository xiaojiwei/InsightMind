package com.graphinsight.indicator.controller;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.graphinsight.indicator.annotation.IgnoreWebLog;
import com.graphinsight.indicator.annotation.ReloadCache;
import com.graphinsight.indicator.auto.entity.TSuperAdmin;
import com.graphinsight.indicator.auto.service.IDataSourceService;
import com.graphinsight.indicator.auto.service.ITSpaceService;
import com.graphinsight.indicator.auto.service.ITSuperAdminService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.CacheStrategy;
import com.graphinsight.indicator.enums.ChartType;
import com.graphinsight.indicator.enums.JdbcDataSourceType;
import com.graphinsight.indicator.enums.ResponseErrorType;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.vo.DatasourceBatchQuery;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.DataSourceService;
import com.graphinsight.indicator.service.DimensionQueryService;
import com.graphinsight.indicator.service.IndicatorService;
import com.graphinsight.indicator.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RestController
@Scope("request")
@RequestMapping(IndicatorConstant.API_V1)
public class DataSourceController extends BaseController {

    @Autowired
    private ChartQueryService chartQueryService;

    @Autowired
    private DimensionQueryService dimQueryService;

    @Autowired
    private DataSourceService dataSourceService;

    /**
     * 指标服务
     */
    @Autowired
    private IndicatorService indicatorService;

    @PostMapping(value = "/datasource/folder/get")
    @ResponseBody
    @Transactional
    public Response listPageByFolderId(@RequestParam("folderId") Long folderId) {

        Response<com.graphinsight.indicator.model.Page> response = null;
        try {

            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            com.graphinsight.indicator.model.Page page = this.dataSourceService.list(folderId);
            response = Response.ok("查询成功", page);

        } catch (Exception ex) {
            log.error("调用异常:",ex);
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    /**
     * copy 功能
     * @param dataSource
     * @return
     */
    @PostMapping(value = "/datasource/copy")
    @ResponseBody
    @Transactional
    public Response copy(@RequestBody DataSource dataSource) {

        Response<Long> response = null;
        try {

            Long dataSourceId = dataSource.getId();
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Long id = this.dataSourceService.copy(dataSourceId);
            response = Response.ok("查询成功", id);

        } catch (Exception ex) {
            log.error("调用异常:",ex);
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/datasource/list")
    @ResponseBody
    @Transactional
    @IgnoreWebLog
    public Response listPage(@RequestBody SearchText searchText) {

        Response<com.graphinsight.indicator.model.Page> response = null;
        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            com.graphinsight.indicator.model.Page page = this.dataSourceService.list(searchText);
            response = Response.ok("查询成功", page);

        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("调用异常:",ex);
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    /*
    @PostMapping(value = "/datasources")
    @ResponseBody
    @Transactional
    public Response listPage(@RequestBody DataSource dataSource) {

        Response response = null;
        try {

            Page<DataSource> page = this.dataSourceService.list(dataSource);
            response = Response.ok("查询成功", page);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }
    */

    @PostMapping(value = "/datasource/name/folder/save")
    @ResponseBody
    @Transactional
    public Response saveByNameAndFolderId(@RequestBody DataSource dataSource) {

        Response<DataSource> response = null;
        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Long dsId = this.dataSourceService.saveByNameAndFolderId(dataSource);
            DataSource saveDataSource = this.dataSourceService.get(dsId);
            response = Response.ok("保存成功", saveDataSource);

        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("调用异常:",ex);
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @ReloadCache
    @PostMapping(value = "/datasource/save")
    @ResponseBody
    @Transactional
    public Response save(@RequestBody DataSource dataSource) {

        Response<DataSource> response = null;
        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Long dsId = this.dataSourceService.save(dataSource);
            DataSource saveDataSource = this.dataSourceService.get(dsId);
            response = Response.ok("保存成功", saveDataSource);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            log.error("调用异常:",ex);
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @ReloadCache
    @PostMapping(value = "/datasource/delete")
    @ResponseBody
    @Transactional
    public Response delete(@RequestBody DataSource dataSource) {

        Response<Boolean> response = null;
        try {
            Long id = dataSource.getId();
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            boolean isDel = this.dataSourceService.delete(id);
            response = Response.ok("删除成功", isDel);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("删除失败");
            log.error("调用异常:",ex);
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/datasource/batch/list")
    @ResponseBody
    @Transactional
    public Response list(@RequestBody DatasourceBatchQuery datasourceBatchQuery) {

        Response<List<DataSource>> response = null;
        try {
            List<DataSource> dataSources = new ArrayList<>();
            if (CollectionUtils.isEmpty(datasourceBatchQuery.getIds())){
                return Response.ok(dataSources);
            }
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            dataSources = this.dataSourceService.listByIds(datasourceBatchQuery.getIds());
            response = Response.ok("查询成功", dataSources);
        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            log.error("调用异常:",ex);
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }


    @GetMapping(value = "/datasource/get")
    @ResponseBody
    @IgnoreWebLog
    @Transactional
    public Response get(@RequestParam("id") Long id) {

        Response<DataSource> response = null;
        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            DataSource dataSource = this.dataSourceService.get(id);
            List<Filter> filterList = dataSource.getFilterList();
            int size = filterList.size();
            response = Response.ok("查询成功", dataSource);
        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            log.error("调用异常:",ex);
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @Autowired
    private IDataSourceService iDataSourceService;


    @GetMapping(value = "/datasource/listBySpaceId")
    @ResponseBody
    @IgnoreWebLog
    public Response listBySpaceId(@RequestParam("spaceId") Long spaceId) {

        Response response = null;
        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            List<com.graphinsight.indicator.auto.entity.DataSource> sources = iDataSourceService.list(Wrappers.<com.graphinsight.indicator.auto.entity.DataSource>lambdaQuery().eq(com.graphinsight.indicator.auto.entity.DataSource::getSpaceId, spaceId));
            response = Response.ok(sources);
        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            log.error("调用异常:",ex);
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/datasource/query/direct/count")
    @ResponseBody
    @IgnoreWebLog
    public Response queryDirectCount(@RequestBody DataSource dataSource) {

        //获取用户名
        String userName = UserThreadLocalUtil.getUserName();
        PageData pageData = null;
        Response<PageData> response = null;
        try {

            dataSource.setDirectQuery(true);
            dataSource.setChartType(ChartType.TABLE);
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            pageData = this.chartQueryService.execQuery(dataSource);
            response = Response.ok("查询成功", pageData);

        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("调用异常:",ex);
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/datasource/pivot/query")
    @ResponseBody
    @IgnoreWebLog
    public Response queryPivot(@RequestBody DataSource dataSource) {

        //获取用户名
        String userName = UserThreadLocalUtil.getUserName();
        PageData pageData = null;
        Response<PageData> response = null;
        try {

            //总数
            DataSource directCountDS = CloneUtils.clone(dataSource);
            directCountDS.setChartType(ChartType.TABLE);
            Response countData = this.queryDirectCount(directCountDS);

            //列维度个数
            DataSource columnDimDS = CloneUtils.clone(dataSource);
            columnDimDS.setChartType(ChartType.TABLE);
            columnDimDS.setPageSize(999);
            columnDimDS = this.chartQueryService.getColumnDataSource(columnDimDS);
            Response countColumnData = this.queryDirectCount(columnDimDS);

            boolean pageAble = dataSource.isPageable();
            if (pageAble) {
                //当前页时需要取的维度
                DataSource rowDimDS = CloneUtils.clone(dataSource);
                rowDimDS.setChartType(ChartType.TABLE);
                rowDimDS = this.chartQueryService.getRowDataSource(rowDimDS);

                PageData rowPageData = this.chartQueryService.execQuery(rowDimDS);
                FilterTree filterList = this.chartQueryService.buildFilter(rowPageData);

                List<FilterTree> filterTreeList = this.chartQueryService.buildFilterTree(filterList, dataSource.getFilterList());
                dataSource.setFilterTreeList(filterTreeList);
//                dataSource.setFilterList(new ArrayList<>());
                //end 当前页时需要取的维度
            }

            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            pageData = this.chartQueryService.execQuery(dataSource);
            response = Response.ok("查询成功", pageData);

        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("调用异常:",ex);
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @Autowired
    ITSuperAdminService itSuperAdminService;
    private boolean isSuperAdmin(String username){
        boolean res = false;
        try {
            List<TSuperAdmin> list = itSuperAdminService.list();
            Set<String> names = list.stream().map(TSuperAdmin::getEmpCode).collect(Collectors.toSet());
            if (names.contains(username)){
                res = true;
            }
        }catch (Exception e){
            log.error("超级管理员查询失败",e);
        }
        return res;
    }

    @PostMapping(value = "/datasource/query")
    @ResponseBody
    @IgnoreWebLog
    public Response query(@RequestBody DataSource dataSource) {

        Long begin = System.currentTimeMillis();
        UserThreadLocalUtil.setBeginTime();

        //获取用户名
        String userName = dataSource.getUsername();
        if (StringUtil.isEmpty(userName)) {
            userName = UserThreadLocalUtil.getUserName();
            dataSource.setUsername(userName);
        }

        PageData pageData = null;
        Response<PageData> response = null;

        Long spaceId = dataSource.getSpaceId();
        if(null == spaceId){
            spaceId = 4L;
        }
        if (null == spaceId && !isSuperAdmin(userName)) {

            log.error("调用异常:", "spaceId is null");
            response = Response.error("查询失败,spaceId为null，请联系开发.");

            response.setErrorType(ResponseErrorType.SYSTEM);
            response.setErrorOwner("doulinxu1");//系统级错误先指定开发

            response.setErrorMessage("spaceId is null");

        } else {

            try {

                DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
                //正常查询都走直查
                dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
                pageData = this.chartQueryService.execQuery(dataSource);

                pageData.setCost(System.currentTimeMillis() - begin);
                response = Response.ok("查询成功", pageData);

                //测试
//                this.chartQueryService.addQueryLog(dataSource, pageData);
                pageData.setLoginUserName(userName);

                UserThreadLocalUtil.printCost("DataSourceController.end");

            } catch (Exception ex) {
                ex.printStackTrace();
                log.error("调用异常:",ex);
                response = Response.error("查询失败");

                if (ex instanceof IllegalArgumentException) {
                    String owenr = String.valueOf(TempThreadLocalUtil.get("owner"));
                    response.setErrorOwner(owenr);
                    response.setErrorType(ResponseErrorType.DATA);
                } else {
                    response.setErrorType(ResponseErrorType.SYSTEM);
                    response.setErrorOwner("xiaojiwei");//系统级错误先指定开发
                }

                response.setErrorStackTrace(ex.getStackTrace());
                response.setErrorMessage(ex.toString());
            }

        }

        return response;

    }

    @PostMapping(value = "/datasource/query/count")
    @ResponseBody
    @IgnoreWebLog
    public Response queryCount(@RequestBody DataSource dataSource) {

        Response<PageData> response = null;
        PageData pageData = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            pageData = this.chartQueryService.execCountQuery(dataSource);
            response = Response.ok("查询成功", pageData);
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("调用异常:",ex);
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/dimension/auth/value/list")
    @ResponseBody
    @IgnoreWebLog
    public Response getDimensionAuthValues(@RequestBody DimensionQueryParam dimQueryParam) {

        dimQueryParam.setIsAuth(true);
        return this.getDimensionValues(dimQueryParam);

    }

    private static Cache<Object, Object> cache = CacheBuilder.newBuilder()
            .initialCapacity(10000)
            .concurrencyLevel(20)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    @PostMapping(value = "/dimension/value/list")
    @ResponseBody
    @IgnoreWebLog
    public Response getDimensionValues(@RequestBody DimensionQueryParam dimQueryParam) {

        Response<PageData> response = null;
        PageData pageData = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());

            String key = dimQueryParam.getkey();
            Object obj = cache.getIfPresent(key);
            if (null == obj) {
                pageData = this.dimQueryService.execQueryDimensionValues(dimQueryParam, false);
                cache.put(key, pageData);

            } else {
                pageData = (PageData) obj;
             }

            response = Response.ok("查询成功", pageData);
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("调用异常:",ex);
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @IgnoreWebLog
    @PostMapping(value = "/datasource/download")
    public void download(HttpServletResponse response, @RequestBody DataSource dataSource) {
        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            this.dataSourceService.exportExcel(dataSource, response);
        } catch (IOException e) {
            e.printStackTrace();
            log.error("调用异常:",e);
        }
    }

    /**
     * https://li.feishu.cn/docs/doccnhmoofPOxwoDJL9OBmkL5dh
     * 三 异步下载文件最第三步，从云BOS上下载文件
     * @param response
     * @param downloadId 下载唯一标识
     */
    @IgnoreWebLog
    @PostMapping(value = "/datasource/downloadbos")
    public void downloadbos(HttpServletResponse response, @RequestParam("downloadid") String downloadId) {
        try {
            this.dataSourceService.exportBos(downloadId, response);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("调用异常:",e);
        }
    }

    @IgnoreWebLog
    @PostMapping(value = "/datasource/file")
    public void downloadFile(HttpServletResponse response, @RequestParam("downloadid") String downloadId) {
        this.dataSourceService.downFile(downloadId, response);
    }

    /**
     * https://li.feishu.cn/docs/doccnhmoofPOxwoDJL9OBmkL5dh
     * 二 异步下载文件第二步，实时获取下载情况
     * @param downloadId
     * @return
     */
    @PostMapping(value = "/datasource/filestatus/get")
    public Response getFileDownStatus(@RequestParam("downloadid") String downloadId) {

        Response<FileDownInfo> response = new Response();
        FileDownInfo fileDownInfo = this.dataSourceService.getFileStatus(downloadId);
        response = Response.ok(fileDownInfo);

        return response;

    }

    /**
     * https://li.feishu.cn/docs/doccnhmoofPOxwoDJL9OBmkL5dh
     * 一 异步下载文件第一步，获取最新的下载唯一标识
     * @param dataSource
     * @return
     */
    @PostMapping(value = "/datasource/downloadid/get")
    @IgnoreWebLog
    public  Response syncDownload(@RequestBody DataSource dataSource) {

        Response<String> response = new Response();
        String downloadid = this.dataSourceService.exportFile(dataSource);
        response.setData(downloadid);

        return response;

    }

    @PostMapping(value = "/graph/sql")
    @ResponseBody
    @IgnoreWebLog
    public Response getSql(@RequestBody DataSource dataSource) {

        dataSource.setOnlySql(true);
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());

        PageData pageData = this.chartQueryService.execQuery(dataSource);

        Response<PageData> response = Response.ok("查询成功", pageData);

        return response;

    }

    @GetMapping(value = "/query/measure")
    @ResponseBody
    public Response queryTest(@RequestParam("code") String code) {

        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        Map<String, String> errorMap = this.chartQueryService.test(code);;

        Response<PageData> response = Response.ok("查询成功", errorMap);

        return response;

    }

    @GetMapping(value = "/all/measure")
    @ResponseBody
    public Response queryMeasure() {

        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        List<Measure> measureList = this.indicatorService.listAllMeasure();

        Response<PageData> response = Response.ok("查询成功", measureList);

        return response;

    }

    @GetMapping(value = "/all/datasource")
    @ResponseBody
    public Response queryDataSource() {

        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        List<DataSource> dataSourceList = this.dataSourceService.listAll();

        List<DataSource> viewDataSourceList = new ArrayList<>();
        for (DataSource dataSource : dataSourceList) {
            DataSource viewDataSource = new DataSource();
            viewDataSource.setId(dataSource.getId());
            viewDataSource.setName(dataSource.getName());
            viewDataSourceList.add(viewDataSource);
        }

        Response<PageData> response = Response.ok("查询成功", viewDataSourceList);

        return response;

    }

    @GetMapping(value = "/query/datasource")
    @ResponseBody
    @IgnoreWebLog
    @Transactional
    public Response queryDataSource(@RequestParam("id") Long id) {

        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        Response<PageData> response = null;

        try {
            PageData pageData = this.chartQueryService.testQuery(id);
            response = Response.ok("查询成功", null);
        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.ok("查询成功", ex);

        }

        return response;

    }

}

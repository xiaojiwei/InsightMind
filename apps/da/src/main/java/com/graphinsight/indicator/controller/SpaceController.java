package com.graphinsight.indicator.controller;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.graphinsight.indicator.annotation.ReloadCache;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.JdbcDataSourceType;
import com.graphinsight.indicator.model.Page;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.SearchText;
import com.graphinsight.indicator.model.Space;
import com.graphinsight.indicator.model.vo.SpaceVO;
import com.graphinsight.indicator.service.RedisCacheService;
import com.graphinsight.indicator.service.SpaceService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@DS("mysql")
@RestController
@RequestMapping(IndicatorConstant.API_V1)
@Slf4j
public class SpaceController extends BaseController {

    @Autowired
    private SpaceService spaceService;

    @PostMapping(value = "/space/all/list")
    @ResponseBody
    @Transactional
    public Response<SpaceVO> listAllPage(@RequestBody SearchText searchText) {

        Response response = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Page page = this.spaceService.listAll(searchText);
            response = Response.ok("查询成功", page);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    private static Cache<Object, Object> CACHE = CacheBuilder.newBuilder()
            .initialCapacity(10000)
            .concurrencyLevel(20)
            .expireAfterWrite(20, TimeUnit.DAYS)
            .build();

    @PostMapping(value = "/space/list")
    @ResponseBody
    @Transactional
    public Response<SpaceVO> listPage(@RequestBody SearchText searchText) {
        log.info("查看内容{}", searchText);
        String userName = UserThreadLocalUtil.getUserName();

        Response response = null;
        try {
            String key = userName + searchText.getText() + searchText.getPageNo() + searchText.isMine();
            if (null != searchText.getFlash() && searchText.getFlash()) {
                CACHE.put(key, null);
            }
            Object pageValue = CACHE.getIfPresent(key);
            Page page = null;
            if (null == pageValue || CollectionUtils.isEmpty(((Page)pageValue).getContent())) {

                DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
                page = this.spaceService.list(searchText);

                CACHE.put(key, page);

            } else {
                page = (Page) pageValue;
            }

            response = Response.ok("查询成功", page);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/space/saveOrUpdate")
    @ResponseBody
    @ReloadCache
    @Transactional
    public Response<Space> save(@RequestBody Space space) {

        Response response = null;
        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Long dsId = this.spaceService.save(space);
            Space saveSpace = this.spaceService.get(dsId);
            response = Response.ok("保存成功", saveSpace);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @PostMapping(value = "/space/delete")
    @ResponseBody
    @ReloadCache
    @Transactional
    public Response<Boolean> delete(@RequestBody Space space) {

        Response response = null;
        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Long id = space.getId();
            boolean isDel = this.spaceService.delete(id);
            response = Response.ok("删除成功", isDel);

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("删除失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }



    @GetMapping(value = "/space/get")
    @ResponseBody
    @Transactional
    public Response<Space> get(@RequestParam("id") Long id) {

        Response response = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            Boolean exist = this.spaceService.has(id);
            if (!exist) {
                response = Response.ok("空间不存在", null);
            } else {
                Space space = this.spaceService.get(id);
                response = Response.ok("查询成功", space);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

}

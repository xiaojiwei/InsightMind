package com.graphinsight.indicator.controller;


import com.graphinsight.indicator.annotation.CurrentUser;
import com.graphinsight.indicator.annotation.IgnoreWebLog;
import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.manager.PortalManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.AuthQuery;
import com.graphinsight.indicator.model.vo.Grant;
import com.graphinsight.indicator.model.vo.GrantAuth;
import com.graphinsight.indicator.model.vo.MeasureMonitorVO;
import com.graphinsight.indicator.model.vo.PageVO;
import com.graphinsight.indicator.model.vo.PortalQuery;
import com.graphinsight.indicator.model.vo.PortalVO;
import com.graphinsight.indicator.util.CreatGroupUtil;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 门户表 前端控制器
 * </p>
 *
 * @since 2022-10-24
 */
@RestController
@RequestMapping("/portal")
public class PortalController {

    @Resource
    PortalManager portalManagerV2;

    @Resource
    CreatGroupUtil creatGroupUtil;

    @PostMapping("/page/list/object")
    public Response<PageVO<GrantAuth>> pageObjects(@RequestBody @Validated AuthQuery query){
        PageVO<GrantAuth> pageVO = portalManagerV2.pageObjectByElement(query);
        return Response.ok(pageVO);
    }

    @OperateLog
    @PostMapping("/append/grant")
    public Response appendGrant(@RequestBody @Validated Grant grant){
        portalManagerV2.appendGrant(grant);
        return Response.ok();
    }

    @OperateLog
    @PostMapping("/cover/grant")
    public Response coverGrant(@RequestBody @Validated Grant grant){
        portalManagerV2.converGrant(grant);
        return Response.ok();
    }




    @OperateLog
    @PostMapping("/save")
    public Response save(@RequestBody @Validated PortalVO portalVO){
        PortalVO vo = portalManagerV2.saveOrUpdate(portalVO);
        return Response.ok(vo);
    }

    @PostMapping("/list")
    public Response<List<PortalVO>> list(@RequestBody @Validated PortalQuery query){
        List<PortalVO> vos = portalManagerV2.list(query);
        return Response.ok(vos);
    }

    @GetMapping("/detail/{id}")
    @IgnoreWebLog
    public Response<PortalVO> detail(@PathVariable Long id){
        PortalVO vo = portalManagerV2.detail(id);
        if (CollectionUtils.isEmpty(vo.getCustomers())) {
            vo.setCustomers(new ArrayList<>());
        }
        return Response.ok(vo);
    }

    @GetMapping("/url/detail/{url}")
    @IgnoreWebLog
    public Response<PortalVO> detail(@PathVariable String url){
        PortalVO vo = portalManagerV2.detailUrl(url);
        return Response.ok(vo);
    }


    @OperateLog
    @GetMapping("/delete/{id}")
    public Response<MeasureMonitorVO> delete(@PathVariable Long id){
        portalManagerV2.delete(id);
        return Response.ok();
    }

    @GetMapping("/createGroup")
    public Response createGroup(Long id, @CurrentUser User user) {
        creatGroupUtil.creatFeishuGroup(id, user);
        return Response.ok("拉群成功");
    }
}

package com.graphinsight.indicator.controller;

import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.enums.ResultCode;
import com.graphinsight.indicator.exception.BusinessWarnException;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.service.IDimensionService;
import com.graphinsight.indicator.manager.DismantlingTreeManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.dto.DismantlingConfigTree;
import com.graphinsight.indicator.model.dto.defaultTree.DismantlingConfigTreeFEDefault;
import com.graphinsight.indicator.model.vo.DismantlingTreeQuery;
import com.graphinsight.indicator.model.vo.DismantlingTreeVO;
import com.graphinsight.indicator.model.vo.DismantlingTreeWrapVO;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import com.graphinsight.indicator.util.UuidUtil;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * <p>
 * 决策树表 前端控制器
 * </p>
 *
 * @author lixiaolong5
 * @since 2022^06^13
 */
@Slf4j
@RestController
@CrossOrigin
@RequestMapping("/dismantling/tree")
public class DismantlingTreeController {

    @Resource
    DismantlingTreeManager dismantlingTreeManagerV2;

    @GetMapping("/query/meas-code/{meas}")
    public Response<DismantlingTreeVO> queryByMeasCode(@PathVariable String meas) {
        DismantlingTreeVO dismantlingTreeVO = null;
        try {
            dismantlingTreeVO = dismantlingTreeManagerV2.queryTree(meas);
        } catch (Exception ex) {
            log.error("queryByMeasCode ex:{}", ex.getMessage(), ex);
        }

        return Response.ok(dismantlingTreeVO);
    }

    @PostMapping("/query")
    public Response<DismantlingTreeVO> query(@RequestBody DismantlingTreeQuery query) {
        DismantlingTreeVO dismantlingTreeVO = dismantlingTreeManagerV2.queryTree(query);
        return Response.ok(dismantlingTreeVO);
    }

    @PostMapping("/query/async")
    public Response<String> queryAsync(@RequestBody DismantlingTreeQuery query) {
        if (Objects.nonNull(query)) {
            String taskId = UuidUtil.getUUID32();

            dismantlingTreeManagerV2.queryTreeAsync(query, taskId);

            return Response.ok("", taskId);
        }

        return Response.error("参数错误");
    }

    @PostMapping("/query/wrap")
    public Response<DismantlingTreeWrapVO> queryWrap(@RequestBody DismantlingTreeQuery query) {
        if (Objects.nonNull(query)) {
            DismantlingTreeWrapVO wrapVO = new DismantlingTreeWrapVO();
            if (StringUtils.isEmpty(query.getQueryId())) {
                String queryId = UuidUtil.getUUID32();
                wrapVO.setProgress(0);
                wrapVO.setQueryId(queryId);
                dismantlingTreeManagerV2.queryTreeAsync(query, queryId);
                return Response.ok("", wrapVO);
            } else {
                int progress = dismantlingTreeManagerV2.getProgress(query.getQueryId());
                wrapVO.setProgress(progress);
                wrapVO.setQueryId(query.getQueryId());

                if (progress == 100) {
                    DismantlingTreeVO dismantlingTreeVO = dismantlingTreeManagerV2.queryTreeCache(query.getQueryId());
                    wrapVO.setDismantlingTree(dismantlingTreeVO);
                }

                return Response.ok("", wrapVO);
            }
        }

        return Response.error("参数错误");
    }

    @GetMapping("/query/task-id/{taskId}")
    public Response<DismantlingTreeVO> queryAsyncResult(@PathVariable("taskId") String taskId) {
        DismantlingTreeVO dismantlingTreeVO = dismantlingTreeManagerV2.queryTreeCache(taskId);
        return Response.ok(dismantlingTreeVO);
    }

    @GetMapping("/query/progress/{taskId}")
    public Response<Integer> getQueryTaskProgress(@PathVariable("taskId") String taskId) {
        int progress = dismantlingTreeManagerV2.getProgress(taskId);
        return Response.ok(progress);
    }

    @OperateLog
    @PostMapping("/save")
    public Response saveTree(@RequestBody DismantlingConfigTree dismantlingConfigTree) {
        dismantlingTreeManagerV2.assemble(dismantlingConfigTree);
        DismantlingConfigTree vo = dismantlingTreeManagerV2.save(dismantlingConfigTree);
        return Response.ok(vo);
    }

    @GetMapping("/detail/{id}")
    public Response<DismantlingTreeVO> detail(@PathVariable("id") Long id) {
        DismantlingConfigTree detail = dismantlingTreeManagerV2.detail(id);
        return Response.ok(detail);
    }

    @GetMapping("/detail/default/{id}")
    public Response<DismantlingConfigTreeFEDefault> detailDefault(@PathVariable("id") Long id) {
        DismantlingConfigTreeFEDefault detailDefault = dismantlingTreeManagerV2.detailDefault(id);
        return Response.ok(detailDefault);
    }

    @PostMapping("/list")
    public Response<List<DismantlingTreeVO>> listTree(@RequestBody DismantlingTreeQuery query) {
        List<DismantlingTreeVO> trees = dismantlingTreeManagerV2.listTree(query);
        return Response.ok(trees);
    }

    @CheckCacheVersion
    @PostMapping("/list/measure")
    public Response<List<Measure>> listTreeMeasure(@RequestBody DismantlingTreeQuery query) {
        List<Measure> trees = dismantlingTreeManagerV2.listMeasure(query.getSpaceId());
        return Response.ok(trees);
    }

    @OperateLog
    @GetMapping("/delete/{id}")
    public Response<DismantlingConfigTree> deleteTree(@PathVariable("id") Long id) {
        dismantlingTreeManagerV2.delete(id);
        return Response.ok();
    }

    @Autowired
    IDimensionService dimensionService;

    @PostMapping("/create/default/{measCode}")
    public Response<DismantlingTreeVO> defaultCreate(@PathVariable String measCode) {
        try {
            // if (dismantlingTreeManagerV2.hasSomeTree(4, measCode)) {
            //     throw new BusinessWarnException(ResultCode.SUCCESS, "该指标已经存在拆解树");
            // }
            DismantlingConfigTree dismantlingConfigTree = dismantlingTreeManagerV2.buildDismantlingConfigTree(4, measCode);
            if (dismantlingConfigTree == null) {
                throw new BusinessWarnException(ResultCode.SUCCESS, "参数错误");
            }
            dismantlingTreeManagerV2.assemble(dismantlingConfigTree);
            DismantlingConfigTree vo = dismantlingTreeManagerV2.save(dismantlingConfigTree);
            return Response.ok(vo);
        } catch (BusinessWarnException ex) {
            return Response.warn(ex.getMessage());
        } catch (Exception ex) {
            log.error("DismantlingTreeController defaultCreate measCode {} error {}", measCode, ex.getMessage(), ex);
            return Response.error("创建默认拆解树失败");
        }
    }
}

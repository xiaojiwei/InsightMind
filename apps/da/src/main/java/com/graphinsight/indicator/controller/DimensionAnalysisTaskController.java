package com.graphinsight.indicator.controller;


import com.graphinsight.indicator.annotation.IgnoreWebLog;
import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.auto.entity.DimensionAnalysisTask;
import com.graphinsight.indicator.manager.DimensionAnalysisManager;
import com.graphinsight.indicator.manager.DimensionAnalysisManagerV2;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.DimensionAnalysisCreateVO;
import com.graphinsight.indicator.model.vo.DimensionAnalysisDetailVO;
import com.graphinsight.indicator.model.vo.DimensionAnalysisGiniQueryVO;
import com.graphinsight.indicator.model.vo.DimensionAnalysisTaskDetailVO;
import com.graphinsight.indicator.model.vo.DimensionAnalysisTaskQueryVO;
import com.graphinsight.indicator.model.vo.DimensionAnalysisVO;
import com.graphinsight.indicator.model.vo.MultiDimensionQueryVO;
import com.graphinsight.indicator.model.vo.PageVO;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 多维分析查询任务列表 前端控制器
 * </p>
 *
 * @since 2022-07-05
 */
@RestController
@RequestMapping("/dimension/analysis")
public class DimensionAnalysisTaskController {

    @Autowired
    private DimensionAnalysisManager dimensionAnalysisManager;

    @Autowired
    private DimensionAnalysisManagerV2 dimensionAnalysisManagerV2;

    @PostMapping("/multi/dimensional/chart/query")
    public Response queryGini(DimensionAnalysisGiniQueryVO queryVO) {
        List<DimensionAnalysisTaskDetailVO> detailVOS = dimensionAnalysisManagerV2.queryGini(queryVO);
        return Response.ok(detailVOS);
    }

    @GetMapping("/task/detail/meas-code/{measCode}")
    public Response<DimensionAnalysisDetailVO> detail(@PathVariable("measCode") String measCode) {
        DimensionAnalysisDetailVO detailVO = dimensionAnalysisManager.detail(measCode);
        return Response.ok(detailVO);
    }

    @GetMapping("/task/detail/{taskId}")
    public Response<DimensionAnalysisDetailVO> detail(@PathVariable("taskId") Long taskId) {
        DimensionAnalysisDetailVO detailVO = dimensionAnalysisManager.detail(taskId);
        return Response.ok(detailVO);
    }

    @PostMapping("/multi/dimensional/query")
    public Response chartQuery(@RequestBody @Validated MultiDimensionQueryVO multiDimensionQueryVO) {
        PageData pageData = dimensionAnalysisManager.multiDimensionalChartQuery(multiDimensionQueryVO);
        return Response.ok(pageData);
    }


    @OperateLog
    @PostMapping("/task/create")
    public Response createTask(@RequestBody @Validated DimensionAnalysisCreateVO dimensionAnalysisVO) {
        List<DimensionAnalysisTask> dimensionAnalysisTasks = dimensionAnalysisManager.listExistedTask(
                dimensionAnalysisVO.getMeasCode(),
                dimensionAnalysisVO.getDimCode(),
                dimensionAnalysisVO.getBaseDate(),
                dimensionAnalysisVO.getCurrentDate(),
                dimensionAnalysisVO.getSpaceId(),
                UserThreadLocalUtil.getUserName());
        if (!CollectionUtils.isEmpty(dimensionAnalysisTasks)) {
            return Response.ok(dimensionAnalysisManager.detail(dimensionAnalysisTasks.get(0).getId()));
        }
        dimensionAnalysisManager.createTask(dimensionAnalysisVO.getMeasCode(),
                dimensionAnalysisVO.getDimCode(),
                dimensionAnalysisVO.getCurrentDate(),
                dimensionAnalysisVO.getBaseDate(),
                dimensionAnalysisVO.getSpaceId());
        return Response.ok();
    }

    @IgnoreWebLog
    @PostMapping("/task/list")
    public Response<PageVO<DimensionAnalysisVO>> listTask(@RequestBody @Validated DimensionAnalysisTaskQueryVO queryVO) {
        PageVO<DimensionAnalysisVO> pageVO = dimensionAnalysisManager.listTask(queryVO);
        return Response.ok(pageVO);
    }

    @IgnoreWebLog
    @GetMapping("/task/procressing/list/{spaceId}")
    public Response<List<DimensionAnalysisVO>> listProcressingTask(@PathVariable("spaceId") Long spaceId) {
        List<DimensionAnalysisVO> analysisVOS = dimensionAnalysisManager.listProcressingTask(spaceId);
        return Response.ok(analysisVOS);
    }

    @GetMapping("/task/retry/{taskId}")
    public Response retryTask(@PathVariable("taskId") Long taskId) {
        dimensionAnalysisManager.retryTask(taskId);
        return Response.ok();
    }

    @OperateLog
    @GetMapping("/task/cancel/{taskId}")
    public Response cancelTask(@PathVariable("taskId") Long taskId) {
        dimensionAnalysisManager.cancel(taskId);
        return Response.ok();
    }

    @OperateLog
    @GetMapping("/task/delete/{taskId}")
    public Response deleteTask(@PathVariable("taskId") Long taskId) {
        dimensionAnalysisManager.delete(taskId);
        return Response.ok();
    }


    @IgnoreWebLog
    @GetMapping("/task/has/procressing/{spaceId}")
    public Response<Boolean> hasProcressingTask(@PathVariable("spaceId") Long spaceId) {
        return Response.ok(dimensionAnalysisManager.hasProcessingTask(spaceId));
    }

}

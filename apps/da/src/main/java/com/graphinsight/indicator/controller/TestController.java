package com.graphinsight.indicator.controller;


import com.graphinsight.indicator.auto.entity.DimensionAnalysisTask;
import com.graphinsight.indicator.auto.service.IDimensionAnalysisTaskService;
import com.graphinsight.indicator.job.MyTestJob;
import com.graphinsight.indicator.manager.DimensionAnalysisManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.dto.GiniCalculateParam;
import com.graphinsight.indicator.model.vo.DimensionAnalysisVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Author: lixiaolong
 * Date: 2022/3/7
 * Desc:
 */
@RestController
@RequestMapping("/secret")
public class TestController {

    @Autowired
    private DimensionAnalysisManager dimensionAnalysisManager;
    @Autowired
    private IDimensionAnalysisTaskService taskService;

    @PostMapping("/create/analysis/task")
    public Response createTask(@RequestBody DimensionAnalysisVO dimensionAnalysisVO) {

        dimensionAnalysisManager.createTask(dimensionAnalysisVO.getMeasCode(),
                dimensionAnalysisVO.getDimCode(),
                dimensionAnalysisVO.getCurrentDate(),
                dimensionAnalysisVO.getBaseDate(),
                dimensionAnalysisVO.getSpaceId());
        return Response.ok();
    }

    @GetMapping("/execute/analysis/task/{taskId}")
    public Response executeTask(@PathVariable("taskId") Long taskId) {
        DimensionAnalysisTask task = taskService.getById(taskId);
        List<GiniCalculateParam> giniCalculateParams = dimensionAnalysisManager.executeTask(task);
        return Response.ok(giniCalculateParams);
    }

    @Autowired
    MyTestJob myTestJob;
    @GetMapping("/execute/record/{dimMonth}")
    public Response executeRecord(@PathVariable("dimMonth") String dimMonth) throws InterruptedException {
        CompletableFuture.runAsync(()->  {
            try {
                myTestJob.execute(dimMonth);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        return Response.ok();
    }
}

package com.graphinsight.indicator.controller;

import com.graphinsight.indicator.auto.entity.DimensionAnalysisTask;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.auto.service.IDimensionAnalysisTaskService;
import com.graphinsight.indicator.enums.DimensionAnalysisTaskStatusType;
import com.graphinsight.indicator.manager.DimensionAnalysisManager;
import com.graphinsight.indicator.manager.DimensionAnalysisManagerV2;
import com.graphinsight.indicator.manager.MeasureMonitorManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.MultiDimensionQueryVO;
import com.graphinsight.indicator.model.vo.PageDataVO;
import com.graphinsight.indicator.service.DimensionQueryService;
import com.graphinsight.indicator.service.IndicatorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/test")
@Slf4j
public class OnlineTestController {

    @Autowired
    private DimensionQueryService dimensionQueryService;
    @Autowired
    private DimensionAnalysisManager dimensionAnalysisManager;
    @Autowired
    private DimensionAnalysisManagerV2 dimensionAnalysisManager2;
    @Autowired
    private IDimensionAnalysisTaskService taskService;
    @Autowired
    private MeasureMonitorManager measureMonitorManager;

    @GetMapping("/test/monitor/{id}")
    public Response testMonitor(@PathVariable Long id){
        measureMonitorManager.executeMonitor(id);
        return Response.ok();
    }

    @GetMapping("/test/analysis")
    public Response testAnalysis(){
        try {
            Long taskId = 214L;
            long start = System.currentTimeMillis();
            DimensionAnalysisTask analysisTask = taskService.getById(taskId);
            if (analysisTask == null) {
                // 任务不存在或者状态不是初始化，跳过处理
                return Response.ok();
            }
            analysisTask.setStatus(DimensionAnalysisTaskStatusType.PROCESSION.getCode());
            // TODO 打点
            taskService.updateById(analysisTask);
            dimensionAnalysisManager.executeTask(analysisTask);
            MultiDimensionQueryVO query = new MultiDimensionQueryVO();
            query.setMeasCode(analysisTask.getMeasCode());
            Set<String> codes = new HashSet<>();
            codes.add("DIM_8e06fee5b2844ea8b2cd325bbc7b7ee3");
            query.setTaskId(taskId);
            query.setFilterDimCode("DIM_770e69c83103477e95a8eabf97650f5b");
            query.setBaseDate(analysisTask.getCurrentPeriod());
            query.setCurrentDate(analysisTask.getCurrentPeriod());
            query.setSpaceId(analysisTask.getSpaceId());
            query.setColDimCodes(codes);
            PageDataVO dataVO = dimensionAnalysisManager.multiDimensionalChartQuery(query);
            long end = System.currentTimeMillis();
            log.info("执行完成，耗时:{} ms",end - start);
            return Response.ok(dataVO);
        } catch (Exception e) {
            log.error("多维分析任务执行失败:",e);
        }
        return Response.ok();
    }

    @GetMapping("/test")
    public String test() {
        return "onlinetest";
    }

    @Autowired
    IndicatorService indicatorService;
    @Autowired
    UserMapper userMapper;

    @GetMapping("/dimension/info")
    public Response getDimensionInfo(@RequestParam("code") String code){
        return Response.ok(indicatorService.getDimensionTableInfo(code));
    }

    @PostMapping("/dimension/tableInfo")
    public Response getTableInfo(@RequestBody Param param){
        return Response.ok(indicatorService.getIndicatorTableInfo(param.getDimCodeSet(),param.getMeasCodeSet()));
    }



    @GetMapping("/dim/count")
    public Response getCount(@RequestParam("code") String code){
        return Response.ok(dimensionQueryService.getDimCount(code));
    }

}

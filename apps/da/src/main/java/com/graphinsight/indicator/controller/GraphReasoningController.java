package com.graphinsight.indicator.controller;

import com.graphinsight.indicator.graph.GraphReasoningService;
import com.graphinsight.indicator.graph.ReasoningRelationDTO;
import com.graphinsight.indicator.model.Response;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@Api(value = "知识图谱推演查询")
@RestController
@RequestMapping("/api/graph/reasoning")
public class GraphReasoningController {

    @Resource
    private GraphReasoningService graphReasoningService;

    @ApiOperation(value = "查询指标可兼容分析的维度")
    @GetMapping("/measure/{code}/compatible-dimensions")
    public Response<List<ReasoningRelationDTO>> compatibleDimensions(@PathVariable("code") String code) {
        return Response.ok(graphReasoningService.listCompatibleDimensions(code));
    }

    @ApiOperation(value = "查询指标上游依赖指标")
    @GetMapping("/measure/{code}/upstream")
    public Response<List<ReasoningRelationDTO>> upstreamMeasures(@PathVariable("code") String code) {
        return Response.ok(graphReasoningService.listUpstreamMeasures(code));
    }

    @ApiOperation(value = "查询指标下游影响指标")
    @GetMapping("/measure/{code}/downstream")
    public Response<List<ReasoningRelationDTO>> downstreamMeasures(@PathVariable("code") String code) {
        return Response.ok(graphReasoningService.listDownstreamMeasures(code));
    }
}

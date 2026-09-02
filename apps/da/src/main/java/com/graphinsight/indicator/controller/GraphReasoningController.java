package com.graphinsight.indicator.controller;

import com.graphinsight.indicator.graph.GraphReasoningService;
import com.graphinsight.indicator.graph.ReasoningRelationDTO;
import com.graphinsight.indicator.model.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/graph/reasoning")
public class GraphReasoningController {

    @Resource
    private GraphReasoningService graphReasoningService;

    @GetMapping("/measure/{code}/compatible-dimensions")
    public Response<List<ReasoningRelationDTO>> compatibleDimensions(@PathVariable("code") String code) {
        return Response.ok(graphReasoningService.listCompatibleDimensions(code));
    }

    @GetMapping("/measure/{code}/upstream")
    public Response<List<ReasoningRelationDTO>> upstreamMeasures(@PathVariable("code") String code) {
        return Response.ok(graphReasoningService.listUpstreamMeasures(code));
    }

    @GetMapping("/measure/{code}/downstream")
    public Response<List<ReasoningRelationDTO>> downstreamMeasures(@PathVariable("code") String code) {
        return Response.ok(graphReasoningService.listDownstreamMeasures(code));
    }
}

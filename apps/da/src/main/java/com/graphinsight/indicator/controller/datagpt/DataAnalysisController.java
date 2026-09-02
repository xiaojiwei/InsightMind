package com.graphinsight.indicator.controller.datagpt;

import javax.annotation.Resource;

import com.graphinsight.indicator.model.vo.AiGptLimitVo;
import com.graphinsight.indicator.service.AiGptUserService;
import lombok.Getter;
import org.springframework.web.bind.annotation.*;

import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.controller.BaseController;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.AttributionAnalysisRequestVO;
import com.graphinsight.indicator.model.vo.AttributionAnalysisResponseVO;
import com.graphinsight.indicator.service.IDataAnalysisService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(IndicatorConstant.DATA_GPT_AI + "/data-analysis")
public class DataAnalysisController extends BaseController {
    @Resource
    private IDataAnalysisService dataAnalysisService;

    @Resource
    private AiGptUserService aiGptUserService;

    @PostMapping("/attribution/overview")
    public Response<AttributionAnalysisResponseVO> attributionOverview(@RequestBody AttributionAnalysisRequestVO attributionAnalysis) {
        try {
            log.info("DataAnalysisController attributionOverview user : {} input : {}", UserThreadLocalUtil.getUserName(), JSON.toJSONString(attributionAnalysis));

            AttributionAnalysisResponseVO responseVO = dataAnalysisService.attributionOverview(attributionAnalysis);

            log.info("DataAnalysisController attributionOverview user : {} output : {}", UserThreadLocalUtil.getUserName(), JSON.toJSONString(responseVO));

            return Response.ok(responseVO);
        } catch (Exception e) {
            log.info("DataAnalysisController attributionOverview error {}", e.getMessage(), e);
            return Response.error(e.getMessage());
        }
    }

    @PostMapping("/attribution/detail")
    public Response<AttributionAnalysisResponseVO> attributionDetail(@RequestBody AttributionAnalysisRequestVO attributionAnalysis) {
        try {
            log.info("DataAnalysisController attributionDetail user : {} input : {}", UserThreadLocalUtil.getUserName(), JSON.toJSONString(attributionAnalysis));

            AttributionAnalysisResponseVO responseVO = dataAnalysisService.attributionDetail(attributionAnalysis);

            log.info("DataAnalysisController attributionDetail user : {} output : {}", UserThreadLocalUtil.getUserName(), JSON.toJSONString(responseVO));

            return Response.ok(responseVO);
        } catch (Exception e) {
            log.info("DataAnalysisController attributionDetail error {}", e.getMessage(), e);
            return Response.error(e.getMessage());
        }
    }

    @GetMapping("/limit/user")
    public Response<AiGptLimitVo> getLimitUser() {
        AiGptLimitVo responseVO = aiGptUserService.limitUser();
        return Response.ok(responseVO);
    }
}

package com.graphinsight.indicator.controller.datagpt;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.mapper.TSpaceMapper;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.auto.service.ITSpaceService;
import com.graphinsight.indicator.auto.service.ITSuperAdminService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.controller.BaseController;
import com.graphinsight.indicator.controller.DimMeasRelationController;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.CategoryManager;
import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.*;
import com.graphinsight.indicator.service.*;
import com.graphinsight.indicator.service.wordNlp.WordSyntax;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@DS("mysql")
@RestController
@RequestMapping(IndicatorConstant.DATA_GPT_AI_SCREEN)
@Api(tags = "【dataGpt】查询赛选模块接口 ")
public class AiScreenController extends BaseController {

    @Autowired
    UserMapper userMapper;
    @Autowired
    WordSyntax wordSyntax;
    @Autowired
    KeyWord2Service keyWord2Service;

    @Autowired
    ITSuperAdminService itSuperAdminService;
    @Autowired
    TSpaceMapper tSpaceMapper;

    @Autowired
    ITSpaceService itSpaceService;
    @Autowired
    CategoryManager categoryManager;

    @Autowired
    CacheManager cacheManager;
    @Autowired
    DimMeasRelationController dimMeasRelationController;

    @Autowired
    AiWordValueService aiWordValueService;

    @Autowired
    AiBoardInfoService aiBoardInfoService;

    @Autowired
    AiQuestionInfoService aiQuestionInfoService;

    @CheckCacheVersion
    @ApiOperation("根据分类查询维度")
    @PostMapping("/dimension/tree")
    public Response<List<DimensionBaseVO>> listDimensionByCategoryId(@RequestBody MeasureQueryParam query) {
        return dimMeasRelationController.listDimensionByCategoryId(query);
    }

    @CheckCacheVersion
    @ApiOperation("根据分类查询指标")
    @PostMapping("/measure/tree")
    public Response<List<CategoryNodeItem>> listMeasureByCategoryId(@RequestBody MeasureQueryParam query) {
        TSpace tSpace = itSpaceService.getAiSpaceById();
        query.setSpaceId(tSpace.getId());
        Set<Integer> ids = categoryManager.getCateIdBySpaceId(tSpace.getId(), true);
        query.setCategoryIds(ids);
        return dimMeasRelationController.listMeasureByCategoryId(query);
    }

    @CheckCacheVersion
    @PostMapping("/listByCode")
    public Response<RelatedCodeSet> listRelatedSet(@RequestBody RelatedCodeSet relatedCodeSet) {
        TSpace tSpace = itSpaceService.getAiSpaceById();
        relatedCodeSet.setSpaceId(tSpace.getId());
        return dimMeasRelationController.listRelatedSet(relatedCodeSet);
    }

    @ApiOperation("关键字增加")
    @PostMapping("/business/add")
    public Response businessAdd(@RequestBody AiBusinessVo aiBusinessVo) {
        aiWordValueService.addBusiness(aiBusinessVo);
        return Response.ok();
    }

    @ApiOperation("关键字列表")
    @PostMapping("/business/list")
    public Response<IPage<AiBusinessListVo>> businessList(@RequestBody AiBusinessSearchVo aiBusinessSearchVo) {

        return Response.ok(aiWordValueService.listBusiness(aiBusinessSearchVo));
    }

    @ApiOperation("关键字更新")
    @PostMapping("/business/update")
    public Response businessUpdate(@RequestBody AiBusinessVo aiBusinessVo) {

        aiWordValueService.updateBusiness(aiBusinessVo);
        return Response.ok();
    }

    @ApiOperation("关键字删除")
    @PostMapping("/business/del")
    public Response businessDel(@RequestBody AiBusinessDelVo aiBusinessDelVo) {
        aiWordValueService.deleteBusiness(aiBusinessDelVo);
        return Response.ok();
    }


    @ApiOperation("问答记录")
    @PostMapping("/question/list")
    public Response<IPage<AiQuestionInfoVO>> questionList(@RequestBody AiQuestionInfoPageParam aiBusinessSearchVo) {

        return Response.ok(aiQuestionInfoService.page(aiBusinessSearchVo));
    }

    @ApiOperation("问答记录数")
    @PostMapping("/question/count/info")
    public Response<List<AiQuestionCountVO>> questionCountInfo(@RequestBody AiQuestionInfoPageParam aiBusinessSearchVo) {

        return Response.ok(aiQuestionInfoService.getCountInfo(aiBusinessSearchVo));
    }

    @ApiOperation("问答记录备注")
    @PostMapping("/question/note")
    public Response questionNote(@RequestBody AiQuestionInfoVO aiBusinessSearchVo) {
        aiQuestionInfoService.update(aiBusinessSearchVo);
        return Response.ok();
    }

    @ApiOperation("看板增加")
    @PostMapping("/board/add")
    public Response boardAdd(@RequestBody AiBoardInfoVO aiBoardInfoVO) {
        aiBoardInfoService.save(aiBoardInfoVO);
        return Response.ok();
    }

    @ApiOperation("看板修改")
    @PostMapping("/board/update")
    public Response boardUpdate(@RequestBody AiBoardInfoVO aiBoardInfoVO) {
        aiBoardInfoService.update(aiBoardInfoVO);
        return Response.ok();
    }

    @ApiOperation("看板删除")
    @PostMapping("/board/delete")
    public Response boardDelete(@RequestBody AiBusinessDelVo aiBoardDelVO) {
        aiBoardInfoService.delete(aiBoardDelVO);
        return Response.ok();
    }

    @ApiOperation("看板列表")
    @PostMapping("/board/list")
    public Response<IPage<AiBoardInfoVO>> boardList(@RequestBody AiBoardInfoPageParam pageParam) {

        return Response.ok( aiBoardInfoService.page(pageParam));
    }


    @ApiOperation("问题推荐")
    @PostMapping("/subject/recommend")
    public Response<PageVO<RecommendListVo>> subjectRecommend(@RequestBody DataSource dataSource) {
        return Response.ok(aiWordValueService.subjectRecommendInfo(dataSource));
    }


    @PostMapping("/subject/analysis")
    public Response<PageVO<AnalysisListVo>> subjectAnalysis() {
        return Response.ok(aiWordValueService.subjectAnalysis());
    }

    @PostMapping("/subject/input/recommend")
    public Response<PageVO<RecommendListVo>> subjectInputRecommend() {
        return Response.ok(aiWordValueService.subjectInputRecommend());
    }

}

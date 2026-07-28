package com.graphinsight.indicator.controller;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.auto.entity.DecisionTree;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.service.IDecisionTreeDetailService;
import com.graphinsight.indicator.auto.service.IDecisionTreeService;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.DecisionTreeManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.dto.UserContext;
import com.graphinsight.indicator.model.vo.BaseInfo;
import com.graphinsight.indicator.model.vo.DecisionTreeQueryVO;
import com.graphinsight.indicator.model.vo.DecisionTreeVO;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 决策树表 前端控制器
 * </p>
 *
 * @since 2022-06-13
 */
@RestController
@RequestMapping("/decision/tree")
public class DecisionTreeController {


    @Autowired
    DecisionTreeManager decisionTreeManager;
    @Autowired
    IDecisionTreeService decisionTreeService;
    @Autowired
    IDecisionTreeDetailService decisionTreeDetailService;
    @Autowired
    CacheManager cacheManager;
    @Autowired
    UserManager userManager;


    @OperateLog
    @GetMapping("/delete/{id}")
    public Response<List<DecisionTreeVO>> delete(@PathVariable("id") Long id) {
        decisionTreeManager.delete(id);
        return Response.ok();
    }

    @OperateLog
    @PostMapping("/save")
    public Response saveTree(@RequestBody @Validated DecisionTreeVO decisionTreeCreateVO) {
        decisionTreeManager.save(decisionTreeCreateVO);
        return Response.ok();
    }

    @PostMapping("/detail")
    public Response<DecisionTreeVO> detail(@RequestBody DecisionTreeQueryVO decisionTreeQueryVO) {
        DecisionTreeVO detail = decisionTreeManager.detail(decisionTreeQueryVO);
        return Response.ok(detail);
    }

    @GetMapping("/list/{spaceId}/{measCode}")
    public Response<List<DecisionTreeVO>> listByMeasCode(@PathVariable("spaceId") Long spaceId, @PathVariable("measCode") String measCode) {
        List<DecisionTreeVO> decisionTreeVOS = decisionTreeManager.listByMeasCode(measCode, spaceId);
        return Response.ok(decisionTreeVOS);
    }

    @GetMapping("/list/measure/{spaceId}")
    @CheckCacheVersion
    public Response<List<BaseInfo>> listMeasureWithDecisionTree(@PathVariable("spaceId") Long spaceId) {
        List<DecisionTree> decisionTrees = decisionTreeService.list(Wrappers.<DecisionTree>lambdaQuery().eq(DecisionTree::getSpaceId, spaceId));
        if (CollectionUtils.isEmpty(decisionTrees)) {
            return Response.ok(Collections.EMPTY_LIST);
        }
        Map<String, Measure> allMeasureCodeMap = cacheManager.getMetadataCache().getAllMeasureCodeMap();
        UserContext userContext = userManager.getUserContext(spaceId, UserThreadLocalUtil.getUserName());
        final Set<String> authAuthMeasCodes = userContext.getAuthMeasures().stream().filter(Objects::nonNull).map(Measure::getCode).collect(Collectors.toSet());
        List<BaseInfo> baseInfoList = decisionTrees.stream()
                .map(DecisionTree::getMeasCode)
                .distinct()
                .filter(code -> authAuthMeasCodes.contains(code))
                .map(code -> {
                    BaseInfo baseInfo = new BaseInfo();
                    Measure measure = allMeasureCodeMap.get(code);
                    if (Objects.nonNull(measure)) {
                        BeanUtils.copyProperties(measure, baseInfo);
                        return baseInfo;
                    }
                    return null;
                }).filter(baseInfo -> Objects.nonNull(baseInfo)).collect(Collectors.toList());
        return Response.ok(baseInfoList);
    }

}

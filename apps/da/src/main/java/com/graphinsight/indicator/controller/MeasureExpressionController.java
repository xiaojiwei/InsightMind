package com.graphinsight.indicator.controller;

import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.annotation.SyncReloadCache;
import com.graphinsight.indicator.auto.service.IMeasureService;
import com.graphinsight.indicator.enums.MeasureType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.manager.MeasureManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.model.vo.ComplexMeasureBaseVO;
import com.graphinsight.indicator.model.vo.MeasureExpBaseVO;
import com.graphinsight.indicator.model.vo.MeasureExpUpdateVO;
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
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Date: 2022/4/14
 * Desc:
 */
@RestController
@RequestMapping("/measure/exp")
public class MeasureExpressionController {

    @Autowired
    MeasureManager measureManager;
    @Autowired
    IMeasureService measureService;

    @OperateLog
    @CheckCacheVersion
    @GetMapping("/disable/{measAppId}")
    public Response<List<ComplexMeasureBaseVO>> disable(@PathVariable Integer measAppId) {
        List<RelatedResourceDTO> relatedResourceDTOS = measureManager.disabledExp(measAppId);
        if (!CollectionUtils.isEmpty(relatedResourceDTOS)){
            String name = relatedResourceDTOS.stream().map(RelatedResourceDTO::getName).collect(Collectors.joining(","));
            throw IndicatorParamNotValidException.error("模型下线会导致相关资源：" + name + ", 失效，请先解除相关资源引用");
        }
        return Response.ok(relatedResourceDTOS);
    }


    @OperateLog
    @CheckCacheVersion
    @GetMapping("/disable/check/{measAppId}")
    public Response<List<RelatedResourceDTO>> disableCheck(@PathVariable Integer measAppId) {
        List<RelatedResourceDTO> relatedResourceDTOS = measureManager.disabledExpCheck(measAppId);
        return Response.ok(relatedResourceDTOS);
    }

    @OperateLog
    @CheckCacheVersion
    @GetMapping("/enable/{measAppId}")
    public Response enable(@PathVariable Integer measAppId) {
        measureManager.enableExp(measAppId);
        return Response.ok();
    }



    @OperateLog
    @SyncReloadCache
    @PostMapping("/update")
    public Response update(@Validated @RequestBody MeasureExpUpdateVO measureExpBaseVO) {
        checkParm(measureExpBaseVO);
        return measureManager.updateExpression(measureExpBaseVO);
    }

    @OperateLog
    @SyncReloadCache
    @PostMapping("/create")
    public Response create(@Validated @RequestBody MeasureExpBaseVO measureExpBaseVO) {
        checkParm(measureExpBaseVO);
        return measureManager.createExpression(measureExpBaseVO);
    }

    @CheckCacheVersion
    @GetMapping("/list/{id}")
    public Response<List<ComplexMeasureBaseVO>> list(@PathVariable Integer id) {
        return Response.ok(measureManager.getExpressionList(id));
    }

    @OperateLog
    @CheckCacheVersion
    @GetMapping("/delete/{measAppId}")
    public Response<List<ComplexMeasureBaseVO>> delete(@PathVariable Integer measAppId) {
        List<RelatedResourceDTO> relatedResourceDTOS = measureManager.deleteExpression(measAppId);
        return Response.ok(relatedResourceDTOS);
    }


    private void checkParm(MeasureExpBaseVO measureExpBaseVO){
        if (Objects.equals(MeasureType.ORIGIN.getCode(), measureExpBaseVO.getMeasureType())){
            // 原子指标
            if (Objects.isNull(measureExpBaseVO.getSqlAggFunType())){
                throw IndicatorParamNotValidException.error("聚合函数不能为空");
            }
            if (Objects.isNull(measureExpBaseVO.getColumnEnName())){
                throw IndicatorParamNotValidException.error("字段不能为空");
            }
            if (Objects.isNull(measureExpBaseVO.getModelId())){
                throw IndicatorParamNotValidException.error("模型ID不能为空");
            }
        } else if (Objects.equals(MeasureType.DERIVED.getCode(), measureExpBaseVO.getMeasureType())){
            if (CollectionUtils.isEmpty(measureExpBaseVO.getExpressionItemList())){
                throw IndicatorParamNotValidException.error("复合指标表达式不能为空");
            }
        } else if (Objects.equals(MeasureType.EXTENDED.getCode(), measureExpBaseVO.getMeasureType())){
            if (CollectionUtils.isEmpty(measureExpBaseVO.getDimensionFilterList())){
                throw IndicatorParamNotValidException.error("派生指标维度筛选不能为空");
            }
        } else {
            throw IndicatorParamNotValidException.error("指标类型不合法");
        }
    }

}

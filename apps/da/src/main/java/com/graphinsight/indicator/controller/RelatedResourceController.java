package com.graphinsight.indicator.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.auto.mapper.MeasureMapper;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.service.RelatedResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/relatedResource")
public class RelatedResourceController {

    @Autowired
    DimensionMapper dimensionMapper;

    @Autowired
    MeasureMapper measureMapper;

    @Autowired
    RelatedResourceService relatedResourceService;

    @GetMapping("/get/{code}")
    public Response<List<RelatedResourceDTO>> getRelatedResource(@PathVariable("code")  String code){
        boolean isDim = false;
        Object obj = null;
        if (code.startsWith("MEAS")){
            obj = measureMapper.selectOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCode,code));
        }else if (code.startsWith("DIM")){
            obj = dimensionMapper.selectOne(Wrappers.<Dimension>lambdaQuery().eq(Dimension::getCode,code));
            isDim = true;
        }
        if (obj==null)   return Response.error("指标或维度不存在",400,code);
        List<RelatedResourceDTO> list = relatedResourceService.getRelatedResource(code,isDim);
        return Response.ok(list);
    }

}

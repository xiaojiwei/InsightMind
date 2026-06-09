package com.graphinsight.indicator.openapi;


import com.graphinsight.indicator.controller.DimMeasRelationController;
import com.graphinsight.indicator.model.DimAllValues;
import com.graphinsight.indicator.model.Dimension;
import com.graphinsight.indicator.model.Measure;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.RelatedCodeSet;
import com.graphinsight.indicator.openapi.dto.DimAndValuesDTO;
import com.graphinsight.indicator.openapi.dto.DimensionDTO;
import com.graphinsight.indicator.openapi.dto.MeasureDTO;
import com.graphinsight.indicator.openapi.dto.RelationCodeDTO;
import com.graphinsight.indicator.openapi.vo.RelationCodeVO;
import com.graphinsight.indicator.service.IndicatorService;
import com.graphinsight.indicator.service.impl.BuildSqlServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.codecs.MySQLCodec;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Api(value = "获取执行多维数据查询的系统元数据")
@RestController
@RequestMapping("/ai")
public class ApiForAiController {

    @Autowired
    private IndicatorService indicatorService;

    @Autowired
    DimMeasRelationController dimMeasRelationController;

    @PersistenceContext
    private EntityManager entityManager;

    @ApiOperation(value = "获取系统内所有的指标信息",notes = "")
    @GetMapping("/allMeasure")
    public List<MeasureDTO> allMeasure(){
        List<Measure> allMeasureList = indicatorService.listAllMeasure();
        List<MeasureDTO> list = allMeasureList.stream().map(e->{
            MeasureDTO measureDTO = new MeasureDTO();
            BeanUtils.copyProperties(e,measureDTO);
            return measureDTO;
        }).collect(Collectors.toList());
        return list;
    }

    @ApiOperation(value = "获取系统内所有的维度信息",notes = "")
    @GetMapping("/allDimension")
    public List<DimensionDTO> allDimension(){
        List<Dimension> allDimensionList = indicatorService.listAllDimension();
        List<DimensionDTO> list = allDimensionList.stream().map(e->{
            DimensionDTO dimensionDTO = new DimensionDTO();
            BeanUtils.copyProperties(e,dimensionDTO);
            return dimensionDTO;
        }).collect(Collectors.toList());
        return list;
    }

    @ApiOperation(value = "获取与通过code指定的指标和维度具有血缘关系的指标和维度的code")
    @PostMapping("/relation")
    public RelationCodeDTO relation(@RequestBody RelationCodeVO relationCodeVO){
        RelatedCodeSet set = new RelatedCodeSet();
        set.setMeasureSet(relationCodeVO.getMeasureSet());
        set.setDimensionSet(relationCodeVO.getDimensionSet());
        Response<RelatedCodeSet> response = dimMeasRelationController.listRelatedSet(set);
        RelationCodeDTO relationCodeDTO = new RelationCodeDTO();
        relationCodeDTO.setDimensionSet(response.getData().getDimensionSet());
        relationCodeDTO.setMeasureSet(response.getData().getMeasureSet());
        return relationCodeDTO;
    }

    @ApiOperation(value = "通过维值获取可能所属的维度列表，返回的维值会被转换成系统的维值")
    @GetMapping("/getDimensionsByValue/{value}")
    public List<DimAndValuesDTO> getDimensionsByValue(@ApiParam(value = "维值",required = true) @PathVariable("value") String value){

        value = BuildSqlServiceImpl.formatSqlValue(value);
        String hql = "select dav From DimAllValues as dav where dav.valueText like '%" + value + "%'";
        Query query = this.entityManager.createQuery(hql);
        List<DimAllValues> list = query.getResultList();
        Map<String, DimAndValuesDTO> map = new HashMap<>();

        list.forEach(e->{
            String code = e.getCode();
            String dimValue = e.getValueKey();
            DimAndValuesDTO dimAndValuesDTO = null;
            if (map.containsKey(code)) {
                dimAndValuesDTO = map.get(code);
            }else {
                dimAndValuesDTO = new DimAndValuesDTO();
                map.put(code,dimAndValuesDTO);
            }
            dimAndValuesDTO.getValues().add(dimValue);
        });

        ArrayList<DimAndValuesDTO> res = new ArrayList<>(map.values());
        return res;
    }

}

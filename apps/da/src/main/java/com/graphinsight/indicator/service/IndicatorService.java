package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.Dimension;
import com.graphinsight.indicator.model.IndicatorTuple;
import com.graphinsight.indicator.model.Measure;
import com.graphinsight.indicator.model.dto.AuthDimensionBloodCheckResult;
import com.graphinsight.indicator.model.dto.BaseInfoDTO;
import com.graphinsight.indicator.model.dto.CategoryDTO;
import com.graphinsight.indicator.model.dto.DimensionHistogramRequest;
import com.graphinsight.indicator.model.dto.HistogramInfo;

import java.util.List;
import java.util.Set;

public interface IndicatorService {


    /**
     * 判断指标是否属于某个分类
     * @param measureCode
     * @param categoryCode
     * @return
     */
    Boolean belongToCategory(String measureCode,String categoryCode);

    /**
     * 根据指标或者维度code获取基本信息
     * @param code
     * @return
     */
    BaseInfoDTO getByCode(String code);


    List<HistogramInfo> listTableHistogram(Set<String> tableNames);

    List<HistogramInfo> listDimensionHistogram(List<DimensionHistogramRequest> requests);

    /**
     * 获取分类详情
     * @param id
     * @return
     */
    CategoryDTO getCategoryById(Long id);

    /**
     * 获取指标平台下入参维度、指标的所有信息，如关联维度或指标，计算方法、口径、表信息。
     * 入参指标包括基础、复合、衍生、派生指标，此接口会根据指标类型返回所有的依赖指标，如复合指标所包含的原生指标。
     * 入参维度包括退化、标准维，此接口会根据维度类型返回所有所需要的维度信息，如输入次维度，则它本身以及它关联的主维度信息。
     * @param dimensionCodeList
     * @param measureCodeList
     * @return
     */
    IndicatorTuple getIndicatorTableInfo(Set<String> dimensionCodeList, Set<String> measureCodeList);


    /**
     * 判断指标和维度是否有血缘关系
     * @param dimensionCodeList
     * @param measureCodeList
     * @return
     */
    Boolean hasRelation(Set<String> dimensionCodeList, Set<String> measureCodeList);

    /**
     * 获取指标平台下入参维度、指标的所有信息，如关联维度或指标，计算方法、口径、表信息。
     * 入参指标包括基础、复合、衍生、派生指标，此接口会根据指标类型返回所有的依赖指标，如复合指标所包含的原生指标。
     * 入参维度包括退化、标准维，此接口会根据维度类型返回所有所需要的维度信息，如输入次维度，则它本身以及它关联的主维度信息。
     * @param dimensionCodeList
     * @param measureCodeList
     * @param isDetail 是否是明细下钻
     * @return
     */
    IndicatorTuple getIndicatorTableInfo(Set<String> dimensionCodeList, Set<String> measureCodeList, boolean isDetail);


    /**
     * 根据维度code，以及关联维度code获取维度信息。
     * @param dimCode
     * @return
     */
    Dimension getDimensionTableInfo(String dimCode);

    /**
     * 获取所有退化维
     * @return
     */
    List<Dimension> listDegenerateDimension();

    /**
     * 获取所有可用维度
     * @return
     */
    List<Dimension> listAllDimension();


    /**
     * 获取所有可用指标
     * @return
     */
    List<Measure> listMeasureByName(String cnName);


    /**
     * 获取所有可用指标
     * @return
     */
    List<Measure> listAllMeasure();



    /**
     * 判断授权维度集合authDimensionCodes中的每一个维度，与需要查询的指标、维度集合是否有血缘关系
     * @param authDimensionCodes
     * @param dimensionCodes
     * @param measureCodes
     * @return
     */
    List<AuthDimensionBloodCheckResult> checkBloodByAuthDimension(Set<String> authDimensionCodes, Set<String> dimensionCodes, Set<String> measureCodes);


    /**
     * 获取所有可用维度
     * @return
     */
    List<BaseInfoDTO> listDateDimension( Set<String> dimensionCodes, Set<String> measureCodes);


}

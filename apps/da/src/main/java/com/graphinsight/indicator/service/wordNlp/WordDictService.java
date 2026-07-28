package com.graphinsight.indicator.service.wordNlp;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.DimAllValuesInfo;
import com.graphinsight.indicator.auto.mapper.AiBoardInfoMapper;
import com.graphinsight.indicator.auto.mapper.ContentValuesMapper;
import com.graphinsight.indicator.auto.mapper.DimAllValuesMapper;
import com.graphinsight.indicator.auto.mapper.WordValuesMapper;
import com.graphinsight.indicator.constant.CommonConstants;
import com.graphinsight.indicator.auto.entity.AiBoardInfo;
import com.graphinsight.indicator.model.Dimension;
import com.graphinsight.indicator.model.Measure;
import com.graphinsight.indicator.service.IndicatorService;
import org.ansj.splitWord.analysis.DicAnalysis;
import org.nlpcn.commons.lang.tire.domain.Forest;
import org.nlpcn.commons.lang.tire.domain.Value;
import org.nlpcn.commons.lang.tire.library.Library;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@Service
public class WordDictService {

    /*
     * 配置信息初始化，包括：
     * 1、指标维度信息初始化
     * 2、近义词配置信息初始化
     * 3、同义词配置信息初始化
     * 4、配置信息初始化
     *
     *
     */

    @Autowired
    private IndicatorService indicatorService;
    @Autowired
    private WordValuesMapper wordValuesMapper;

    @Autowired
    private ContentValuesMapper contentValuesMapper;

    @Autowired
    DimAllValuesMapper dimAllValuesMapper;

    @Autowired
    AiBoardInfoMapper aiBoardInfoMapper;

    private static List<Value> INIT_VALUSE_LIST = new ArrayList<Value>();

    public static Forest FOREST = null;

    @PostConstruct
    public void init() {
        // 全量指标
        List<Measure> measureList = indicatorService.listAllMeasure();
        for (Measure measure : measureList) {
            //自定义词、词性。此处指标、维度、维度值都定义成名词。
            Value v = new Value(measure.getName().toLowerCase(), "kw", "1000");
            INIT_VALUSE_LIST.add(v);
        }

        // 全量维度
        List<Dimension> dimensionList = indicatorService.listAllDimension();
        for (Dimension dim : dimensionList) {
            if (dim.getName().contains("_")) {
                String name = dim.getName().split("_")[0];
                Value dimN = new Value(name.toLowerCase(), "kw", "1000");
                INIT_VALUSE_LIST.add(dimN);
            }
            Value dimV = new Value(dim.getName().toLowerCase(), "kw", "1000");
            INIT_VALUSE_LIST.add(dimV);
        }

        // 全量维度值
        List<DimAllValuesInfo> dimensionValueList = dimAllValuesMapper.selectAllDimList();
        for (DimAllValuesInfo dimVItem : dimensionValueList) {
            if (dimVItem.getValueFormatText().contains("特斯拉汽车")) {
                String text = dimVItem.getValueFormatText().replace("特斯拉汽车", "");
                Value textV = new Value(text, "kw", "1000");
                INIT_VALUSE_LIST.add(textV);
            }
            Value dimV = new Value(dimVItem.getValueFormatText(), "kw", "1000");
            INIT_VALUSE_LIST.add(dimV);
        }

        List<AiBoardInfo> aiBoardInfoList = aiBoardInfoMapper.selectList(Wrappers.<AiBoardInfo>lambdaQuery()
                .eq(AiBoardInfo::getIsDel, 0));
        aiBoardInfoList.forEach(aiBoardInfoEntity -> {
            INIT_VALUSE_LIST.add(new Value(aiBoardInfoEntity.getBoardName(), "kw", "1000"));
        });
        // 日期字典
        CommonConstants.DateKeyMap.forEach((datek, dateV) -> {
            INIT_VALUSE_LIST.add(new Value(datek, "kw", "1000"));
        });
        // 车型字典
        CommonConstants.CarCodeMap.forEach((carK, carV) -> {
            INIT_VALUSE_LIST.add(new Value(carK, "kw", "1000"));
        });

        wordValuesMapper.selectInfoList().forEach(wordValues -> {
//            INIT_VALUSE_LIST.add(new Value(wordValues.getValue(), "kw", "1000"));
            INIT_VALUSE_LIST.add(new Value(wordValues.getKey(), "kw", "1000"));
            INIT_VALUSE_LIST.add(new Value(wordValues.getValue(), "kw", "1000"));

        });

        // 句式词初始化
        FOREST = Library.makeForest(INIT_VALUSE_LIST);
        DicAnalysis.parse("测试", FOREST);
        // 后续把FOREST返回给其他地方使用即可。

    }

}

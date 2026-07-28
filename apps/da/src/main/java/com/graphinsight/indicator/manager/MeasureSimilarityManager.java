package com.graphinsight.indicator.manager;

import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureSimilarity;
import com.graphinsight.indicator.auto.service.IMeasureSimilarityService;
import com.graphinsight.indicator.model.dto.SimilarityResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.impl.model.GenericItemPreferenceArray;
import org.apache.mahout.cf.taste.impl.similarity.PearsonCorrelationSimilarity;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Date: 2022/12/16
 * Desc:
 */
@Slf4j
@Component
public class MeasureSimilarityManager {

    @Resource
    IMeasureSimilarityService measureSimilarityService;
    @Resource
    CacheManager cacheManager;

    public List<SimilarityResult> similarity(String measCode, Integer topN){
        List<SimilarityResult> results = new ArrayList<>();
        try {
            List<MeasureSimilarity> list = measureSimilarityService.list();
            MeasureSimilarity target = list.stream().filter(m -> Objects.equals(m.getCode(), measCode)).findFirst().orElse(null);
            if (target == null){
                return results;
            }
            Map<Integer, Measure> allMeasureMap = cacheManager.getMetadataCache().getAllMeasureMap();
            UserSimilarity similarity = new PearsonCorrelationSimilarity(buildDataModel(list));
            for (MeasureSimilarity measureSimilarity : list) {
                if (! Objects.equals(measureSimilarity.getCode(), measCode)){
                    double itemSimilarity = similarity.userSimilarity(target.getMeasId().longValue(), measureSimilarity.getMeasId().longValue());
                    Measure measure = allMeasureMap.get(measureSimilarity.getMeasId());
                    if (!Double.isNaN(itemSimilarity) && measure != null){
                        SimilarityResult similarityResult = new SimilarityResult();
                        similarityResult.setCnName(measure.getCnName());
                        similarityResult.setCode(measure.getCode());
                        similarityResult.setR(itemSimilarity);
                        results.add(similarityResult);
                    }
                }
            }
            results = results.stream().filter(result -> result.getR() != Double.NaN).sorted(Comparator.comparing(SimilarityResult::getR).reversed()).collect(Collectors.toList());
            results = results.size() <= topN ?  results : results.subList(0, topN >= results.size() ? results.size() - 1 : topN - 1);
            return results;
        } catch (TasteException e) {
            log.error("相关性系数计算异常:",e);
        }
        return results;
    }

    private GenericDataModel buildDataModel(List<MeasureSimilarity> list){
        FastByIDMap<PreferenceArray> data = new FastByIDMap();
        for (int i = 0; i < list.size(); i++) {
            MeasureSimilarity measureSimilarity = list.get(i);
            long measId = measureSimilarity.getMeasId().longValue();
            List<BigDecimal> decimals = JSON.parseArray(measureSimilarity.getData(), BigDecimal.class);
            GenericItemPreferenceArray itemPreferenceArray = new GenericItemPreferenceArray(decimals.size());
            itemPreferenceArray.setUserID(i,measId);
            for (int j = 0; j < decimals.size(); j++) {
                itemPreferenceArray.setItemID(j,j);
                itemPreferenceArray.setValue(j,decimals.get(j).floatValue());
            }
            data.put(measId,itemPreferenceArray);
        }
        GenericDataModel model = new GenericDataModel(data);
        return model;
    }

    // private List<GenericPreference> getPreferences(Long measId,String data) {
    //     List<GenericPreference> preferences = new ArrayList<>();
    //     List<BigDecimal> decimals = JSON.parseArray(data, BigDecimal.class);
    //     for (int i = 0; i < decimals.size(); i++) {
    //         GenericPreference preference = new GenericPreference(measId,i,decimals.get(i).floatValue());
    //         preferences.add(preference);
    //     }
    //     return preferences;
    // }
}

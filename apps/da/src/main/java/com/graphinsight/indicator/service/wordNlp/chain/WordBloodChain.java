package com.graphinsight.indicator.service.wordNlp.chain;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.manager.BloodManager;
import com.graphinsight.indicator.model.vo.RelatedSet;
import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j

@Component
public class WordBloodChain extends AbstractWordChain {

    /*
     * 根据各个filter最后的结果dataSource，过滤出有效的血缘关系
     *
     */


    @Value("#{'${date.nature.base.code:DIM_0a61b0022ae241e7a400399e97dc1e63,DIM_4e41a99d4b964cc0a66dd7c02356c473,DIM_a15f9bcd0235428fbaf164b584f8055f,DIM_563feabf15084ed2becab6174b90061d,DIM_48efd3166d6f4674a1b62f8aa27e5b04}'.split(',')}")
    private Set<String> dateNatureBaseCode;


    @Override
    protected WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {
        return bloodMark(wordSyntaxVo);
    }

    @Autowired
    private BloodManager bloodManager;
    @Autowired
    DimensionMapper dimensionMapper;

    public WordSyntaxVo bloodMark(WordSyntaxVo wordSyntaxVo) {

        RelatedSet relatedSet = new RelatedSet();

        Set<Integer> measBaseSet = new HashSet<>();
        Set<Integer> dimBaseSet = new HashSet<>();
        Set<Integer> dimValueBaseSet = new HashSet<>();
        Set<Integer> dateBaseSet = new HashSet<>();

        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
            if (!wordItem.getBoolSet().isEmpty()) {
                if (Objects.equals(wordItem.getMatchType(), "measure")) {
                    measBaseSet.addAll(wordItem.getBoolSet());
                } else if (Objects.equals(wordItem.getMatchType(), "dimension")) {
                    if (!dimBaseSet.isEmpty()) {
                        break;
                    }
                    dimBaseSet.addAll(wordItem.getBoolSet());
                } else if (Objects.equals(wordItem.getMatchType(), "dimValue")) {
                    if (!dimBaseSet.isEmpty()) {
                        break;
                    }
                    if (wordItem.getBoolSet().size() == 1) {
                        dimValueBaseSet.addAll(wordItem.getBoolSet());
                    }
                } else if (Objects.equals(wordItem.getMatchType(), "date")) {
                    if (!dimBaseSet.isEmpty()) {
                        break;
                    }
                    dateBaseSet.addAll(wordItem.getBoolSet());
                }
            }
        }

        List<com.graphinsight.indicator.auto.entity.Dimension> dimensionDefaultList = dimensionMapper.selectList(Wrappers.<com.graphinsight.indicator.auto.entity.Dimension>lambdaQuery()
                .in(com.graphinsight.indicator.auto.entity.Dimension::getCode, dateNatureBaseCode));
        Set<Integer> dateNaturBaseSet = dimensionDefaultList.stream().map(com.graphinsight.indicator.auto.entity.Dimension::getId).collect(Collectors.toSet());
        if (!measBaseSet.isEmpty()) {
            relatedSet.getMeasureSet().addAll(measBaseSet);
        } else if (!dimBaseSet.isEmpty()) {
            relatedSet.getDimensionSet().addAll(dimBaseSet);
        } else if (!dimValueBaseSet.isEmpty()) {
            relatedSet.getDimensionSet().addAll(dimValueBaseSet);
        } else if (!dateBaseSet.isEmpty()) {
            // 优先使用自然日期
            if (dateBaseSet.retainAll(dateNaturBaseSet)) {
                relatedSet.getDimensionSet().addAll(dateBaseSet);
            } else {
                relatedSet.getDimensionSet().add(dateBaseSet.iterator().next());
            }
        }


        // 获取血缘关系的维度
        RelatedSet resultRelatedSet = bloodManager.listRelatedSet(relatedSet);

        // 获取所有维度
        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
            if (!wordItem.getBoolSet().isEmpty()) {
                if (!wordItem.getMatchType().equals("measure")) {
                    wordItem.getBoolSet().retainAll(resultRelatedSet.getDimensionSet());
                }

            }
        }

        log.info("intersection: {}", resultRelatedSet.getDimensionSet());
        wordSyntaxVo.setRelatedSet(resultRelatedSet);

        return wordSyntaxVo;

    }


}


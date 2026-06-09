package com.graphinsight.indicator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.vo.AiCollectInfoVo;
import com.graphinsight.indicator.model.vo.AiSplitTextVo;
import com.graphinsight.indicator.model.vo.DataQueryVO;

import java.util.List;

/**
 * CahceService
 */
public interface KeyWord2Service {
    PageData doAction2(DataQueryVO dataQueryVO, String word, Boolean isData);

    PageData doRecommendData(DataSource dataSource);

    List<AiSplitTextVo.Tokens> getSplitWordInfo(AiSplitTextVo aiSplitTextVo);

    PageData doAction(DataQueryVO dataQueryVO);

    void reloadSplit();

    PageData queryNlp(DataSource dataSource);

    PageData queryNlpDetail(DataSource dataSource);

    void buildBaseMap(DataSource dataSource, PageData pageData);

    void recordQuestInfo(String info,DataSource dataSource, PageData pageData);

    PageData queryDetail(DataSource dataSource);
}

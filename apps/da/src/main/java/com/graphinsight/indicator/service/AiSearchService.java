package com.graphinsight.indicator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import com.graphinsight.indicator.model.vo.AiCollectInfoVo;
import com.graphinsight.indicator.model.vo.AiContextInfoVo;
import com.graphinsight.indicator.model.vo.AiSessionUpdateVo;
import com.graphinsight.indicator.model.vo.AiSessionVo;

import java.util.List;

/**
 * CahceService
 */
public interface AiSearchService {

    /**
     * 刷新cache数据
     */
    IPage<AiCollectInfoVo> userCollect(Integer pageNum, Integer limit);

    IPage<AiSearchInfo> userHistory(Integer pageNum, Integer limit);
    IPage<AiSearchInfo> userHistoryList(AiSessionVo aiSessionVo);


    IPage<AiSearchInfo> searchHot(Integer viewType,Integer pageNum, Integer limit);

    void userHistoryDel(Integer seatchId);
    void cancelCollect(Integer seatchId);

     void userCollectOperate(Integer searchId, Integer opType,String contentCode);

    List<AiSearchInfo>  searchInfoRecommend();

    void recordInfo(String word, Integer userId);

    void contextSave(AiContextInfoVo aiContextInfoVo);
}

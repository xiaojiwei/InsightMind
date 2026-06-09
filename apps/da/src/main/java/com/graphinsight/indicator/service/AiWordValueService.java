package com.graphinsight.indicator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.vo.*;

import java.util.List;


public interface AiWordValueService {
     void addBusiness(AiBusinessVo aiBusinessVo);
     void updateBusiness(AiBusinessVo aiBusinessVo);
     void deleteBusiness(AiBusinessDelVo aiBusinessVo);
     IPage<AiBusinessListVo> listBusiness(AiBusinessSearchVo aiBusinessVo);


    PageVO<RecommendListVo> subjectRecommendInfo(DataSource dataSource);
    PageVO<RecommendListVo> subjectRecommend(DataSource dataSource);

    PageVO<RecommendListVo> subjectInputRecommend();

    PageVO<AnalysisListVo>  subjectAnalysis();
}

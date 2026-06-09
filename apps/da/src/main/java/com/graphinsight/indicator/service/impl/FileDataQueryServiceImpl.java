package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.model.BuildSqlTuple;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.QueryResult;
import com.graphinsight.indicator.service.DataQueryService;
import org.springframework.stereotype.Service;

@Service("fileDataQuery")
public class FileDataQueryServiceImpl extends DataQueryService {

    @Override
    public PageData queryData(BuildSqlTuple tuple, PageData pageData) {

        PageData listPageDataVO = pageData;

        boolean isMeasureDetail = tuple.isMeasureDetail();
        QueryResult queryResult = null;
        //指标明细
        if (isMeasureDetail) {
            queryResult = this.baseMeasureDetailFileIndicatorQuery(tuple, listPageDataVO);
        } else {
            queryResult = this.baseFileIndicatorQuery(tuple, listPageDataVO);
        }
        listPageDataVO.setDownloadId(queryResult.getDownloadId());

        return listPageDataVO;

    }
}

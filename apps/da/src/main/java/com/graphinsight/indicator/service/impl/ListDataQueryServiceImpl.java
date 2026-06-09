package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.DataQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service("listDataQuery")
public class ListDataQueryServiceImpl extends DataQueryService {

    @Override
    public PageData queryData(BuildSqlTuple tuple, PageData pageData) {

        //页面选择的维度
        Set<Dimension> choiceDimensionSet = tuple.getChoiceDimensionSet();
        //页面选择的指标
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();

        QueryResult result = super.baseListQuery(tuple, pageData);
        List<List<Cell>> cellTableList = super.buildCell(result.getValues(), choiceDimensionSet, choiceMeasureSet);

        pageData.setCellList(cellTableList);

        return pageData;

    }

}

package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.enums.DataSetType;
import com.graphinsight.indicator.service.DataQueryService;
import com.graphinsight.indicator.service.SqlQueryStrategy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SqlQueryStrategyImpl implements SqlQueryStrategy {

    /*
    @Resource(name = "gradDataQuery")
    private GradDataQueryService gradDataQuery;
    */

    @Resource(name = "listDataQuery")
    private ListDataQueryServiceImpl listDataQuery;

    @Resource(name = "tableDataQuery")
    private TableDataQueryServiceImpl tableDataQuery;

    @Resource(name = "countDataQuery")
    private CountDataQueryServiceImpl countDataQuery;

    @Resource(name = "sqlOnlyQuery")
    private SqlOnlyQueryServiceImpl sqlOnlyQuery;

    @Resource(name = "fileDataQuery")
    private FileDataQueryServiceImpl fileDataQuery;

    @Resource(name = "pivotDataQuery")
    private PivotDataQueryServiceImpl pivotDataQuery;

    @Override
    public DataQueryService getSqlQueryMethod(DataSetType dataSetType) {
        switch (dataSetType) {
//            case CARD:
//                return gradDataQuery;
            case LIST:
                return listDataQuery;
            case TABLE:
                return tableDataQuery;
            case SQL:
                return sqlOnlyQuery;
            case COUNT:
                return countDataQuery;
            case PIVOT:
                return pivotDataQuery;
            case SYNCFILE:
                return fileDataQuery;
            default:
                return null;
        }
    }

}

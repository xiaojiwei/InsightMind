package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.enums.DimType;
import com.graphinsight.indicator.enums.MeasureType;
import com.graphinsight.indicator.enums.SourceType;
import com.graphinsight.indicator.model.Dimension;
import com.graphinsight.indicator.model.IndicatorTuple;
import com.graphinsight.indicator.model.Measure;
import com.graphinsight.indicator.model.Table;
import com.graphinsight.indicator.model.dto.AuthDimensionBloodCheckResult;
import com.graphinsight.indicator.model.dto.BaseInfoDTO;
import com.graphinsight.indicator.model.dto.CategoryDTO;
import com.graphinsight.indicator.model.dto.DimensionHistogramRequest;
import com.graphinsight.indicator.model.dto.HistogramInfo;
import com.graphinsight.indicator.service.IndicatorService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//@Service
//@Qualifier("mockIndicatorService")
public class MockIndicatorServiceImpl implements IndicatorService {

    @Override
    public List<Dimension> listAllDimension() {
        return new ArrayList<>();
    }

    @Override
    public List<Measure> listMeasureByName(String cnName) {
        return null;
    }

    @Override
    public List<Measure> listAllMeasure() {
        return null;
    }

    @Override
    public List<AuthDimensionBloodCheckResult> checkBloodByAuthDimension(Set<String> authDimensionCodes, Set<String> dimensionCodes, Set<String> measureCodes) {
        return Collections.EMPTY_LIST;

    }

    @Override
    public List<BaseInfoDTO> listDateDimension(Set<String> dimensionCodes, Set<String> measureCodes) {
        return null;
    }

    @Override
    public Boolean belongToCategory(String measureCode, String categoryCode) {
        return false;
    }

    @Override
    public BaseInfoDTO getByCode(String code) {
        return null;
    }

    @Override
    public List<HistogramInfo> listTableHistogram(Set<String> tableNames) {
        return null;
    }

    @Override
    public List<HistogramInfo> listDimensionHistogram(List<DimensionHistogramRequest> requests) {
        return null;
    }

    @Override
    public CategoryDTO getCategoryById(Long id) {
        return null;
    }

    @Override
    public IndicatorTuple getIndicatorTableInfo(Set<String> dimensionCodeList, Set<String> measureCodeList) {

        IndicatorTuple indicatorTuple = new IndicatorTuple();

        String SCHEMA = "eps_test";

        /**
         *  事实表 tbl_fact_1
         *  维度表 tbl_dim_1
         */
        Measure measure1 = new Measure();
        measure1.setCode("meas_1");
        measure1.setName("指标1");
        measure1.setAlias("指标别名1");

        Table factTable = new Table();
        factTable.setSourceType(SourceType.DORIS);
        factTable.setSchemaName(SCHEMA);
        factTable.setTableName("employee_channel_sale_stat_day_v1");
        factTable.setFactColumn("leads_cnt");
//        factTable.setWhereCondition("gender=0");
        factTable.setApplyType(MeasureType.ORIGIN);
        factTable.setExpression("[{\"operatingType\":\"operator\",\"operator\":\"sum\"}]");
        //指标关联的事实表
        measure1.setFactTable(Arrays.asList(factTable));

        Dimension dimension1 = new Dimension();
        dimension1.setCode("dim_1");
        dimension1.setName("维度1");
        dimension1.setAlias("维度别名1");
        dimension1.setMaster(true);

        Table factTable2 = new Table();
        factTable2.setSourceType(SourceType.DORIS);
        factTable2.setSchemaName(SCHEMA);
        factTable2.setTableName("employee_channel_sale_stat_day_v1");
        factTable2.setFactColumn("area_id");

        //维度关联事实表
        dimension1.setFactTableList(Arrays.asList(factTable2));

        Table dimTable1 = new Table();
        dimTable1.setSourceType(SourceType.DORIS);
        dimTable1.setSchemaName(SCHEMA);
        dimTable1.setTableName("nr_sales_dim_dept_info");
        dimTable1.setDimPrimaryKey("area_dept_id");
        dimTable1.setDimColumn("area");

        //维度关联维度表
        dimension1.setDimTableList(Arrays.asList(dimTable1));
        dimension1.setDimType(DimType.STD_WITH_TABLE);

        Set<Measure> measureSet = new HashSet<Measure>();
        measureSet.add(measure1);

        Set<Dimension> dimensionSet = new HashSet<Dimension>();
        dimensionSet.add(dimension1);

        //设置维度、指标
        indicatorTuple.setDimensionSet(dimensionSet);
        indicatorTuple.setMeasureSet(measureSet);

        return indicatorTuple;
    }

    @Override
    public Boolean hasRelation(Set<String> dimensionCodeList, Set<String> measureCodeList) {
        return null;
    }

    @Override
    public IndicatorTuple getIndicatorTableInfo(Set<String> dimensionCodeList, Set<String> measureCodeList, boolean isDetail) {
        return null;
    }

    @Override
    public Dimension getDimensionTableInfo(String dimCode) {

        String SCHEMA = "eps_test";

        Dimension dimension1 = new Dimension();
        dimension1.setCode("dim_1");
        dimension1.setName("维度1");
        dimension1.setAlias("维度别名1");
        dimension1.setMaster(true);

        Table factTable2 = new Table();
        factTable2.setSourceType(SourceType.DORIS);
        factTable2.setSchemaName(SCHEMA);
        factTable2.setTableName("employee_channel_sale_stat_day_v1");
        factTable2.setFactColumn("area_id");

        //维度事实表添加
        dimension1.setFactTableList(Arrays.asList(factTable2));

        Table dimTable1 = new Table();
        dimTable1.setSourceType(SourceType.DORIS);
        dimTable1.setSchemaName(SCHEMA);
        dimTable1.setTableName("nr_sales_dim_dept_info");
        dimTable1.setDimPrimaryKey("area_dept_id");
        dimTable1.setDimColumn("area");

        dimension1.setDimTableList(Arrays.asList(dimTable1));
        dimension1.setDimType(DimType.STD_WITH_TABLE);

        Set<Dimension> dimensionSet = new HashSet<Dimension>();
        dimensionSet.add(dimension1);

        return dimension1;


    }

    @Override
    public List<Dimension> listDegenerateDimension() {
        return Collections.EMPTY_LIST;
    }



}

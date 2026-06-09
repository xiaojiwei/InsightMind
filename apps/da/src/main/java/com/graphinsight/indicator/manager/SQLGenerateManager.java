package com.graphinsight.indicator.manager;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureApplication;
import com.graphinsight.indicator.auto.service.IDwTableService;
import com.graphinsight.indicator.auto.service.IMeasureApplicationService;
import com.graphinsight.indicator.auto.service.IMeasureService;
import com.graphinsight.indicator.enums.ItemType;
import com.graphinsight.indicator.enums.MeasureType;
import com.graphinsight.indicator.enums.SqlAggFunType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.dto.SQLGenerateResult;
import com.graphinsight.indicator.model.dto.SingleMeasureRootSql;
import com.graphinsight.indicator.model.vo.ExpressionItem;
import com.graphinsight.indicator.model.vo.MeasureBasicInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * Author: lixiaolong
 * Date: 2022/8/8
 * Desc:
 */
@Component
@Slf4j
public class SQLGenerateManager {

    @Autowired
    CacheManager cacheManager;
    @Autowired
    IMeasureService measureService;
    @Autowired
    IMeasureApplicationService measureApplicationService;
    @Autowired
    IDwTableService dwTableService;

    public SQLGenerateResult generateSqlFromDB(String measCode){
        SQLGenerateResult result = new SQLGenerateResult();
        Measure measure = measureService.getOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCode, measCode));
        if (Objects.isNull(measure)){
            throw IndicatorParamNotValidException.error("指标不存在");
        }

        List<MeasureApplication> measureApplications = measureApplicationService
                .list(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getMeasId, measure.getId()));




        return null;
    }

    public void generateRootSQL(Measure measure, List<MeasureApplication> measureApplications, SQLGenerateResult result){
        if (CollectionUtils.isEmpty(measureApplications)){
            throw IndicatorParamNotValidException.error("指标:" + measure.getCnName() + "code: " + measure.getCode() + "不存在事实表");
        }

        measureApplications.forEach(ma -> {
            Integer applyType = ma.getApplyType();
            if (Objects.equals(applyType,MeasureType.ORIGIN.getCode())){
                // 原子指标

            } else if(Objects.equals(applyType,MeasureType.EXTENDED.getCode())){
                // 派生指标

            } else if(Objects.equals(applyType,MeasureType.DERIVED.getCode())){
                // 复合指标

            }
        });
    }


    /**
     * 原子指标生成逻辑
     * @param measure
     * @param measureApplication
     * @param result
     */
    private void generateOriginMeasureSql(Measure measure,MeasureApplication measureApplication,SQLGenerateResult result){

        SingleMeasureRootSql singleMeasureRootSql = new SingleMeasureRootSql();
        Integer dwTableId = measureApplication.getDwTableId();
        DwTable factTable = dwTableService.getById(dwTableId);
        if (Objects.isNull(factTable)){
            throw IndicatorParamNotValidException.error("指标应用表对应的事实表不存在指标: " + measure.getCnName() + "应用表ID: " + measureApplication.getId());
        }

        singleMeasureRootSql.setAlias(measure.getCnName() + "_" + measure.getCode());
        singleMeasureRootSql.setColumn(measure.getEnName());
        singleMeasureRootSql.setFactTable(factTable);
        String schemaName = factTable.getSchemaName();
        String tableName = factTable.getTableName();
        String expression = measureApplication.getExpression();

        String sql = " select " + singleMeasureRootSql.getColumn() + " as " + singleMeasureRootSql.getAlias() + " from " + schemaName + "." + tableName;
        singleMeasureRootSql.setRootSql(sql);
        result.getSingleMeasureRootSqlLinkedList().add(singleMeasureRootSql);
    }


    private void parseExpression(String expression, Measure measure, DwTable factTable){
        if (! StringUtils.hasLength(expression)){
            throw IndicatorParamNotValidException.error("指标表达式为空");
        }

        String selectContext = " ";

        List<ExpressionItem> expressionItems = JSON.parseArray(expression, ExpressionItem.class);
        for (ExpressionItem item : expressionItems) {
            String operatingType = item.getOperatingType();
            if (ItemType.OPERATOR.getName().equalsIgnoreCase(operatingType)){
                SqlAggFunType sqlAggFunType = SqlAggFunType.valueOfDesc(operatingType.toLowerCase());
                if (Objects.nonNull(sqlAggFunType)){

                } else {

                }
                selectContext += item.getOperator() + " ";
            } else if(ItemType.CONSTANT.getName().equalsIgnoreCase(operatingType)){
                selectContext += item.getConstant() + " ";
            } else if(ItemType.OPERAND.getName().equalsIgnoreCase(operatingType)){
                MeasureBasicInfoVO measureBasicInfoVO = item.getOperand();
                Integer subMeasId = measureBasicInfoVO.getId();
                MeasureApplication subMeasureApplication = getOneApplication(subMeasId);
            }
        }
    }

    private MeasureApplication getOneApplication(Integer measureId){
        Measure subMeasure = measureService.getById(measureId);
        List<MeasureApplication> applications = measureApplicationService.list(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getMeasId, measureId));
        if (CollectionUtils.isEmpty(applications)){
            throw IndicatorParamNotValidException.error("指标不存在对应的事实表,Measure:" + subMeasure.getCnName());
        }
        //TODO 有多个事实表只取一个
        MeasureApplication measureApplication = applications.get(0);
        return measureApplication;
    }

    /**
     * 复合指标生成逻辑
     * @param measure
     * @param measureApplication
     * @param result
     */
    private void generateDerivedMeasureSql(Measure measure,MeasureApplication measureApplication,SQLGenerateResult result){

        SingleMeasureRootSql singleMeasureRootSql = new SingleMeasureRootSql();
        Integer dwTableId = measureApplication.getDwTableId();
        DwTable factTable = dwTableService.getById(dwTableId);
        if (Objects.isNull(factTable)){
            throw IndicatorParamNotValidException.error("指标应用表对应的事实表不存在指标: " + measure.getCnName() + "应用表ID: " + measureApplication.getId());
        }

        singleMeasureRootSql.setAlias(measure.getCnName() + "_" + measure.getCode());
        singleMeasureRootSql.setColumn(measure.getEnName());
        singleMeasureRootSql.setFactTable(factTable);
        String schemaName = factTable.getSchemaName();
        String tableName = factTable.getTableName();

        String sql = " select " + singleMeasureRootSql.getColumn() + " as " + singleMeasureRootSql.getAlias() + " from " + schemaName + "." + tableName;
        singleMeasureRootSql.setRootSql(sql);
        result.getSingleMeasureRootSqlLinkedList().add(singleMeasureRootSql);
    }
























































}

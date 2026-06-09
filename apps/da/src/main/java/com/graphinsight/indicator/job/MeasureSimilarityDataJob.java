package com.graphinsight.indicator.job;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Sets;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureSimilarity;
import com.graphinsight.indicator.auto.service.IDimensionService;
import com.graphinsight.indicator.auto.service.IMeasureSimilarityService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.RatioType;
import com.graphinsight.indicator.enums.SqlOprType;
import com.graphinsight.indicator.enums.TimeRange;
import com.graphinsight.indicator.manager.BloodManager;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.DorisQueryManager;
import com.graphinsight.indicator.model.Cell;
import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.Filter;
import com.graphinsight.indicator.model.Operator;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.Ratio;
import com.graphinsight.indicator.model.vo.RelatedSet;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.util.NumberFormatUtil;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/12/15
 * Desc:
 */
@Component
public class MeasureSimilarityDataJob {

    @Resource
    CacheManager cacheManager;
    @Resource
    BloodManager bloodManager;
    @Resource
    IDimensionService dimensionService;
    @Resource
    DorisQueryManager dorisQueryManager;
    @Resource
    ChartQueryService chartQueryService;
    @Resource
    IMeasureSimilarityService measureSimilarityService;



    @Transactional(rollbackFor = Exception.class)
    public void runData() {
        Dimension dimension = dimensionService.getOne(Wrappers.<Dimension>lambdaQuery().eq(Dimension::getEnName, IndicatorConstant.INDICATOR_NATUAL_DIM_DAY));
        if (Objects.nonNull(dimension)) {
            RelatedSet relatedSet = new RelatedSet();
            relatedSet.setFilterWithRelyDimensions(true);
            relatedSet.setDimensionSet(Sets.newHashSet(Arrays.asList(dimension.getId())));
            relatedSet = bloodManager.listRelatedSet(relatedSet);
            Map<Integer, Measure> measureMap = cacheManager.getMetadataCache().getAllMeasureMap();
            if (!CollectionUtils.isEmpty(relatedSet.getMeasureSet())) {
                Set<Integer> ids = relatedSet.getMeasureSet();
                List<Measure> measures = ids.stream().map(id -> measureMap.get(id)).collect(Collectors.toList());
                save(measures,dimension);
            }
        }
    }

    private void save(List<Measure> measures, Dimension dimension) {
        HashSet<String> dimCodes = Sets.newHashSet(Arrays.asList(dimension.getCode()));
        Set<String> measCodes = measures.stream().map(Measure::getCode).collect(Collectors.toSet());
        Filter filter = lastYearFilter(dimension);
        DataSource dataSource = dorisQueryManager.buildDataSource(23L, measCodes, dimCodes, getRatio(), Arrays.asList(lastYearFilter(dimension)));
        PageData pageData = chartQueryService.execQuery(dataSource);
        List<MeasureSimilarity> similarities = convert(pageData, measures, dimension);
        measureSimilarityService.remove(null);
        measureSimilarityService.saveBatch(similarities);
    }

    private List<MeasureSimilarity> convert(PageData pageData, List<Measure> measures, Dimension dimension) {
        List<MeasureSimilarity> result = new ArrayList<>();
        List<List<Cell>> cellList = pageData.getCellList();
        String startDate = DateTime.now().plusYears(-1).toString("yyyy-MM-dd");
        String endDate = DateTime.now().toString("yyyy-MM-dd");
        measures.forEach(measure -> {
            List<MeasureSimilarityTemp> measureSimilarityTemps = getTemp(measure, cellList, dimension);
            List<BigDecimal> data = measureSimilarityTemps.stream().sorted(Comparator.comparing(MeasureSimilarityTemp::getDate)).map(MeasureSimilarityTemp::getData).map(d -> d.setScale(4, BigDecimal.ROUND_DOWN)).collect(Collectors.toList());
            MeasureSimilarity measureSimilarity = new MeasureSimilarity();
            measureSimilarity.setData(JSON.toJSONString(data));
            measureSimilarity.setCode(measure.getCode());
            measureSimilarity.setMeasId(measure.getId());
            measureSimilarity.setStartTime(startDate);
            measureSimilarity.setEntTime(endDate);
            result.add(measureSimilarity);
        });
        return result;
    }

    private List<MeasureSimilarityTemp> getTemp(Measure measure, List<List<Cell>> cellList, Dimension dimension){
        List<MeasureSimilarityTemp> result = new LinkedList<>();
        cellList.forEach(list -> {
            MeasureSimilarityTemp temp = new MeasureSimilarityTemp();
            Cell measCell = list.stream().filter(cell -> Objects.equals(cell.getCode(), measure.getCode())).findFirst().orElse(defaultCell(measure.getCode()));
            Cell dimCell = list.stream().filter(cell -> Objects.equals(cell.getCode(), dimension.getCode())).findFirst().orElse(defaultCell(dimension.getCode()));
            temp.setData(NumberFormatUtil.formatExceptionWithZero(measCell.getData()));
            temp.setDate(dimCell.getData());
            result.add(temp);
        });
        return result;
    }

    private Cell defaultCell(String code){
        Cell cell = new Cell();
        cell.setCode(code);
        cell.setData("0");
        return cell;
    }

    private Ratio getRatio() {
        Ratio ratio = new Ratio();
        ratio.setRatioType(RatioType.MONTHONMONTH);
        return ratio;
    }

    private Filter lastYearFilter(Dimension dimension) {
        Filter filter = new Filter();
        filter.setCode(dimension.getCode());
        List<Operator> operatorList = new LinkedList<>();
        Operator operator = new Operator();
        operator.setTimeRange(TimeRange.DATE);
        String startDate = DateTime.now().plusYears(-1).toString("yyyy-MM-dd");
        String endDate = DateTime.now().toString("yyyy-MM-dd");
        operator.setSqlOprType(SqlOprType.BETEEN);
        operator.setDataList(Arrays.asList(startDate, endDate));
        operatorList.add(operator);
        filter.setInternal(true);
        filter.setOperatorList(operatorList);
        return filter;
    }


}

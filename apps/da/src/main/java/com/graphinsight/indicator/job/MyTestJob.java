package com.graphinsight.indicator.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.MeasureDateRecode;
import com.graphinsight.indicator.auto.entity.MeasureRelateRecode;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.MeasureDateRecodeMapper;
import com.graphinsight.indicator.auto.mapper.MeasureMapper;
import com.graphinsight.indicator.auto.mapper.MeasureRelateRecodeMapper;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.constant.CommonConstants;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.controller.DimMeasRelationController;
import com.graphinsight.indicator.enums.SortType;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.vo.RelatedCodeSet;
import com.graphinsight.indicator.openapi.dto.MeasureDTO;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.IndicatorService;
import com.graphinsight.indicator.service.impl.KeyWord2ServiceImpl;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.Wrapper;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/10/9
 * Desc:
 */
@Component
@Slf4j
public class MyTestJob {

    @Autowired
    private IndicatorService indicatorService;

    @Autowired
    private KeyWord2ServiceImpl keyWord2Service;

    @Autowired
    private DimMeasRelationController dimMeasRelationController;


    @Autowired
    UserMapper userMapper;

    @Autowired
    MeasureMapper measureMapper;

    @Autowired
    MeasureRelateRecodeMapper measureRelateRecodeMapper;

    @Autowired
    MeasureDateRecodeMapper measureDateRecodeMapper;


    @Autowired
    private ChartQueryService chartQueryService;


    public static final List<String> dateDefaultList = new ArrayList<String>(Arrays.asList("DIM_4e41a99d4b964cc0a66dd7c02356c473", "DIM_0a61b0022ae241e7a400399e97dc1e63", "DIM_a15f9bcd0235428fbaf164b584f8055f"));


   // public static final List<String> dateDefaultListCW = new ArrayList<String>(Arrays.asList("DIM_7093ed52c6034e7bbb21d37cce1437d5"));
//    @Scheduled(cron = "0 51 18 * * ?")
    public void execute(String dimMonth) throws InterruptedException {

        log.info("init data start");
        measureDateRecodeMapper.delete(null);
        execMeasuerDate( dimMonth);
        log.info("init data success");
        measureRelateRecodeMapper.delete(null);
        execPearsonsMeasure();
        log.info("init record success");
//        execInsertTargetTable();
    }

/*
        //execMeasuerDate();
        //Thread.sleep(2000);
       // execPearsonsMeasure();

//        execTruncateTargetTable();
//        execInsertTargetTable();
 */

    @Autowired
    @Qualifier("secondJdbcTemplate")
    private JdbcTemplate defaultJdbcTemplate;

    @Autowired
    @Qualifier("dipJdbcTemplate")
    private JdbcTemplate dipJdbcTemplate;

    public void execTruncateTargetTable() {
       String executeSql = "TRUNCATE table legal_dw.dwd_legal_contract_process_approve_df";
        //String executeSql = "delete from  legal_dw.dwd_legal_contract_process_approve_df where dt='2024-03-26'";
        dipJdbcTemplate.execute(executeSql);
        log.info("execute del Sql ");
    }
   
    public void execInsertTargetTable() {

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        //取今天日期,如果日期类型为String类型,可以使用df.parse()方法,转换为Date类型
        Date date = new Date();
        Calendar calendar = Calendar.getInstance();//new一个Calendar类,把Date放进去
        calendar.setTime(date);
        //此时的日期为明天的日期,要实现昨天,日期应该减二
        calendar.add(Calendar.DATE, -1);

        String executeSql = "insert into legal_dw.dwd_legal_contract_process_approve_df_to_dip  select * from  legal_dw.dwd_legal_contract_process_approve_df where dt = "+df.format(calendar.getTime());
        defaultJdbcTemplate.execute(executeSql);
        log.info("execute insert sql {}",executeSql);
    }



    public void execMeasuerDate(String dimMonth) {
        RelatedCodeSet relatedCodeSet = new RelatedCodeSet();
        List<String> dateDefaultListCW = new ArrayList<String>(Arrays.asList(dimMonth));
        relatedCodeSet.getDimensionSet().addAll(dateDefaultListCW);
        RelatedCodeSet resultSet = dimMeasRelationController.listRelatedSetDemo(relatedCodeSet);
        //  result.getMeasureSet()
        log.info("info is {}",resultSet.getMeasureSet());


        resultSet.getMeasureSet().forEach((measuer) -> {
            try {
                //String measuer = "MEAS_f1a3b9a690fc46768492c435eeec5cee";
                com.graphinsight.indicator.auto.entity.Measure measureInfo = measureMapper.selectByCode(measuer);
                DataSource dataSource = new DataSource();

                BaseConfigure baseConfigure = new BaseConfigure();


                baseConfigure.setCode(measuer);
                // 默认降序
                Order order = new Order();
                order.setSortType(SortType.DEFAULT);
                baseConfigure.setOrder(order);
                dataSource.getConfigureList().add(baseConfigure);

                BaseConfigure baseConfigureDate = new BaseConfigure();
                baseConfigureDate.setCode(dimMonth);
                Order orderDate = new Order();
                orderDate.setSortType(SortType.DESC);
                baseConfigureDate.setOrder(orderDate);

                dataSource.getConfigureList().add(baseConfigureDate);
                dataSource.setUsername("lipengkai");
                PageData pageData = chartQueryService.execQuery(dataSource);


                List<MeasureDateRecode> measureDateRecodeList = new ArrayList<>();
                pageData.getCellList().forEach(cellList -> {
                    MeasureDateRecode measureDateRecode = new MeasureDateRecode();
                    measureDateRecode.setmCode(measureInfo.getCode());
                    measureDateRecode.setmName(measureInfo.getCnName());
                    measureDateRecode.setDateType("month");
                    cellList.forEach(cell -> {
                        if (cell.getCode().contains("MEAS")) {
                            measureDateRecode.setmData(cell.getData());
                        } else if (cell.getCode().contains("DIM")) {
                            measureDateRecode.setDateDesc(cell.getData());
                        }
                    });
                    measureDateRecodeList.add(measureDateRecode);
                });
                if (!measureDateRecodeList.isEmpty()) {
                    measureDateRecodeMapper.saveBatch(measureDateRecodeList);
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("execMeasuerDate 执行异常:", e);
            }

        });


    }


    // 相关性计算
    /*
    Pearson相关性分析：用于分析服从正态分布的定量变量相关性。
    Spearman相关性分析：用于分析定量变量或有序定量变量相关性。
    Kendall’s tau-b相关性分析：用于分析有序定类变量相关性。
    Cochran's Q 检验：用于多组二分类定类数据的一致性检验，即相关程度分析。
    Kappa一致性检验：用于定类数据的一致性检验，即相关程度分析。
    Kendall一致性检验：用于多组定量数据整体的一致性检验，即相关程度分析。
     */

    private void execPearsonsMeasure() {
        /*
            -1.0：完全负相关
            -0.7 到 -0.9：很强的负相关
            -0.5 到 -0.7：强的负相关
            -0.3 到 -0.5：中等程度的负相关
            -0.1 到 -0.3：弱的负相关
            0.0：无相关性
            0.1 到 0.3：弱的正相关
            0.3 到 0.5：中等程度的正相关
            0.5 到 0.7：强的正相关
            0.7 到 0.9：很强的正相关
            1.0：完全正相关
            通常取决于研究的领域和研究的具体背景。在一些领域，一个相关系数超过0.5（无论正负）已经可能被认为是有较强相关性的
         */
//        double[] xData = {1, 2, 3, 4, 5};
//        double[] yData = {5, 4, 2, 4, 5};
        PearsonsCorrelation pearsonsCorrelation = new PearsonsCorrelation();

//        List<Double> x = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
//        List<Double> y = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
//        double[] xData1 = new double[]{};
//        Arrays.fill(xData1, 1.0);
//        double[] xData2 = new double[]{};
//        Arrays.fill(xData2, 1.0);

//        double correlation = pearsonsCorrelation.correlation(xData, yData);
//
//        Wrapper<MeasureDateRecode> measureDateRecodeWrapper = new QueryWrapper<>();

        List<MeasureDateRecode> measureDateRecodeList = measureDateRecodeMapper.selectAllInfo();

        boolean isSame = true;
        int i = 0;
        double[] xData1 = new double[20];
        List<double[]> listData = new ArrayList<>();

        List<String> listCode = new ArrayList<>();
        for (int l = 0; l < measureDateRecodeList.size(); l++) {
            MeasureDateRecode measureDateRecode = measureDateRecodeList.get(l);
            double number = Double.parseDouble(measureDateRecode.getmData());


                if (l + 1 < measureDateRecodeList.size() && null != measureDateRecodeList.get(l + 1)) {
                    if (l + 1 < measureDateRecodeList.size() - 1) {
                        if (Objects.equals(measureDateRecodeList.get(l + 1).getmCode(), measureDateRecodeList.get(l).getmCode())) {
                            xData1[i] = number;
                            i++;
                        } else {
                            listCode.add(measureDateRecode.getmCode());
                            listData.add(xData1);
                            i = 0;
                            xData1 = new double[20];
                        }
                    } else if (l + 1 == measureDateRecodeList.size() - 1) {
                        listCode.add(measureDateRecode.getmCode());
                        xData1[i] = number;
                        listData.add(xData1);
                    }

                }

            }


        log.info("xxx data is {},{},{},{}", listData, listCode,listData.size(),listCode.size());
        //


        String relateType = "no";
        for (int j = 0; j < listData.size(); j++) {
            List<MeasureRelateRecode> measureRelateRecodes = new ArrayList<>();
//        if (Objects.equals(listCode.get(j), "MEAS_6512a9d981a24b92892813a30b8649c2")) {
            for (int k = j + 1; k < listData.size(); k++) {
                MeasureRelateRecode measureRelateRecode = new MeasureRelateRecode();
                double correlationData = pearsonsCorrelation.correlation(listData.get(j), listData.get(k));

                if (correlationData < -0.5 ) {

                    // 负相关
                    relateType = "negative";
                } else if (correlationData > 0.5) {
                    // 正相关
                    relateType = "positive";
                }else {
                    relateType = "no";
                }

                measureRelateRecode.setMData(Double.toString(correlationData));
                measureRelateRecode.setRCode(listCode.get(k));
                measureRelateRecode.setMCode(listCode.get(j));
                measureRelateRecode.setMType(relateType);
                measureRelateRecodes.add(measureRelateRecode);
            }
//        }
            if(measureRelateRecodes.size() > 0){
                log.info("测试数据使用 {}",measureRelateRecodes);
               measureRelateRecodeMapper.saveBatch(measureRelateRecodes);
            }
        }


        //
    }

    private void execTestMeasure() {
        try {
            String value = UUID.randomUUID().toString();
            List noDataMap = new ArrayList<>();
            List errInfo = new ArrayList<>();


            User user = userMapper.selectByUsername("lipengkai");
            UserThreadLocalUtil.set(user);

            List<Measure> allMeasureList = indicatorService.listAllMeasure();
            List<MeasureDTO> listInfo = allMeasureList.stream().map(e -> {
                MeasureDTO measureDTO = new MeasureDTO();
                BeanUtils.copyProperties(e, measureDTO);
                return measureDTO;
            }).collect(Collectors.toList());

            for (MeasureDTO measureDTO : listInfo) {

                try {
                    PageData dataInfo = keyWord2Service.doAction2(null, measureDTO.getName(), true);
                    if (null != dataInfo
                            && null != dataInfo.getCellList()
                            && !dataInfo.getCellList().isEmpty()
                    ) {
                        boolean successFlag = false;
                        for (Cell cell : dataInfo.getCellList().get(0)) {
                            if (cell.getCode().contains("MEAS")) {
                                successFlag = true;
                                log.info("data success info is name -{} - code -{} - data - {} ", cell.getName(), cell.getCode(), cell.getData());
                                break;
                            }
                        }


                        if (!successFlag) {
                            noDataMap.add(dataInfo.getCellList());
                            log.info("data no info {}", dataInfo.getCellList());
                        }

                    }
                } catch (Exception e) {
                    errInfo.add(e.getMessage());
                    log.info("error is info {}", e);
                }
                Thread.sleep(1000);
            }


            log.info("data no list {}", noDataMap);
            log.info("error is list {}", errInfo);
        } catch (Exception e) {
            log.error("UserSyncJob 执行异常:", e);
        }
    }
}

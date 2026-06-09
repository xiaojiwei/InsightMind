package com.graphinsight.indicator.measure;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.entity.MeasureApplication;
import com.graphinsight.indicator.auto.mapper.MeasureApplicationMapper;
import com.graphinsight.indicator.doris.mapper.TablesMapper;
import com.graphinsight.indicator.enums.SqlLogicalType;
import com.graphinsight.indicator.enums.SqlOprType;
import com.graphinsight.indicator.manager.MeasureManager;
import com.graphinsight.indicator.manager.ReferenceManager;
import com.graphinsight.indicator.model.dto.IndicatorBean;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.model.vo.ComplexMeasureCreateVO;
import com.graphinsight.indicator.model.vo.DimensionFilterCreateVO;
import com.graphinsight.indicator.model.vo.DimensionFilterOperatorCreateVO;
import com.graphinsight.indicator.model.vo.ExpressionItem;
import com.graphinsight.indicator.model.vo.MeasureBasicInfoVO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Author: lixiaolong
 * Date: 2022/2/10
 * Desc:
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
@ActiveProfiles("debug")
public class MeasureManagerTest {

    @Autowired
    MeasureManager measureManager;
    @Autowired
    MeasureApplicationMapper measureApplicationMapper;
    @Autowired
    TablesMapper tablesMapper;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    ReferenceManager referenceManager;

    @Test
    public void testReference(){
        IndicatorBean bean = new IndicatorBean();
        bean.setCode("MEAS_9e0af3b5cc9f443985d41859ebfa8fe2");
        List<RelatedResourceDTO> dtos = referenceManager.listRelatedResource(bean);
        System.out.println(dtos);
    }

    @Test
    public void testSqlBuild(){


        DwTable dwTable = new DwTable();
        dwTable.setSchemaName("eps_service");
        dwTable.setTableName("dwd_aftersale_customer_core_requirement_df");
        List<MeasureApplication> measureApplications = measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getDwTableId, 1).eq(MeasureApplication::getApplyType, 0));

        String sql = measureManager.buildeOriginMeasureQuerySql(dwTable, measureApplications);
        System.out.println(sql);
        DynamicDataSourceContextHolder.push("doris");
        jdbcTemplate.execute(sql);

    }


    /**
     * 指标循环依赖校验
     */
    @Test
    public void circularDependencyCheckTest(){
        boolean b = measureManager.circularDependencyCheck(42, Arrays.asList(46));
        System.out.println(b);
    }

    /**
     * 派生指标创建测试
     */
    @Test
    public void extendMeasureCreateTest(){
        ComplexMeasureCreateVO complexMeasureCreateVO = new ComplexMeasureCreateVO();
        // complexMeasureCreateVO.setId(51);
        complexMeasureCreateVO.setCnName("派生指标from[派生指标from复合指标]");
        complexMeasureCreateVO.setEnName(UUID.randomUUID().toString().replaceAll("-",""));
        LinkedList<ExpressionItem> itemList = new LinkedList<>();
        ExpressionItem item1 = new ExpressionItem();
        item1.setOperatingType("operand");
        MeasureBasicInfoVO operand = new  MeasureBasicInfoVO(56,"b3127745b5b74be085ce763ab481ad63","派生指标2_修改3");
        item1.setOperand(operand);
        itemList.add(item1);

        ExpressionItem item2 = new ExpressionItem();
        item2.setOperatingType("operator");
        item2.setOperator("+");
        itemList.add(item2);

        ExpressionItem item3 = new ExpressionItem();
        item3.setOperatingType("constant");
        item3.setConstant(1.0);
        itemList.add(item3);

        complexMeasureCreateVO.setExpressionItemList(itemList);
        LinkedList<DimensionFilterCreateVO> dimensionFilterList = new LinkedList<>();
        DimensionFilterCreateVO dimensionFilterCreateVO = new DimensionFilterCreateVO();
        dimensionFilterCreateVO.setDimCode("DIM_a61262b9b48743fb8e3a984a68898a53");
        dimensionFilterCreateVO.setDimId(5);
        dimensionFilterCreateVO.setSqlLogicalType(SqlLogicalType.AND.getCode());
        LinkedList<DimensionFilterOperatorCreateVO> operatorList = new LinkedList<>();
        DimensionFilterOperatorCreateVO dimensionFilterOperatorCreateVO = new DimensionFilterOperatorCreateVO();
        LinkedList<String> dataList = new LinkedList<>();
        dataList.add("上海_修改22");
        dimensionFilterOperatorCreateVO.setDataList(dataList);
        dimensionFilterOperatorCreateVO.setSqlLogicalType(SqlLogicalType.AND.getCode());
        dimensionFilterOperatorCreateVO.setSqlOprType(SqlOprType.EQUAL.getCode());

        operatorList.add(dimensionFilterOperatorCreateVO);
        dimensionFilterCreateVO.setOperatorList(operatorList);
        dimensionFilterList.add(dimensionFilterCreateVO);
        complexMeasureCreateVO.setDimensionFilterList(dimensionFilterList);
        measureManager.createComplexMeasure(complexMeasureCreateVO);
    }

    @Test
    public void complexMeasureCreateTest(){
        ComplexMeasureCreateVO complexMeasureCreateVO = new ComplexMeasureCreateVO();
        complexMeasureCreateVO.setCnName("复合指标测试1");
        LinkedList<ExpressionItem> itemList = new LinkedList<>();
        ExpressionItem item1 = new ExpressionItem();
        item1.setOperatingType("operand");
        MeasureBasicInfoVO operand = new MeasureBasicInfoVO(1,"testCode","原子指标1");
        item1.setOperand(operand);
        itemList.add(item1);

        ExpressionItem item2 = new ExpressionItem();
        item2.setOperatingType("operator");
        item2.setOperator("+");
        itemList.add(item2);

        ExpressionItem item3 = new ExpressionItem();
        item3.setOperatingType("constant");
        item3.setConstant(1.0);
        itemList.add(item3);

        complexMeasureCreateVO.setExpressionItemList(itemList);
        measureManager.createComplexMeasure(complexMeasureCreateVO);
    }

    @Test
    public void measureAvaliableTest(){
        boolean available = measureManager.available(43);
        System.out.println(available);
    }
}

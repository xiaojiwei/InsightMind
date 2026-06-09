package com.graphinsight.indicator.dimension;

import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.doris.entity.DimWithoutTable;
import com.graphinsight.indicator.doris.mapper.DimWithoutTableMapper;
import com.graphinsight.indicator.manager.BloodManager;
import com.graphinsight.indicator.manager.DimensionManager;
import com.graphinsight.indicator.model.vo.DimensionValueItem;
import com.graphinsight.indicator.model.vo.DimensionValuesCreateVO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Author: lixiaolong
 * Date: 2022/2/15
 * Desc:
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
@ActiveProfiles("debug")
public class DimensionTest {
    @Autowired
    DimWithoutTableMapper dimWithoutTableMapper;
    @Autowired
    DimensionManager dimensionManager;
    @Autowired
    private BloodManager bloodManager;

    @Test
    public void helloworld() {
        List<DimWithoutTable> dimWithoutTables = dimWithoutTableMapper.selectList(null);
        System.out.println(dimWithoutTables);
    }


    @Test
    public void dimensionValueSaveTest() {
        DimensionValuesCreateVO dimensionValuesCreateVO = new DimensionValuesCreateVO();
        dimensionValuesCreateVO.setDimCode("DIM_7c538aa46cf645dbbc598e282555101c");

        DimensionValueItem dimensionValueItem1 = new DimensionValueItem();
        dimensionValueItem1.setDisplayField("北京");
        dimensionValueItem1.setQueryField("beijing");

        DimensionValueItem dimensionValueItem2 = new DimensionValueItem();
        dimensionValueItem2.setDisplayField("上海");
        dimensionValueItem2.setQueryField("shanghai");

        List<DimensionValueItem> dimensionValueItemList = new ArrayList<>();
        dimensionValueItemList.add(dimensionValueItem1);
        dimensionValueItemList.add(dimensionValueItem2);
        dimensionValuesCreateVO.setDimensionValueItemList(dimensionValueItemList);
        dimensionManager.saveDimensionValues(dimensionValuesCreateVO);
    }

    @Test
    public void relatedDimensionTest() {
        long spaceId = 4;
        String measCode = "MEAS_43c0ec09bf884fdd93dabe534fe6ee64";
        String dimCode = "DIM_9ba70287003d4665bf7b8689caf98bc4";
        Set<Dimension> dimensions = bloodManager.listRelatedDimensions(measCode, dimCode, spaceId);
        assert dimensions != null;
    }
}

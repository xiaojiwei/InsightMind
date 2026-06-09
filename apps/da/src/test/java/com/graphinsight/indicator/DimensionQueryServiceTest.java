package com.graphinsight.indicator;

import com.graphinsight.indicator.enums.SqlOprType;
import com.graphinsight.indicator.model.DimensionQueryParam;
import com.graphinsight.indicator.model.Filter;
import com.graphinsight.indicator.model.Operator;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.service.DimensionQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class DimensionQueryServiceTest {

    @Autowired
    private DimensionQueryService dimQueryService;

    @Test
    public void testDimensionQuery() {

        DimensionQueryParam dimQueryParam = new DimensionQueryParam();
        dimQueryParam.setCode("dim_1");
        List<Filter> filterList = new ArrayList<>();
        Filter filter = new Filter();
        Operator operator = new Operator();
        operator.setSqlOprType(SqlOprType.LIKE);
        filter.getOperatorList().add(operator);
        filterList.add(filter);
        PageData pageData = this.dimQueryService.execQueryDimensionValues(dimQueryParam);

    }
}

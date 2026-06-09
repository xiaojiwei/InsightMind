package com.graphinsight.indicator.auto.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.MeasureDateRecode;
import com.graphinsight.indicator.auto.entity.MeasureDateRecodeExample;
import com.graphinsight.indicator.auto.entity.WordInfos;

import java.util.List;

public interface MeasureDateRecodeMapper extends BaseMapper<MeasureDateRecode> {

    void saveBatch(List<MeasureDateRecode> measureDateRecodeList);

    List<MeasureDateRecode> selectAllInfo();
}
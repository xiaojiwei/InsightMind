package com.graphinsight.indicator.auto.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.MeasureRelateRecode;

import java.util.List;
import java.util.Set;

public interface MeasureRelateRecodeMapper extends BaseMapper<MeasureRelateRecode> {
    void saveBatch(List<MeasureRelateRecode> measureDateRecodeList);
    List<MeasureRelateRecode> relateRecodeInfo(String mCode, Set<String> rCodeSet);
}
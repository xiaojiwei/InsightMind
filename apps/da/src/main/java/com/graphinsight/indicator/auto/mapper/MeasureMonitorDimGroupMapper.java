package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.MeasureMonitorDimGroup;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;
@DS("mysql")
public interface MeasureMonitorDimGroupMapper extends BaseMapper<MeasureMonitorDimGroup> {


}

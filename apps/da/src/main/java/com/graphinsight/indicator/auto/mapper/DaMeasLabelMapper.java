package com.graphinsight.indicator.auto.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.DaMeasLabel;
import com.graphinsight.indicator.model.dto.MeasLabelGroupDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;


public interface DaMeasLabelMapper extends BaseMapper<DaMeasLabel> {
    List<MeasLabelGroupDTO> getRelateMeasGroupLabel(@Param("measIds") List<Integer> authMeasureIds);

    List<MeasLabelGroupDTO> getRelateMeasByLabel(@Param("labelIds") List<Long> labelId);
}
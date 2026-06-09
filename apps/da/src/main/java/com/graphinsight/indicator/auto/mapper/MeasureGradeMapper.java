package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.MeasureGrade;

/**
 * <p>
 * 指标等级字典表 Mapper 接口
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@DS("mysql")
public interface MeasureGradeMapper extends BaseMapper<MeasureGrade> {

}

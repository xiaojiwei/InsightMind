package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.MeasureGrade;

/**
 * <p>
 * 指标等级字典表 服务类
 * </p>
 *
 * @since 2021-11-16
 */
@DS("mysql")
public interface IMeasureGradeService extends IService<MeasureGrade> {

}

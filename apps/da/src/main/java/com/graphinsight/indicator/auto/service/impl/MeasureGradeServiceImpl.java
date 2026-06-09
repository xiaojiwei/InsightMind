package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.MeasureGrade;
import com.graphinsight.indicator.auto.mapper.MeasureGradeMapper;
import com.graphinsight.indicator.auto.service.IMeasureGradeService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 指标等级字典表 服务实现类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Service
@DS("mysql")
public class MeasureGradeServiceImpl extends ServiceImpl<MeasureGradeMapper, MeasureGrade> implements IMeasureGradeService {

}

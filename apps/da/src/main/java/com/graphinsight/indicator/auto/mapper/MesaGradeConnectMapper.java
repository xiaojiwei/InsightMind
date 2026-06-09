package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.MesaGradeConnect;

/**
 * <p>
 * 指标等级关联表 Mapper 接口
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@DS("mysql")
public interface MesaGradeConnectMapper extends BaseMapper<MesaGradeConnect> {

}

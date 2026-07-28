package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.DwTable;

/**
 * <p>
 * 数仓物理表 Mapper 接口
 * </p>
 *
 * @since 2021-11-16
 */
@DS("mysql")
public interface DwTableMapper extends BaseMapper<DwTable> {

}

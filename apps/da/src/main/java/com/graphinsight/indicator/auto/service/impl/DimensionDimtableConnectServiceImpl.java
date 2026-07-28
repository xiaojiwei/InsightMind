package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.DimensionDimtableConnect;
import com.graphinsight.indicator.auto.mapper.DimensionDimtableConnectMapper;
import com.graphinsight.indicator.auto.service.IDimensionDimtableConnectService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 维度和维表的关联表 服务实现类
 * </p>
 *
 * @since 2021-11-18
 */
@Service
@DS("mysql")
public class DimensionDimtableConnectServiceImpl extends ServiceImpl<DimensionDimtableConnectMapper, DimensionDimtableConnect> implements IDimensionDimtableConnectService {

}

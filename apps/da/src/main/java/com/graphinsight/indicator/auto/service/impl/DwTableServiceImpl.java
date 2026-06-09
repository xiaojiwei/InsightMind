package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.mapper.DwTableMapper;
import com.graphinsight.indicator.auto.service.IDwTableService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 数仓物理表 服务实现类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Service
@DS("mysql")
public class DwTableServiceImpl extends ServiceImpl<DwTableMapper, DwTable> implements IDwTableService {

}

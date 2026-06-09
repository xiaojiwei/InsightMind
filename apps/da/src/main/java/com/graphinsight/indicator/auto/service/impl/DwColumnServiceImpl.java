package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.DwColumn;
import com.graphinsight.indicator.auto.mapper.DwColumnMapper;
import com.graphinsight.indicator.auto.service.IDwColumnService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 数仓物理表的列信息 服务实现类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Service
@DS("mysql")
public class DwColumnServiceImpl extends ServiceImpl<DwColumnMapper, DwColumn> implements IDwColumnService {

}

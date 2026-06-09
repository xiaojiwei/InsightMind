package com.graphinsight.indicator.doris.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.doris.entity.Schemata;
import com.graphinsight.indicator.doris.entity.Tables;
import com.graphinsight.indicator.doris.mapper.SchemataMapper;
import com.graphinsight.indicator.doris.mapper.TablesMapper;
import com.graphinsight.indicator.doris.service.ISchemataService;
import com.graphinsight.indicator.doris.service.ITablesService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * SCHEMA 服务实现类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-17
 */
@Service
public class SchemataServiceImpl extends ServiceImpl<SchemataMapper, Schemata> implements ISchemataService {

}

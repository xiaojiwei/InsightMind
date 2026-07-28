package com.graphinsight.indicator.doris.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.doris.entity.Columns;
import com.graphinsight.indicator.doris.mapper.ColumnsMapper;
import com.graphinsight.indicator.doris.service.IColumnsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * SCHEMA 服务实现类
 * </p>
 *
 * @since 2021-11-17
 */
@Service
public class ColumnsServiceImpl extends ServiceImpl<ColumnsMapper, Columns> implements IColumnsService {

}

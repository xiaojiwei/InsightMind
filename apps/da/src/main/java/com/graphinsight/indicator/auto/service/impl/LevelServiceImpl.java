package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.Level;
import com.graphinsight.indicator.auto.mapper.LevelMapper;
import com.graphinsight.indicator.auto.service.ILevelService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-01-28
 */
@Service
@DS("mysql")
public class LevelServiceImpl extends ServiceImpl<LevelMapper, Level> implements ILevelService {

}

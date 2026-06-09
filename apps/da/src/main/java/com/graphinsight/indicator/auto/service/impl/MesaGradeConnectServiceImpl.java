package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.MesaGradeConnect;
import com.graphinsight.indicator.auto.mapper.MesaGradeConnectMapper;
import com.graphinsight.indicator.auto.service.IMesaGradeConnectService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 指标等级关联表 服务实现类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Service
@DS("mysql")
public class MesaGradeConnectServiceImpl extends ServiceImpl<MesaGradeConnectMapper, MesaGradeConnect> implements IMesaGradeConnectService {

}

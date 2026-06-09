package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.MesaGradeConnect;

/**
 * <p>
 * 指标等级关联表 服务类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@DS("mysql")
public interface IMesaGradeConnectService extends IService<MesaGradeConnect> {

}

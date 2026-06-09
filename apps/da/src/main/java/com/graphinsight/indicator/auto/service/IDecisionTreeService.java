package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.DecisionTree;

/**
 * <p>
 * 决策树表 服务类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-06-13
 */
@DS("mysql")
public interface IDecisionTreeService extends IService<DecisionTree> {

}

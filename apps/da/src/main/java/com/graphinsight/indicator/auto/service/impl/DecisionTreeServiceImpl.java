package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.DecisionTree;
import com.graphinsight.indicator.auto.mapper.DecisionTreeMapper;
import com.graphinsight.indicator.auto.service.IDecisionTreeService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 决策树表 服务实现类
 * </p>
 *
 * @since 2022-06-13
 */
@Service
public class DecisionTreeServiceImpl extends ServiceImpl<DecisionTreeMapper, DecisionTree> implements IDecisionTreeService {

}

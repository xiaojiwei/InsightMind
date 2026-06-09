package com.graphinsight.indicator.auto.service.impl;

import com.graphinsight.indicator.auto.entity.Classification;
import com.graphinsight.indicator.auto.mapper.ClassificationMapper;
import com.graphinsight.indicator.auto.service.IClassificationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 指标分类 服务实现类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-05-12
 */
@Service
public class ClassificationServiceImpl extends ServiceImpl<ClassificationMapper, Classification> implements IClassificationService {

}

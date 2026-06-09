package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.Source;
import com.graphinsight.indicator.auto.mapper.SourceMapper;
import com.graphinsight.indicator.auto.service.ISourceService;
import org.springframework.stereotype.Service;

@Service
public class SourceServiceImpl extends ServiceImpl<SourceMapper, Source> implements ISourceService {
}

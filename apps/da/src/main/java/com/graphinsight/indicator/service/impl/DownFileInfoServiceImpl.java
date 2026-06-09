package com.graphinsight.indicator.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.dao.DownFileInfoDao;
import com.graphinsight.indicator.model.DownFileInfo;
import com.graphinsight.indicator.service.DownFileInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@DS("mysql")
@Service
public class DownFileInfoServiceImpl implements DownFileInfoService {

    @Autowired
    private DownFileInfoDao downFileInfoDao;

    @Override
    public Long save(DownFileInfo downFileInfo) {

        DownFileInfo saveInfo = downFileInfoDao.save(downFileInfo);
        return saveInfo.getId();

    }
}

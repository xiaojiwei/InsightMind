package com.graphinsight.indicator.service;

import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.model.*;

import java.util.List;

/**
 * 指标、维度授权接口
 */
public interface DownFileInfoService {

    /**
     * 指标维度授权持久化
     * @param downFileInfo
     * @return
     */
    Long save(DownFileInfo downFileInfo);

}

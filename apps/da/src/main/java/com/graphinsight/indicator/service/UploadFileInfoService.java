package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.DownFileInfo;
import com.graphinsight.indicator.model.FileDownInfo;

/**
 * 指标、维度授权接口
 */
public interface UploadFileInfoService {

    /**
     * 指标维度授权持久化
     * @param downFileInfo
     * @return
     */
    Integer save(FileDownInfo downFileInfo);

}

package com.graphinsight.indicator.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.auto.entity.UploadFile;
import com.graphinsight.indicator.auto.mapper.UploadFileMapper;
import com.graphinsight.indicator.model.FileDownInfo;
import com.graphinsight.indicator.service.UploadFileInfoService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@DS("mysql")
@Service
public class UploadFileInfoServiceImpl implements UploadFileInfoService {
    @Autowired
    UploadFileMapper uploadFileMapper;

    @Override
    public Integer save(FileDownInfo downFileInfo) {

        List<UploadFile> uploadFiles = uploadFileMapper.selectKeyList(downFileInfo.getDownloadId());
        if (!uploadFiles.isEmpty()) {
            return uploadFiles.get(0).getId();
        }

        if( null == downFileInfo.getFileKey() || Objects.equals(downFileInfo.getFileKey(), "") ){
            return 0;
        }
        UploadFile uploadFile = new UploadFile();
        uploadFile.setDataId(downFileInfo.getDownloadId());
        uploadFile.setFileKey(downFileInfo.getFileKey());
        uploadFile.setCreator(UserThreadLocalUtil.getUserName());
        uploadFile.setUpdater(UserThreadLocalUtil.getUserName());
        uploadFile.setCreateDate(LocalDateTime.now());
        uploadFile.setUpdateDate(LocalDateTime.now());
        uploadFileMapper.insert(uploadFile);
        return uploadFile.getId();

    }
}

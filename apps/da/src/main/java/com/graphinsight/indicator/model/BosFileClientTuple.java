package com.graphinsight.indicator.model;

import com.baidubce.services.bos.BosClient;
import lombok.Data;

@Data
public class BosFileClientTuple {

    private BosClient bosClient;

    private String endpoint;

    private String bucketName;

    private String fileKey;

}

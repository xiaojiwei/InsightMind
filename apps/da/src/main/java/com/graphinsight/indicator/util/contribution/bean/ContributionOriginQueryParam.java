package com.graphinsight.indicator.util.contribution.bean;

import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/7/22
 * Desc:
 */
@Data
public class ContributionOriginQueryParam {
    private String measCode;
    private String upperLayerMeasCode;
    private String currentDate;
    private String baseDate;
    private String dimCode;


    public ContributionOriginQueryParam() {
    }
}

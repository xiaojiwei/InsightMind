package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2023/10/30
 * Desc:
 * @author lixiaolong5
 */
@Data
public class PageQueryVO extends BaseVO {

    private Integer pageSize;

    private Integer pageNo;
}

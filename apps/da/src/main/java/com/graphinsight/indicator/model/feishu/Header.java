package com.graphinsight.indicator.model.feishu;

import lombok.Builder;
import lombok.Data;

/**
 * Date: 2022/10/14
 * Desc:
 */
@Data
@Builder
public class Header {

    private String template;

    private Text title;
}

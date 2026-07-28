package com.graphinsight.indicator.model.feishu;

import lombok.Builder;
import lombok.Data;

/**
 * Date: 2022/10/14
 * Desc:
 */
@Data
@Builder
public class Action {

    private String tag;

    private Text text;

    private String type;

    private String url;
}

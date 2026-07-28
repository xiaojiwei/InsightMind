package com.graphinsight.indicator.model.feishu;

import lombok.Builder;
import lombok.Data;

/**
 * Date: 2022/10/14
 * Desc:
 */
@Data
@Builder
public class Field {

    private Boolean is_short = Boolean.TRUE;

    private Text text;
}

package com.graphinsight.indicator.model.feishu;

import lombok.Builder;
import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/10/14
 * Desc:
 */
@Data
@Builder
public class Text {

    protected String content;

    // 默认是文本
    protected String tag = TagType.TEXT.getCode();
}

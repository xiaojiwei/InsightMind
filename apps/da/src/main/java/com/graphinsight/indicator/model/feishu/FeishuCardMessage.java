package com.graphinsight.indicator.model.feishu;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Author: lixiaolong
 * Date: 2022/10/14
 * Desc:
 */
@Data
@Builder
public class FeishuCardMessage {

    private Map<String,Object> config;

    private List<Element> elements;

    private Header header;

}

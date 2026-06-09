package com.graphinsight.indicator.model.feishu;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/10/14
 * Desc:
 */
@Data
@Builder
public class Element {

    private String tag;

    private Text text;

    private List<Field> fields;

    private List<Action> actions;

    private Boolean is_short;

}

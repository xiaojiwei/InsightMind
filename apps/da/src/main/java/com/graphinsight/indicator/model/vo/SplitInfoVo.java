package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/11/28
 * Desc: 授权对象
 */
@Data
public class SplitInfoVo {

    // 最原始的文本内容
    private String originalText;


    private String splitText;
}

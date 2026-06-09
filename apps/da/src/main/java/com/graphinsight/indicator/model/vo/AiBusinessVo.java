package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.Pattern;


@Data
public class AiBusinessVo {


    // 关键字
    private String keyWord;
    // 专业术语
    private String keyValue;

    private String ids;

}

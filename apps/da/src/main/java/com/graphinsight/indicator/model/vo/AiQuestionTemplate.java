package com.graphinsight.indicator.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Auther: zhangjinyu
 * @Date: 2024/6/11 16:35
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiQuestionTemplate {
    private String template;
    private Integer viewType;
    private Integer measureCount = 1;
    private Integer dimensionCount = 1;

    public AiQuestionTemplate(String template, Integer viewType) {
        this.template = template;
        this.viewType = viewType;
    }
}

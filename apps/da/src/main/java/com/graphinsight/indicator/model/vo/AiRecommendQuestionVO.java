package com.graphinsight.indicator.model.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * @author houfenglei
 */
@Getter
@Setter
public class AiRecommendQuestionVO {

    /**
    * 唯一主键
    */
    private Long id;

    /**
    * 问题类型
    */
    private String type;

    /**
    * 问题类型标题
    */
    private String title;

    /**
    * 问题
    */
    private String info;

    /**
    * 创建时间
    */
    private String createDate;

    /**
    * 创建人
    */
    private String creator;

    /**
    * 修改时间
    */
    private String updateDate;

    /**
    * 修改人
    */
    private String updater;

}

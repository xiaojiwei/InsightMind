package com.graphinsight.indicator.model.vo;

import lombok.Getter;
import lombok.Setter;
import java.util.Date;

/**
* @author houfenglei
*/
@Getter
@Setter
public class AiQuestionInfoPageVO extends AiQuestionInfoVO {

    private Date createTime;

    private Date updateTime;

    private String createByName;

    private String updateByName;
}

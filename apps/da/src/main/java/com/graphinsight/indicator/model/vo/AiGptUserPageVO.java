package com.graphinsight.indicator.model.vo;

import lombok.Getter;
import lombok.Setter;
import java.util.Date;

/**
* @author houfenglei
*/
@Getter
@Setter
public class AiGptUserPageVO extends AiGptUserVO {

    private Date createTime;

    private Date updateTime;

    private String createByName;

    private String updateByName;
}

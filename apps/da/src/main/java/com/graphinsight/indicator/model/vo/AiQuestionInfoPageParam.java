package com.graphinsight.indicator.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.Date;

/**
* @author houfenglei
*/
@Getter
@Setter
public class AiQuestionInfoPageParam{


    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date endTime;

    private String replyType;

    private String keyWord;
    private Integer pageNo = 1;
    private Integer pageSize = 10;
}

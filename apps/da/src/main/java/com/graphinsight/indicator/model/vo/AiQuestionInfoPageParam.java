package com.graphinsight.indicator.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty(value = "开始时间")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "结束时间")
    private Date endTime;

    @ApiModelProperty(value = "回答是否成功 fail失败 success 成功")
    private String replyType;

    private String keyWord;
    private Integer pageNo = 1;
    private Integer pageSize = 10;
}

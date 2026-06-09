package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataQueryVO {

    private List<String> dimension;

    private List<String> measure;

    private Map<String,List<String>> filter;

    private String sort = null;

    private Integer limit;

    @NotBlank
    @ApiModelProperty(value = "关键字搜", required = true, example = "1")
    private String word;

    //临时后门
    private String username;

    private Integer searchId;
    @NonNull
    @ApiModelProperty(value = "对话sessionId", required = true, example = "1")
    private Integer sessionId;

    private Boolean isData = true;

    private Long spaceId;
    private Boolean useCache = true;
}

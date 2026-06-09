package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.ChartType;
import com.graphinsight.indicator.enums.FolderDataSourceType;
import com.graphinsight.indicator.enums.LineStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.*;

@Data
@ApiModel(value = "FolderDataSourceVO", description = "文件夹数据源VO")
public class FolderDataSourceVO extends BaseVO {

    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "code")
    private String code;

    @ApiModelProperty(value = "名称")
    private String name;

    @ApiModelProperty(value = "上下线状态")
    private LineStatus lineStatus;

    @ApiModelProperty(value = "图标类型")
    private ChartType chartType;

    @ApiModelProperty(value = "类型")
    private FolderDataSourceType nodeType;

    @ApiModelProperty(value = "下级列表")
    private List<FolderDataSourceVO> children = new LinkedList<>();

    @ApiModelProperty(value = "创建人")
    private String creator;

    @ApiModelProperty(value = "创建时间")
    private Date createDate;

    @ApiModelProperty(value = "修改人")
    private String updater;

    @ApiModelProperty(value = "修改时间")
    private Date updateDate;

    @ApiModelProperty(value = "修改时间")
    private Date sortData;

}

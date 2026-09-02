package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.ChartType;
import com.graphinsight.indicator.enums.FolderDataSourceType;
import com.graphinsight.indicator.enums.LineStatus;
import lombok.Data;

import java.util.*;

@Data
public class FolderDataSourceVO extends BaseVO {

    private Long id;

    private String code;

    private String name;

    private LineStatus lineStatus;

    private ChartType chartType;

    private FolderDataSourceType nodeType;

    private List<FolderDataSourceVO> children = new LinkedList<>();

    private String creator;

    private Date createDate;

    private String updater;

    private Date updateDate;

    private Date sortData;

}

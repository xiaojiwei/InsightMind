package com.graphinsight.indicator.model;

import lombok.Data;

import java.util.List;

@Data
public class Page {

    /**
     * 当前页内容
     */
    private List content;

    /**
     * 分页信息
     */
    private PageInfo pageInfo;

}

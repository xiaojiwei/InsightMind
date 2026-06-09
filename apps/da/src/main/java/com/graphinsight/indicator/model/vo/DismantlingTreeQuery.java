package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.DismantlingTreeQueryComparedType;
import com.graphinsight.indicator.enums.DismantlingWay;
import com.graphinsight.indicator.model.Filter;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/11/7
 * Desc:
 */
@Data
public class DismantlingTreeQuery {

    private String queryId;

    private Long spaceId;

    /**
     * 如spaceId一样，userName也是权限控制的一部分
     */
    private String userName;

    private String measCode;

    private Long treeId;

    /**
     * 拆解方式
     */
    private DismantlingWay dismantlingWay;

    /**
     * 对比方式
     */
    private DismantlingTreeQueryComparedType comparedType;

    // private List<Filter> dateFilters = new LinkedList<>();

    private List<Filter> currentDateFilters = new LinkedList<>();

    private List<Filter> baseDateFilters = new LinkedList<>();

    private List<Filter> filters = new LinkedList<>();

}

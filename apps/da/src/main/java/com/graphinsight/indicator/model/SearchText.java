package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.enums.LineStatus;
import lombok.Data;

/**
 * 查询对象
 */
@Data
public class SearchText extends BaseModel {

    /**
     * 检索文本
     */
    private String text;

    /**
     * 只搜索我的
     */
    private boolean mine;

    /**
     * 维度或指标code
     */
    private String elementCode;

    /**
     * 在线状态
     */
    private LineStatus lineStatus;

    /**
     * 当前页
     */
    private Integer pageNo = Integer.valueOf(0);

    /**
     * 页面大小
     */
    private Integer pageSize = Integer.valueOf(9999);

    /**
     * 授权元素对象、指标或维度
     */
    private AuthElementType authElementType;

    /**
     * 空间Id
     */
    private Long spaceId;

    /**
     * 刷新缓存
     */
    private Boolean flash = false;

}

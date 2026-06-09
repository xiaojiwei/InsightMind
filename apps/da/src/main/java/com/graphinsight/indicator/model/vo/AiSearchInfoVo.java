package com.graphinsight.indicator.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import org.apache.poi.ss.formula.functions.T;

import java.io.Serializable;
import java.util.Date;

/**
 * Table: ai_search_info
 */
@Data
public class AiSearchInfoVo {

    private Integer id;


    private String userId;

    private String user;


    private Object content;


    private String contentCode;


    private String contentGpt;


    private String analysisContent;


    private Integer analysisType;


    private Integer isDel;


    private Date createTime;


    private Date updateTime;

    private String measureData;

    private Integer sessionId;

    private String roleType;
}
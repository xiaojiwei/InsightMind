package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import lombok.Data;

import java.util.Date;


@Data
public class AiBusinessListVo {


    private Integer id;

    private String ids;

    // 关键字
    private String keyWord;
    // 专业术语
    private String keyValue;

    private String code;

    private String creator;
    private String updater;

    private User updaterInfo;


    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 更新时间
     */
    private Date updateDate;

}

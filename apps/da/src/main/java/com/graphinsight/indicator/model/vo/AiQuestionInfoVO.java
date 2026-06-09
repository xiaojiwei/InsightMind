package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * @author houfenglei
 */
@Getter
@Setter
public class AiQuestionInfoVO {

    /**
    * 主键
    */
    private Long id;

    /**
    * 回答是否成功
    */
    private String content;

    /**
    * 搜索内容
    */
    private String replyInfo;

    /**
    * 备注
    */
    private String notes;

    /**
    * 对话角色
    */
    private String roleType;

    /**
    * 是否追问 0否 1是
    */
    private String category;

    /**
    * 用户名称
    */
    private String user;

    private User userInfo;

    /**
    * 修改用户名称
    */
    private String updater;

    private String replyType;
    private Date createTime;
}

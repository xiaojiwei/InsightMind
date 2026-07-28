package com.graphinsight.indicator.model.dto;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

/**
 * @Description:
 * @Date: 2021/12/13
 */
@Data
public class CoaUserInfo {

    int id;

    String name;

    @JSONField(name = "job_number")
    String jobNumber;
    /**
     * 当accountType是微信时，该字段存放的是openid
     */
    String email;

    @JSONField(name = "department_id")
    int departmentId;

    @JSONField(name = "feishu_user_id")
    String feishuUserId;

    String cxoToken;

    String accType;

    @JSONField(name = "department_name_path")
    String departmentNamePath;

    String avatar;

    @JSONField(name = "user_name")
    String username;

    Boolean isLeft = false;

}

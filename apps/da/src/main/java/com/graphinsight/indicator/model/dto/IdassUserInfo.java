package com.graphinsight.indicator.model.dto;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.Date;

/**
 * @author zhangbo
 */
@Data
public class IdassUserInfo {

    @JSONField(name = "open_id")
    private String openId;

    @JSONField(name = "domain_id")
    private String domainId;

    @JSONField(name = "mobile")
    private String mobile;

    @JSONField(name = "mobile_verified")
    private Boolean mobileVerified;

    @JSONField(name = "nickname")
    private String nickname;

    @JSONField(name = "picture")
    private String picture;

    @JSONField(name = "gender")
    private String gender;

    @JSONField(name = "email")
    private String email;

    @JSONField(name = "email_verified")
    private Boolean emailVerified;

    @JSONField(name = "registered_at")
    private Date registeredAt;

    @JSONField(name = "created_at")
    private Date createdAt;

    @JSONField(name = "updated_at")
    private Date updatedAt;

    @JSONField(name = "ldap_name")
    private String ldapName;

//    @JSONField(name = "status")
//    private IdassUserStatus status;

}

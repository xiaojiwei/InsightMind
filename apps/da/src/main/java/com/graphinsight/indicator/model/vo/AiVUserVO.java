package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * @author houfenglei
 */
@Data
public class AiVUserVO {

    private String open_id;
    private String domain_id;
    private String mobile;
    private boolean mobile_verified;
    private String nickname;
    private String picture;
    private String gender;
    private String email;
    private boolean email_verified;
    private Date registered_at;
    private Date created_at;
    private Date updated_at;
    private String ldap_name;
    private Object status;

}

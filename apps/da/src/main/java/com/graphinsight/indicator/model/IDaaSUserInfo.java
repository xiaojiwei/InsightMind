package com.graphinsight.indicator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IDaaSUserInfo {

    private String email;

    //中文名
    private String name;

    //正式员工为域账号,外援为idaas返回的id,合作伙伴为idaas返回的user_id
    private String username;

    private String avatar;

    private Integer departmentId;


    private String mobile;

    private String jobNumber;

    private String departmentNamePath;

    private String feishuUserId;

    //正式员工0 外援1 合作伙伴2
    private Integer type;

    public final static int LI_LDAP = 0;

    public final static int LI_HELPER = 1;

    public final static int LI_PARTNER = 2;

}

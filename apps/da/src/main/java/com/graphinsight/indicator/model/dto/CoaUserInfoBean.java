package com.graphinsight.indicator.model.dto;


import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

/**
 * @author tony
 * 查询用户信息返回对象
 */
@Data
public class CoaUserInfoBean {


    @JSONField(name = "department_id_path")
    private String departmentIdPath;


    @JSONField(name = "department_id")
    private Long departmentId;


    @JSONField(name = "department_name")
    private String departmentName;


    @JSONField(name = "department_name_path")
    private String departmentNamePath;


    @JSONField(name = "manager_staff_id")
    private Long managerStaffId;


    @JSONField(name = "mobile")
    private String mobile;


    @JSONField(name = "avatar")
    private String avatar;


    @JSONField(name = "leader_staff_id")
    private Long leaderStaffId;


    @JSONField(name = "user_type")
    private Integer userType;


    @JSONField(name = "user_id")
    private Long userId;


    @JSONField(name = "feishu_open_id")
    private String feishuOpenId;


    @JSONField(name = "feishu_user_id")
    private String feishuUserId;


    @JSONField(name = "manager_feishu_user_id")
    private String managerFeishuUserId;

    /**
     * 姓名
     */
    @JSONField(name = "name")
    private String name;

    /**
     *
     */
    @JSONField(name = "job_number")
    private String jobNumber;

    /**
     * ldap名称
     */
    @JSONField(name = "ldap_name")
    private String ldapName;

    /**
     * 邮箱
     */
    @JSONField(name = "email")
    private String email;

}

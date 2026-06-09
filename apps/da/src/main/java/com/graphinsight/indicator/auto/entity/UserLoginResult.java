package com.graphinsight.indicator.auto.entity;

import lombok.Data;

@Data
public class UserLoginResult {
    private Integer id;
    private String email;

    private String nickname;

    private String username;

    private String avatar;

    private String jobNumber;

    private String departmentNamePath;

    private Integer departmentId;

    private String token;

    private boolean statisticOpen = false;

    public UserLoginResult(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.avatar = user.getAvatar();
        this.departmentId = user.getDepartmentId();
        this.avatar = user.getAvatar();
        this.jobNumber = user.getJobNumber();
        this.nickname = user.getNickname();
        this.departmentNamePath = user.getDepartmentNamePath();
    }

    public UserLoginResult() {
    }
}

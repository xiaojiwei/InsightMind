package com.graphinsight.indicator.auto.entity;

import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Author: lixiaolong
 * Date: 2022/1/28
 * Desc:
 */
@Data
public class BaseEntityV3 {

    public Integer creator;

    public Integer updater;

    public String createUser;

    public String updateUser;

    public LocalDateTime createTime;

    public LocalDateTime updateTime;


    public void initCreate() {

        Integer userId = UserThreadLocalUtil.getUserId();
        this.creator = userId;
        this.updater = userId;
        this.createUser = UserThreadLocalUtil.getUserName();
        this.updateUser = UserThreadLocalUtil.getUserName();
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();

    }

    public String initCreateWithCodePrefix(String codePrefix) {

        Integer userId = UserThreadLocalUtil.getUserId();
        this.creator = userId;
        this.updater = userId;
        this.createUser = UserThreadLocalUtil.getUserName();
        this.updateUser = UserThreadLocalUtil.getUserName();
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();

        return codePrefix + UUID.randomUUID().toString().replace("-","");

    }

    public void initUpdate() {
        Integer userId = UserThreadLocalUtil.getUserId();
        this.updater = userId;
        this.updateUser = UserThreadLocalUtil.getUserName();
        this.updateTime = LocalDateTime.now();
    }


}

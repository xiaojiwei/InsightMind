package com.graphinsight.indicator.auto.entity;

import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Author: lixiaolong
 * Date: 2022/1/28
 * Desc:
 */
@Data
public class BaseEntityV2 {

    public String creator;

    public String updater;

    public LocalDateTime createTime;

    public LocalDateTime updateTime;


    public void initCreate() {

        String userId = UserThreadLocalUtil.getUserName();
        this.creator = userId;
        this.updater = userId;

        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();

    }


    public void initUpdate() {
        String userId = UserThreadLocalUtil.getUserName();
        this.updater = userId;
        this.updateTime = LocalDateTime.now();
    }


}

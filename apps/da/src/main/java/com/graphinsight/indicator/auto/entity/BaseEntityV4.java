package com.graphinsight.indicator.auto.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Date: 2022/1/28
 * Desc:
 */
@Data
public class BaseEntityV4 {

    public String creator;

    public String updater;

    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date createTime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date updateTime;


    public void initCreate() {

        String userId = UserThreadLocalUtil.getUserName();
        this.creator = userId;
        this.updater = userId;

        this.createTime = new Date();
        this.updateTime = new Date();

    }


    public void initUpdate() {
        String userId = UserThreadLocalUtil.getUserName();
        this.updater = userId;
        this.updateTime = new Date();
    }


}

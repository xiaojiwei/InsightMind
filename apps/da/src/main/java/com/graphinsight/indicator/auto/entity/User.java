package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.graphinsight.indicator.model.dto.TokenDetail;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @Author: lixiaolong
 * @Description:
 * @Date: 2021/11/16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class User extends TokenDetail {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String username;

    private String nickname;

    private String email;

    private Integer departmentId;

    private String jobNumber;

    private String departmentNamePath;

    private String avatar;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    private Integer available;

    private String feishuUserId;

}

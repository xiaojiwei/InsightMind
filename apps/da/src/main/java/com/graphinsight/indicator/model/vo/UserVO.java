package com.graphinsight.indicator.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description:
 * @Date: 2021/12/14
 */
@Data
public class UserVO {
    /**
     * 主键
     */
    private Integer id;

    private String username;

    private String nickname;

    private String email;

    private Integer departmentId;

    private String jobNumber;

    private String departmentNamePath;

    private String avatar;

    private String token;
}

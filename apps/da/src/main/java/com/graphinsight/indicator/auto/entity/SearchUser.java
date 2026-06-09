package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.graphinsight.indicator.model.dto.TokenDetail;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

/**
 * @Description:
 */
@Data
public class SearchUser {

    public static SearchUser build(User user) {
        SearchUser searchUser = new SearchUser();
        BeanUtils.copyProperties(user,searchUser);
        searchUser.setType(1);
        return searchUser;
    }

    private Integer id;

    private String username;

    private String nickname;

    private String email;

    private Integer departmentId;

    private String jobNumber;

    private String departmentNamePath;

    private String deptPath;

    private String avatar;

    private String feishuUserId;

    private Integer type;

}

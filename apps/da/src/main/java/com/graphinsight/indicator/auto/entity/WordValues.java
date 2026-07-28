package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.graphinsight.indicator.model.dto.TokenDetail;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class WordValues  implements Serializable {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "`code`")
    private String code;
    @TableField(value = "`key`")
    private String key;
    @TableField(value = "`value`")
    private String value;

    private String creator;
    private String updater;

    private Integer type;


    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 更新时间
     */
    private Date updateDate;

    public void initCreate() {

        String userId = UserThreadLocalUtil.getUserName();
        this.creator = userId;
        this.updater = userId;

    }

}

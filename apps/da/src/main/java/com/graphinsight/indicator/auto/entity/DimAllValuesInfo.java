package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.graphinsight.indicator.model.dto.TokenDetail;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DimAllValuesInfo extends TokenDetail {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String code;

    private String dimCode;

    private String dimName;


    private String valueKey;

    private String valueText;

    private String valueFormatText;

    private String nature;

    /**
     * 创建时间
     */
    private LocalDateTime createDate;

    /**
     * 更新时间
     */
    private LocalDateTime createTime;

    private String dimFilters;
    private Integer manyFlag;

    private Integer dimId;
}

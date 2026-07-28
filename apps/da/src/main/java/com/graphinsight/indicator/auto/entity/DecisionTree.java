package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 决策树详情表
 * </p>
 *
 * @since 2022-06-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DecisionTree extends BaseEntityV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 结果指标code
     */
    private String measCode;

    /**
     * 是否是默认树 0-否 1-是
     */
    private Integer isDefault;

    /**
     * 空间ID
     */
    private Long spaceId;

}

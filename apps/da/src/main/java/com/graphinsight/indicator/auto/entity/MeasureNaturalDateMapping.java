package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 指标自然日关联表
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-08-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MeasureNaturalDateMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 指标id
     */
    private Long measId;

    /**
     * 选择的维度id
     */
    private Long targetDimId;

    /**
     * 关联的自然日期维度主键
     */
    private Long naturalDimId;

    /**
     * 指标所在事实表主键
     */
    private Long dwTableId;


}

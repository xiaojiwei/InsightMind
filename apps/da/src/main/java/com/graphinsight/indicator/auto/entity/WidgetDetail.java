package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * widget详情
 * </p>
 *
 * @since 2022-08-31
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class WidgetDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * widget表主键
     */
    private Long widgetId;

    /**
     * 指标或维度code
     */
    private String code;

    /**
     * 0-指标 1-维度
     */
    private Integer type;


}

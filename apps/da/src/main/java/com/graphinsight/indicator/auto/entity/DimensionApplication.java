package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 维度应用表
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DimensionApplication implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer dimId;

    /**
     * 事实表Id
     */
    private Integer dwTableId;

    /**
     * 数据源类型 0 mysql； 1 doris
     */
    private Integer sourceType;

    /**
     * 在事实表中的列名映射，比如城市维度，在事实表中可能叫city_id
     */
    private String factColumn;

    /**
     * 维度在事实表中的字段类型
     */
    private String dataType;


    /**
     * 是否可用 0-不可用 1-可用
     */
    private Integer available;

    private Integer creator;

    private Integer updater;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


}

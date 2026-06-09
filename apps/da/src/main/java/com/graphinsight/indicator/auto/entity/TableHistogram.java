package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author lixiaolong5
 * @since 2023-01-04
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TableHistogram implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 行数
     */
    private Long rowNum;

    /**
     * 事实表表名
     */
    private String tableName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


}

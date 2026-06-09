package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 决策树引用表
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-11-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DismantlingTreeQuote implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 拆解树主键
     */
    private Long treeId;

    /**
     * 引用的指标、维度
     */
    private String code;

    /**
     * 0-指标 1-维度
     */
    private Integer type;


}

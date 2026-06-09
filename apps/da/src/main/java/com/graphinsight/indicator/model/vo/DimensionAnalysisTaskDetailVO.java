package com.graphinsight.indicator.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 多维分析任务详情表
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-07-05
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DimensionAnalysisTaskDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * task主键
     */
    private Long taskId;

    /**
     * 查询维度
     */
    private String dimCode;

    /**
     * 基尼系数
     */
    private BigDecimal giniValue;

    /**
     * 任务执行失败信息
     */
    private String errorMessage;

    /**
     * 0-未开始计算
     * 1-计算成功
     * 2-计算失败
     */
    private Integer status;

    private String cnName;


}

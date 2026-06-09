package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 拆解树表
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-11-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DismantlingTree extends BaseEntityV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;


    /**
     * 空间ID
     */
    private Long spaceId;

    /**
     * 根节点指标code
     */
    private String rootMeasCode;

    /**
     * 名称
     */
    private String name;

    /**
     * 前端配置
     */
    private String feConfig;

    /**
     * 后端配置
     */
    private String beConfig;

    /**
     * 是否是默认树
     */
    private Integer isDefault;

}

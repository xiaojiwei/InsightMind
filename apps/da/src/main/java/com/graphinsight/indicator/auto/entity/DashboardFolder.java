package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 看板文件夹
 * </p>
 *
 * @since 2022-08-31
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DashboardFolder extends BaseEntityV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 文件夹名称
     */
    private String name;

    /**
     * 父文件夹ID
     */
    private Long parentId;

    /**
     * 空间ID
     */
    private Long spaceId;

    /**
     * 备注
     */
    private String mark;

    /**
     * 根目录ID
     */
    private Long rootId;


}

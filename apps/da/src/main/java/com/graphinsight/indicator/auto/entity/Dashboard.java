package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 看板表
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-08-31
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Dashboard extends BaseEntityV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 看板状态
     */
    private Integer status;

    /**
     * 文件夹ID
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long folderId;

    /**
     * 文件夹ID
     */
    private Long spaceId;

    /**
     * 看板在线版本
     */
    private Long onlineVersionId;

    /**
     * 看板最新版本
     */
    private Long latestVersionId;

    /**
     * 是否被删除
     */
    private Integer isDelete;

    /**
     * 看板名称
     */
    private String name;

    /**
     * 备注
     */
    private String remark;


}

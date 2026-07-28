package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Date: 2022/10/12
 * Desc:
 */
@Data
public class TSpaceVO {


    /**
     * 唯一主键
     */
    private Long id;

    /**
     * cobe状态下的唯一标识
     */
    private String code;

    /**
     * 创建时间
     */
    private LocalDateTime createDate;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 修改时间
     */
    private LocalDateTime updateDate;

    /**
     * 修改人
     */
    private String updater;

    /**
     * 空间名称
     */
    private String name;

    /**
     * 空间说明
     */
    private String remarks;

}

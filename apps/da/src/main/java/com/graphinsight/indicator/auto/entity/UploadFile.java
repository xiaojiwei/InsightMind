package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 数据源描述
 *    为多维分析的核心模型，承载数据源存储、检索功能。
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class UploadFile {

   @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String dataId;



    private String fileKey;


    /**
     * 创建时间
     */
    private LocalDateTime createDate;

    /**
     * 更新时间
     */
    private LocalDateTime updateDate;

    public String creator;

    public String updater;

}

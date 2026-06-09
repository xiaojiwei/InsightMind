package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.graphinsight.indicator.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.Table;
import javax.persistence.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * 数据源描述
 *    为多维分析的核心模型，承载数据源存储、检索功能。
 */
@Entity
@Table(name = "down_file")
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "down_file", comment="下载信息")
public class DownFileInfo extends BaseModel {

    /**
     * 数据源名称
     */
    @Column(name = "v_user_info", columnDefinition = "varchar(255) COMMENT '下载信息'")
    private String userInfo;

    /**
     * 数据源ID
     */
    @Column(name = "v_count", columnDefinition = "bigint(11) COMMENT '下载总数'")
    private Long count;

    /**
     * 下载sql
     */
    @Column(name = "v_sql", columnDefinition = "varchar(8000) COMMENT '下载sql'")
    private String sqlText;

    /**
     * bos路径
     */
    @Column(name = "v_file_path", columnDefinition = "varchar(8000) COMMENT '文件路径'")
    private String filePath;

}

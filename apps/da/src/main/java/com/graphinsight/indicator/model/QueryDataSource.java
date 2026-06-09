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
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "query_data_source", comment="查询数据源")
public class QueryDataSource extends BaseModel {

    /**
     * 指标、维度的所有配置信息
     */
    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true)
    @Cascade({CascadeType.ALL})
    @Fetch(FetchMode.SUBSELECT)
    @JoinColumn(name="data_source_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private List<QueryBaseConfigure> configureList = new LinkedList<>();

    /**
     * 指标、维度的所有配置信息
     */
    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true)
    @Cascade({CascadeType.ALL})
    @Fetch(FetchMode.SUBSELECT)
    @JoinColumn(name="data_source_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private List<QueryBaseTable> queryTableList = new LinkedList<>();

    /**
     * spaceId
     */
    @Column(columnDefinition = "varchar(255) COMMENT '空间ID'")
    private String spaceId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        QueryDataSource that = (QueryDataSource) o;
        return Objects.equals(this.getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.getId());
    }

    public void initCreate() {
        super.initCreate();
    }

}

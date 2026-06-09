package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "query_base_table", comment="查询表")
public class QueryBaseTable extends BaseModel {

    /**
     * 维度唯一名称
     */
    @Column(name="table_name", columnDefinition = "varchar(255) COMMENT '维度唯一名称'")
    private String tableName;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryBaseTable)) return false;
        if (!super.equals(o)) return false;
        QueryBaseTable that = (QueryBaseTable) o;
        return Objects.equals(getCode(), that.getCode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getCode());
    }
}

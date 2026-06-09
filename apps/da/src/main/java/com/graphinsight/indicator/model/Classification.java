package com.graphinsight.indicator.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
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
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "classification", comment="指标分类")
public class Classification extends BaseModel {

    /**
     * 指标分类code
     */
    @Column(columnDefinition = "varchar(255) COMMENT '指标分类标识'")
    private String classCode;

    /**
     * 名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '名称'")
    private String name;

    /**
     * 工作空间
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIncludeProperties({"id", "name"})
    @Cascade({org.hibernate.annotations.CascadeType.SAVE_UPDATE})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Space space;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Classification)) return false;
        if (!super.equals(o)) return false;
        Classification that = (Classification) o;
        return classCode.equals(that.classCode) &&
                name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), classCode, name);
    }
}

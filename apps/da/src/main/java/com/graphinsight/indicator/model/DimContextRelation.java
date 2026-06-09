package com.graphinsight.indicator.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.enums.AuthFilterParamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Table;
import javax.persistence.*;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "dim_context_relation")
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "dim_context_relation", comment="维度上下文环境")
public class DimContextRelation extends BaseModel {

    /**
     * 此处code是维度
     * super.code
     */

    /**
     * 指标或维度名称
     */
    @Column(columnDefinition = "bigint(20) COMMENT '上下文授权id operate_grant_config'")
    private Long grantConfigId;

}

package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.Table;
import javax.persistence.*;
import java.util.*;

@Entity
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "filter_tree", comment="筛选树")
public class FilterTree extends BaseModel {

    @Transient
    private String unionid;

    /**
     * 结点类型
     */
    private FilterType filterType;

    /**
     * 关系模式下逻辑符，逻辑运算符 默认逻辑与
     */
    @Column(columnDefinition = "int(11) COMMENT '逻辑运算符 默认逻辑与'")
    private SqlLogicalType sqlLogicalType = SqlLogicalType.AND;

    /**
     * 筛选项模式下 filter
     */
    @OneToOne(fetch = FetchType.LAZY, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Filter filter;

    /**
     * 下级元素操作数据集合
     */
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(name="tree_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @Fetch(FetchMode.SUBSELECT)
    private Set<FilterTree> filterTreeSet = new LinkedHashSet<>();

}

package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.enums.SortType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;
import org.hibernate.annotations.CascadeType;

import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.Table;
import javax.persistence.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

@Entity  //定义为实体类
@Table(name = "t_ratio")
@DynamicInsert         //支持动态插入
@DynamicUpdate         //支持动态更新
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "t_ratio", comment="比率")
public class Ratio extends BaseModel {

    @Column(columnDefinition = "int(11) COMMENT '同环比设置'")
    private RatioType ratioType = RatioType.DEFAULT;

    /**
     * 百分比计算方式
     */
    @Column(columnDefinition = "int(11) COMMENT '百分比计算方式'")
    private RatioExpType ratioExpType;

    /**
     * 同环比为相对值时，时间计算方式。向前或向后。
     */
    @Column(columnDefinition = "int(11) COMMENT '同环比取值向前或向后'")
    private RatioOperaType ratioOperaType;

    /**
     * 同环比维度。
     */
    @Column(columnDefinition = "varchar(255) COMMENT '维度code'")
    private String dimCode;


    /**
     * 同环设置值，固定、相对。
     */
    @Column(columnDefinition = "varchar(255) COMMENT '同环设置值，固定、相对'")
    private String ratioValue;


    /**
     * 排序类型
     */
    @Column(columnDefinition = "int(11) COMMENT '排序类型'")
    private SortType sortType;

    /**
     * 前端设置
     */
    @Column(length = 3000, columnDefinition = "varchar(3000) COMMENT '数据源设置'")
    private String settings;

    /**
     * 操作数据集合
     */
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade({CascadeType.ALL})
    @JoinColumn(name="ratio_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @Fetch(FetchMode.SUBSELECT)
    private List<Operator> operatorList = new LinkedList<>();


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ratio order = (Ratio) o;
        return Objects.equals(this.getCode(), order.getCode()) &&
                sortType == order.sortType ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getCode(), sortType);
    }

}

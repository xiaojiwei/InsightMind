package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.graphinsight.indicator.enums.SortScope;
import com.graphinsight.indicator.enums.SortType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import javax.persistence.Table;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

@Entity  //定义为实体类
@Table(name = "t_order")
@DynamicInsert         //支持动态插入
@DynamicUpdate         //支持动态更新
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "t_order", comment="排序字段")
public class Order extends BaseModel {

    /**
     * 排序类型
     */
    @Column(columnDefinition = "int(11) COMMENT '排序类型'")
    private SortType sortType;

    /**
     * 排序范围
     */
    @Column(columnDefinition = "int(11) COMMENT '排序范围'")
    private SortScope sortScope = SortScope.GROUP;

    /**
     * 自定义排序中用到的值
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "order_data_list", joinColumns = @JoinColumn(columnDefinition = "bigint(10) COMMENT '排序id'", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT)), foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @Column(columnDefinition = "varchar(255) COMMENT '值'")
    private List<String> valueList = new LinkedList<String>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(this.getCode(), order.getCode()) &&
                sortType == order.sortType &&
                Objects.equals(valueList, order.valueList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getCode(), sortType, valueList);
    }

}

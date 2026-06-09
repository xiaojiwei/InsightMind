package com.graphinsight.indicator.model;

import lombok.Data;

import java.util.Objects;

@Data
public class MeasureDetailColumnConfigure extends BaseModel {

    /**
     * 指标明细唯一 columnName
     */
    private String columnName;

    /**
     * 排序类型
     * @see Order
     */
    private Order order;

    public Order getOrder() {
        if (null != this.order) {
            this.order.setCode(this.getCode());
        }
        return this.order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MeasureDetailColumnConfigure)) return false;
        if (!super.equals(o)) return false;
        MeasureDetailColumnConfigure that = (MeasureDetailColumnConfigure) o;
        return Objects.equals(getColumnName(), that.getColumnName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getColumnName());
    }
}

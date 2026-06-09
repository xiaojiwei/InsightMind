package com.graphinsight.indicator.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.graphinsight.indicator.enums.RoleType;
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
@Table(name = "space_role")
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "space_role", comment="空间角色")
public class SpaceRole extends BaseModel {

    /**
     * 角色类型
     */
    @Column(columnDefinition = "int(11) COMMENT '角色类型'")
    private RoleType roleType;

    /**
     * 所属人员
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIncludeProperties({"id", "employeeCode", "name"})
    @Cascade({org.hibernate.annotations.CascadeType.REFRESH})
    @JoinColumn(name="space_employee_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private SpaceEmployee spaceEmployee;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Space)) return false;
        if (!super.equals(o)) return false;
        Space space = (Space) o;
        return Objects.equals(id, space.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id);
    }

}

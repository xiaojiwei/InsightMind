package com.graphinsight.indicator.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.graphinsight.indicator.enums.AuthObjectType;
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
@Table(name = "t_department")
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "t_department", comment="部门")
public class SpaceDepartment extends BaseModel {

    /**
     * 部门code
     */
    @Column(columnDefinition = "varchar(255) COMMENT '部门code'")
    private String deptCode;

    /**
     * 名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '名称'")
    private String name;

    /**
     * 授权类型
     */
    @Column(columnDefinition = "int(11) COMMENT '授权类型组织或人员'")
    private AuthObjectType authObjectType;

    /**
     * 部门下的用户数量
     */
    @Transient
    private Integer userNum;

    /**
     * 头像
     */
    @Transient
    private String avatar;

    /**
     * 工作空间
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JsonIncludeProperties({"id", "name"})
    @Cascade({org.hibernate.annotations.CascadeType.SAVE_UPDATE})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Space space;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpaceDepartment)) return false;
        if (!super.equals(o)) return false;
        SpaceDepartment that = (SpaceDepartment) o;
        return Objects.equals(deptCode, that.deptCode) &&
                Objects.equals(name, that.name) &&
                Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), deptCode, name, id);
    }
}

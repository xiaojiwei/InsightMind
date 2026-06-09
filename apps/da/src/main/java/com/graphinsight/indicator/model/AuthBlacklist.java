package com.graphinsight.indicator.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.graphinsight.indicator.enums.AuthElementType;
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
import java.util.Objects;

@Entity
@Table(name = "auth_blacklist")
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "auth_blacklist", comment="空间人员黑名单")
public class AuthBlacklist extends BaseModel {

    /**
     * 用户标识
     */
    @Column(columnDefinition = "varchar(255) COMMENT '用户标识'")
    private String employeeCode;

    /**
     * 用户名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '用户名称'")
    private String name;

    /**
     * 授权类型
     */
    @Column(columnDefinition = "int(11) COMMENT '授权类型组织或人员'")
    private AuthObjectType authObjectType;

    /**
     * 授权元素类型
     */
    @Column(columnDefinition = "int(11) COMMENT '授权元素类型维度或指标'")
    private AuthElementType authElementType;

    /**
     * 工作空间
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIncludeProperties({"id", "name"})
    @Cascade({org.hibernate.annotations.CascadeType.REFRESH})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Space space;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthBlacklist)) return false;
        if (!super.equals(o)) return false;
        AuthBlacklist that = (AuthBlacklist) o;
        return Objects.equals(employeeCode, that.employeeCode) &&
                Objects.equals(name, that.name) &&
                authObjectType == that.authObjectType &&
                authElementType == that.authElementType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), employeeCode, name, authObjectType, authElementType);
    }
}

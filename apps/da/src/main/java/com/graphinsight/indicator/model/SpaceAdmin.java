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

import javax.persistence.*;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "space_admin")
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "space_admin", comment="空间管理员")
public class SpaceAdmin extends BaseModel {

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
     * mail
     */
    @Column(columnDefinition = "varchar(255) COMMENT 'mail'")
    private String mail;

    /**
     * 授权类型
     */
    @Column(columnDefinition = "int(11) COMMENT '授权类型组织或人员'")
    private AuthObjectType authObjectType;

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
        if (!(o instanceof SpaceAdmin)) return false;
        SpaceAdmin that = (SpaceAdmin) o;
        return Objects.equals(employeeCode, that.employeeCode) &&
                Objects.equals(name, that.name) &&
                Objects.equals(mail, that.mail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(employeeCode, name, mail);
    }
}

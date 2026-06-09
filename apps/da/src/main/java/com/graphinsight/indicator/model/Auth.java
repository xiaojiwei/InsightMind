package com.graphinsight.indicator.model;


import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.RoleType;
import com.graphinsight.indicator.model.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Table;
import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "t_auth")
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "t_auth", comment="权限")
public class Auth extends BaseModel {

    /**
     * 用户或组织标识
     */
    @Column(columnDefinition = "varchar(255) COMMENT '用户标识'")
    private String employeeCode;

    @Column(name = "search_index",columnDefinition = "varchar(255) COMMENT '权限主表搜索字段'")
    private String searchIndex;

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
     * 其为部门时的原始id
     */
    @Transient
    private Long authId;

    /**
     * 人员部门名称
     */
    @Transient
    private String name;

    /**
     * 授权过期时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Column(columnDefinition = "datetime COMMENT '授权过期时间'")
    protected Date authDate;

    /**
     * 授权类型
     */
    @Column(columnDefinition = "int(11) COMMENT '授权类型指标或维度'")
    private AuthElementType authElementType;

    /**
     * 所属工作空间
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIncludeProperties({"id", "name"})
    @Cascade({org.hibernate.annotations.CascadeType.REFRESH})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @JsonBackReference
    private Space space;

    /**
     * 授权类型
     */
    @Column(columnDefinition = "int(11) COMMENT '授权类型组织或人员'")
    private AuthObjectType authObjectType;

    /**
     * 授权元素
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JsonIncludeProperties({"code", "name", "authFilterParamType", "filter", "detailFilter", "authElementMeasureSet", "authElementType"})
    @JoinColumn(name="auth_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<AuthElement> authElementSet = new LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Auth)) return false;
        Auth auth = (Auth) o;
        return Objects.equals(employeeCode, auth.employeeCode) &&
                authElementType == auth.authElementType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(employeeCode, authElementType);
    }

    @Override
    public String toString() {
        return "Auth{" +
                "employeeCode='" + employeeCode + '\'' +
                '}';
    }

    /**
     * 用户部门id
     */
    @Transient
    private String departmentNamePath;

    /**
     * 用户类型
     */
    @Transient
    private RoleType roleType;

    /**
     * 用户邮箱
     */
    @Transient
    private String mail;

    /**
     * 用户授权的角色
     */
    @Transient
    private Set<SpaceRole> spaceRoleSet = new HashSet<>();

    public static Auth build(UserDTO user, Auth paramAuth, RoleType roleType) {
        return build(user, paramAuth, roleType, true);
    }

    public static Auth build(UserDTO user, Auth paramAuth, RoleType roleType, Boolean isExist) {

        Auth auth = new Auth();

        if (isExist) {
            auth.setId(paramAuth.getId());
            auth.setAuthId(paramAuth.getId());
//            auth.setAuthObjectType(paramAuth.getAuthObjectType());
        } else {
            //
        }
        auth.setAuthObjectType(AuthObjectType.EMPLOYEE);
        auth.setRoleType(roleType);
        auth.setName(user.getNickname());
        auth.setMail(user.getEmail());
        auth.setEmployeeCode(user.getUsername());
        auth.setDepartmentNamePath(user.getDepartmentNamePath());
        //用户包含的角色
        auth.setAuthElementType(paramAuth.getAuthElementType());
        auth.setAuthDate(paramAuth.getAuthDate());
        auth.setAuthElementSet(paramAuth.getAuthElementSet());
        auth.setRoleType(paramAuth.getRoleType());
        auth.setAvatar(user.getAvatar());

        return auth;

    }
}

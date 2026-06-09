package com.graphinsight.indicator.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.RoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Table;
import javax.persistence.*;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "t_space")
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "t_space", comment="空间管理")
public class Space extends BaseModel {

    /**
     * 空间名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '空间名称'")
    private String name;

    /**
     * 空间说明
     */
    @Column(columnDefinition = "varchar(800) COMMENT '空间说明'")
    private String remarks;

    /**
     * 人员授权时，所拥有的角色,前端传入后台无需存储
     */
    @Transient
    private Set<SpaceRole> spaceRoleSet = new LinkedHashSet<>();

    /**
     * 文件夹
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonIncludeProperties({})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<Folder> folderSet = new LinkedHashSet<>();

    /**
     * 数据源
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonIncludeProperties({})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<DataSource> dataSourceSet = new LinkedHashSet<>();

    /**
     * 部门
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonIncludeProperties({"name", "userNum", "authObjectType", "deptCode", "creator", "createDate", "updater", "updateDate"})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<SpaceDepartment> deptSet = new LinkedHashSet<>();

    /**
     * 指标分类
     */
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonIncludeProperties({"name", "classCode", "creator", "createDate", "updater", "updateDate"})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<Classification> classificationSet = new LinkedHashSet<>();

    /**
     * 空间管理员
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonIncludeProperties({"name", "userNum", "avatar", "employeeCode", "authObjectType", "creator", "createDate", "updater", "updateDate"})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<SpaceAdmin> spaceAdminSet = new LinkedHashSet<>();

    /**
     * 空间拥有者
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonIncludeProperties({"name", "employeeCode", "userNum", "avatar", "authObjectType", "creator", "createDate", "updater", "updateDate"})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<SpaceOwner> spaceOwnerSet = new LinkedHashSet<>();

    /**
     * 空间人员
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonIncludeProperties({"name", "employeeCode", "userNum", "avatar", "mail", "authObjectType", "spaceRoleSet", "creator", "createDate", "updater", "updateDate"})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<SpaceEmployee> spaceEmpSet = new LinkedHashSet<>();

    /**
     * 空间人员黑名单
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
//    @JsonIncludeProperties({"name", "employeeCode", "userNum", "avatar", "authObjectType", "creator", "createDate", "updater", "updateDate"})
    @JsonIncludeProperties({})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<SpaceBlacklist> spaceBlacklistSet = new LinkedHashSet<>();

    /**
     * 空间授权黑名单
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
//    @JsonIncludeProperties({"name", "employeeCode", "userNum", "avatar", "authObjectType", "delAuthElementType", "creator", "createDate", "updater", "updateDate"})
    @JsonIncludeProperties({})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<AuthBlacklist> authBlacklistSet = new LinkedHashSet<>();

    /**
     * 空间权限(指标、维度)
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonIncludeProperties({"authElementType", "authObjectType", "userNum", "avatar", "employeeCode", "authDate", "creator", "createDate", "updater", "updateDate", "name"})
    @JoinColumn(name="space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<Auth> authSet = new LinkedHashSet<>();


    /**
     * 授权元素,用于授权提交时使用，无需持久化。
     */
    @Transient
    private Set<AuthElement> authElementSet = new LinkedHashSet<>();

    /**
     * 授权类型,用于授权提交时使用，无需持久化。
     */
    @Transient
    private AuthObjectType authObjectType;

    /**
     * 授权类型
     */
    @Transient
    private AuthElementType authElementType;

    /**
     * 授权过期时间，用于授权提交时使用，无需持久化。
     */
    @Transient
    private Date authDate;

    /**
     * 是否为追加模式
     */
    @Transient
    private boolean append;

    /**
     * 当前人在空间所拥有的角色
     */
    @Transient
    private Set<RoleType> roleTypeSet = new LinkedHashSet<>();

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

    @Override
    public String toString() {
        return "Space{" +
                "name='" + name + '\'' +
                '}';
    }
}

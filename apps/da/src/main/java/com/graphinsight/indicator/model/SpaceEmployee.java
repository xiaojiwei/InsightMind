package com.graphinsight.indicator.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.RoleType;
import com.graphinsight.indicator.enums.SortType;
import com.graphinsight.indicator.model.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import javax.persistence.Table;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "space_employee")
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "space_employee", comment="空间人员")
public class SpaceEmployee extends BaseModel {

    /**
     * 用户标识
     */
    @Column(columnDefinition = "varchar(255) COMMENT '用户标识'")
    private String employeeCode;

    /**
     * 当其为部门时的原始id
     */
    @Transient
    private Long spaceEmpId;

    /**
     * 用户名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '用户名称'")
    private String name;

    /**
     * mail
     */
    @Column(columnDefinition = "varchar(255) COMMENT 'mail'")
    private String mail;

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
     * 人员所属的最高角色
     */
    @Transient
    private RoleType roleType;

    /**
     * 授权类型
     */
    @Column(columnDefinition = "int(11) COMMENT '授权类型组织或人员'")
    private AuthObjectType authObjectType;

    /**
     * 空间人员角色，多选
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JsonIncludeProperties({"name", "roleType", "creator", "createDate", "updater", "updateDate"})
    @JoinColumn(name="space_employee_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<SpaceRole> spaceRoleSet = new LinkedHashSet<>();

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
        if (!(o instanceof SpaceEmployee)) return false;
        SpaceEmployee that = (SpaceEmployee) o;
        return employeeCode.equals(that.employeeCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(employeeCode);
    }

    @Override
    public String toString() {
        return "SpaceEmployee{" +
                "employeeCode='" + employeeCode + '\'' +
                ", name='" + name + '\'' +
                ", mail='" + mail + '\'' +
                ", authObjectType=" + authObjectType +
                '}';
    }

    /**
     * 用户部门名称路径
     */
    @Transient
    private String departmentNamePath;

    public static SpaceEmployee build(UserDTO user, SpaceEmployee paramSpaceEmployee, boolean isExist) {

        SpaceEmployee spaceEmployee = new SpaceEmployee();

        if (isExist) {
            spaceEmployee.setId(paramSpaceEmployee.getId());
            spaceEmployee.setSpaceEmpId(paramSpaceEmployee.getId());
            spaceEmployee.setAuthObjectType(paramSpaceEmployee.getAuthObjectType());
        } else {
            spaceEmployee.setAuthObjectType(AuthObjectType.EMPLOYEE);
        }

        spaceEmployee.setCreateDate(paramSpaceEmployee.getCreateDate());
        spaceEmployee.setEmployeeCode(user.getUsername());
        spaceEmployee.setName(user.getNickname());
        spaceEmployee.setMail(user.getEmail());
        spaceEmployee.setDepartmentNamePath(user.getDepartmentNamePath());


        //用户包含的角色
//        spaceEmployee.setSpaceRoleSet(paramSpaceEmployee.getSpaceRoleSet());
        spaceEmployee.getSpaceRoleSet().addAll(paramSpaceEmployee.getSpaceRoleSet());
        spaceEmployee.setAvatar(user.getAvatar());

        return spaceEmployee;

    }



    public static SpaceEmployee build(UserDTO user, SpaceEmployee paramSpaceEmployee) {
        return build(user, paramSpaceEmployee, true);
    }

}

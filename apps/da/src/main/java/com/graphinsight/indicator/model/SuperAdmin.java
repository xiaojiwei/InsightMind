package com.graphinsight.indicator.model;


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
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "t_super_admin")
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "t_super_admin", comment="超级管理员")
public class SuperAdmin extends BaseModel {

    /**
     * 用户或组织标识
     */
    @Column(columnDefinition = "varchar(255) COMMENT '用户标识'")
    private String empCode;

}

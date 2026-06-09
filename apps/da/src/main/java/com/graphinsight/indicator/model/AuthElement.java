package com.graphinsight.indicator.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.enums.AuthFilterParamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.util.CollectionUtils;

import javax.persistence.Table;
import javax.persistence.*;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "auth_element")
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler","hibernateLazyInitializer","fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "auth_element", comment="属权元素指标或维度")
public class AuthElement extends BaseModel {

    /**
     * 此处code是维度或指标的唯一标识，以标识具体授权的对象。
     * super.code
     */

    /**
     * 指标或维度名称
     */
    @Transient
    private String name;

    /**
     * 授权类型
     * @see AuthElementType
     */
    @Column(columnDefinition = "int(11) COMMENT '授权类型 指标或维度'")
    private AuthElementType authElementType;

    /**
     * 标准授权、上下文授权
     * @see AuthElementType
     */
    @Column(columnDefinition = "int(11) COMMENT '标准模式、上下文模式'")
    private AuthFilterParamType authFilterParamType = AuthFilterParamType.STANDARD;

    /**
     * 所属授权
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIncludeProperties({"id", "name"})
    @Cascade({org.hibernate.annotations.CascadeType.SAVE_UPDATE})
    @JoinColumn(name="auth_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Auth auth;

    /**
     * 维度筛选项，如果为运营架构授权时，filter operator in 值里存储环境变量
     * @see com.graphinsight.indicator.enums.EnvirAuthValueType
     */
    @OneToOne(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Filter filter;


    /**
     * 维度筛选项，如果为运营架构授权时，filter operator in 值里存储环境变量
     * @see com.graphinsight.indicator.enums.EnvirAuthValueType
     */
    @OneToOne(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(name="detail_filter_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Filter detailFilter;

    /**
     * 授权元素所应用的指标,可为null
     */
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JsonIncludeProperties({"id", "measCode"})
    @JoinColumn(name="auth_element_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderBy(value = "updateDate desc")
    private Set<AuthElementMeasure> authElementMeasureSet = new LinkedHashSet<>();

    private String getAuthMeasCodes() {

        StringBuffer measCodeBuffer = new StringBuffer();
        if (!CollectionUtils.isEmpty(authElementMeasureSet)) {
            for (AuthElementMeasure authElementMeasure : authElementMeasureSet) {
                measCodeBuffer.append(authElementMeasure.getMeasCode());
            }
        }

        return measCodeBuffer.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthElement that = (AuthElement) o;
        String measCodes = this.getAuthMeasCodes();
        Filter filter = this.getFilter();


        //容错延迟加载对象在异步下载时code为null的bug
        if (null == code || null == measCodes) {
            return false;
        }

        return code.equalsIgnoreCase(that.code) && measCodes.equalsIgnoreCase(that.getAuthMeasCodes()) &&
                authElementType == that.authElementType &&
                authFilterParamType == that.authFilterParamType &&
                Objects.equals(auth, that.auth) &&
                Objects.equals(filter, that.filter) &&
                Objects.equals(authElementMeasureSet, that.authElementMeasureSet);
    }


//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        if (!super.equals(o)) return false;
//        AuthElement that = (AuthElement) o;
//        return Objects.equals(name, that.name) &&
//                authElementType == that.authElementType &&
//                authFilterParamType == that.authFilterParamType &&
//                Objects.equals(auth, that.auth) &&
//                Objects.equals(filter, that.filter) &&
//                Objects.equals(authElementMeasureSet, that.authElementMeasureSet);
//    }


//    public int hashCode1() {
//        return Objects.hash(super.hashCode(), name, authElementType, authFilterParamType, auth, filter, authElementMeasureSet);
//    }

    @Override
    public int hashCode() {
        String measCodes = this.getAuthMeasCodes();
        return Objects.hash(code, measCodes, authElementType, authFilterParamType, auth, filter, authElementMeasureSet);
    }

    @Override
    public String toString() {
        return "AuthElement{" +
                "creator='" + creator + '\'' +
                '}';
    }
}

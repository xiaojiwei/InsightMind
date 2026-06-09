package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-05-23
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class OperateGrantConfig implements Serializable {


    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 库名
     */
    private String schemaName;

    /**
     * 授权维度名称
     */
    private String name;

    /**
     * 授权维度key对应的列名
     */
    private String grantColumnKey;

    /**
     * 授权维度名称对应的列名
     */
    private String grantColumnValue;

    /**
     * 授权类型
     * 0 - 维度列精确授权，获取维值的时候直接获取当前列
     * 1 - 组织架构授权, 获取维值时候，获取当前列以及当前列的所有下级的列值，比如获取区域经理的上下文信息时，除了拿到区域code，还要拿到区域下面所有的城市、门店code
     */
    private Integer grantType;


    /**
     * 组织架构类型
     * 参见:OrganizationType
     */
    private Integer orgType;

    /**
     * 数据源
     * 0 - mysql
     * 1 - doris
     */
    private Integer dataSource;
}

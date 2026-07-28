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
 * @since 2022-01-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Department implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 飞书部门ID
     */
    private Integer feishuDeptId;

    /**
     * 部门全称
     */
    private String fullname;

    /**
     * 公司ID
     */
    private Integer companyId;

    /**
     * id路径
     */
    private String idPath;

    /**
     * 名称路径
     */
    private String namePath;

    /**
     * 部门等级
     */
    private Integer deptLevel;

    /**
     * 部门ID
     * 注意不是主键
     */
    private Integer departmentId;

    /**
     * 父级部门ID
     */
    private Integer parentId;


    /**
     * 部门CODD
     */
    private String code;


    /**
     * 员工数量
     */
    private Integer userNum;

}

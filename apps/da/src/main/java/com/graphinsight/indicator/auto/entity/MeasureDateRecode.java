package com.graphinsight.indicator.auto.entity;

import java.util.Date;

/**
 * Table: measure_date_recode
 */
public class MeasureDateRecode {
    /**
     * Column: id
     * Type: BIGINT UNSIGNED
     * Remark: 唯一主键
     */
    private Long id;

    /**
     * Column: m_code
     * Type: VARCHAR(255)
     * Remark: 指标编码
     */
    private String mCode;

    /**
     * Column: m_name
     * Type: VARCHAR(255)
     * Remark: 指标名称
     */
    private String mName;

    /**
     * Column: m_data
     * Type: VARCHAR(255)
     * Remark: 值
     */
    private String mData;

    /**
     * Column: date_type
     * Type: VARCHAR(255)
     * Remark: month 月维度 day 日维度
     */
    private String dateType;

    /**
     * Column: date_desc
     * Type: VARCHAR(255)
     * Remark: 时间内容
     */
    private String dateDesc;

    /**
     * Column: create_date
     * Type: DATETIME
     * Remark: 创建时间
     */
    private Date createDate;

    /**
     * Column: creator
     * Type: VARCHAR(255)
     * Remark: 创建人
     */
    private String creator;

    /**
     * Column: update_date
     * Type: DATETIME
     * Remark: 修改时间
     */
    private Date updateDate;

    /**
     * Column: updater
     * Type: VARCHAR(255)
     * Remark: 修改人
     */
    private String updater;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getmCode() {
        return mCode;
    }

    public void setmCode(String mCode) {
        this.mCode = mCode == null ? null : mCode.trim();
    }

    public String getmName() {
        return mName;
    }

    public void setmName(String mName) {
        this.mName = mName == null ? null : mName.trim();
    }

    public String getmData() {
        return mData;
    }

    public void setmData(String mData) {
        this.mData = mData == null ? null : mData.trim();
    }

    public String getDateType() {
        return dateType;
    }

    public void setDateType(String dateType) {
        this.dateType = dateType == null ? null : dateType.trim();
    }

    public String getDateDesc() {
        return dateDesc;
    }

    public void setDateDesc(String dateDesc) {
        this.dateDesc = dateDesc == null ? null : dateDesc.trim();
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator == null ? null : creator.trim();
    }

    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    public String getUpdater() {
        return updater;
    }

    public void setUpdater(String updater) {
        this.updater = updater == null ? null : updater.trim();
    }
}
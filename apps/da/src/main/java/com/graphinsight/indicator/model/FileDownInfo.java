package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.FileDownStatus;
import lombok.Data;

import java.math.BigInteger;
import java.util.Set;

/**
 * 文件下载task
 */
@Data
public class FileDownInfo {

    /**
     * 下载唯一ID
     */
    private String downloadId;

    /**
     * 运行信息 失败信息等
     */
    private String message;

    /**
     * 已完成条数
     */
    private BigInteger progress = BigInteger.valueOf(0);

    /**
     * 文件下载运行状态
     */
    private FileDownStatus fileDownStatus = FileDownStatus.WAIT;

    /**
     * 查询参数
     */
    private BuildSqlTuple tuple;

    /**
     * 页面选择的维度
     */
    private Set<Dimension> choiceDimensionSet;

    /**
     * 页面选择的指标
     */
    private Set<Measure> choiceMeasureSet;

    /**
     * 用户唯一标识
     */
    private String ldap;

    /**
     * 查询下载数据sql
     */
    private String sql;

    /**
     * count sql
     */
    private String countSql;

    /**
     * 总数
     */
    private BigInteger count = BigInteger.valueOf(1);

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 是否是明细下载
     */
    private boolean measDetail;

    /**
     * 上传bos后的唯一标识
     */
    private String fileKey;

}

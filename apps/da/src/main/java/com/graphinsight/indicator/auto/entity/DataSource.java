package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 数据源表
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-09-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DataSource implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 唯一主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * cobe状态下的唯一标识
     */
    private String code;

    /**
     * 创建时间
     */
    private LocalDateTime createDate;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 修改时间
     */
    private LocalDateTime updateDate;

    /**
     * 修改人
     */
    private String updater;

    /**
     * 缓存策略
     */
    private Integer cacheStrategy;

    /**
     * 数据图表类型
     */
    private Integer chartType;

    /**
     * 数据源ID
     */
    private Long dataSourceId;

    /**
     * 是否下载文件
     */
    private Integer downFile;

    /**
     * 只返回sql，无数据
     */
    private Integer isOnlySql;

    /**
     * 在线状态
     */
    private Integer lineStatus;

    /**
     * 指标下钻标识
     */
    private Integer measureDetail;

    /**
     * 数据源名称
     */
    private String name;

    /**
     * 数据源操作类型
     */
    private Integer operaType;

    /**
     * 当前页
     */
    private Integer pageNo;

    /**
     * 页大小
     */
    private Integer pageSize;

    /**
     * 是否分页
     */
    private Integer pageable;

    /**
     * 请求分页数的唯一标识
     */
    private String queryCountId;

    /**
     * 备注
     */
    private String remarks;

    /**
     * 数据源设置
     */
    private String settings;

    /**
     * 数据源类型
     */
    private Integer sourceType;

    /**
     * 任务Id
     */
    private String taskId;

    /**
     * 唯一访问标识
     */
    private String traceId;

    /**
     * 唯一主键
     */
    private Long dataBaseInfoId;

    /**
     * 唯一主键
     */
    private Long folderId;

    /**
     * 唯一主键
     */
    private Long spaceId;


}

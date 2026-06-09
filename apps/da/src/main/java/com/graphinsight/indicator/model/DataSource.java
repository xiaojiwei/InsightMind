package com.graphinsight.indicator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.model.vo.AiFrontFormatVo;
import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;
import org.hibernate.annotations.CascadeType;

import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.Table;
import javax.persistence.*;
import java.util.*;

/**
 * 数据源描述
 * 为多维分析的核心模型，承载数据源存储、检索功能。
 */
@Entity
@Table
@DynamicInsert
@DynamicUpdate
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(value = {"handler", "hibernateLazyInitializer", "fieldHandler"})
@org.hibernate.annotations.Table(appliesTo = "data_source", comment = "数据源表")
public class DataSource extends BaseModel {

    @Transient
    private String md5;

    /**
     * 数据源名称
     */
    @Column(columnDefinition = "varchar(255) COMMENT '数据源名称'")
    private String name;

    /**
     * 唯一访问标识
     */
    @Column(columnDefinition = "varchar(255) COMMENT '唯一访问标识'")
    private String traceId;

    /**
     * 数据源类型
     *
     * @see DataSourceType
     */
    @Column(columnDefinition = "int(11) COMMENT '数据源类型'")
    private DataSourceType sourceType = DataSourceType.INDICATOR;

    /**
     * 数据源操作类型
     *
     * @see DataOprType
     */
    @Column(columnDefinition = "int(11) COMMENT '数据源操作类型'")
    private DataOprType operaType = DataOprType.AGGREGATION_TABLE_OPERATION;

    /**
     * 当数据源为非指标平台时，需要提供数据链接类型。
     */
    @OneToOne(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private DataBaseInfo dataBaseInfo;

    /**
     * 数据源ID
     */
    @Column(columnDefinition = "bigint(11) COMMENT '数据源ID'")
    private Long dataSourceId;

    /**
     * 请求分页数的唯一标识
     */
    @Column(columnDefinition = "varchar(255) COMMENT '请求分页数的唯一标识'")
    private String queryCountId;

    /**
     * 只返回sql，无数据
     */
    @Column(columnDefinition = "int(1) COMMENT '只返回sql，无数据'")
    private boolean isOnlySql;

    /**
     * 在线状态
     * ON("上线"),
     * OFF("下线");
     */
    @Column(columnDefinition = "int(1) COMMENT '在线状态'")
    private LineStatus lineStatus = LineStatus.ON;

    /**
     * 数据图表类型
     *
     * @see ChartType
     */
    @Column(columnDefinition = "int(1) COMMENT '数据图表类型'")
    private ChartType chartType = ChartType.TABLE;

    /**
     * 是否开启小计
     */
    @Column(columnDefinition = "int(1) COMMENT '是否开启小计'")
    private boolean totalSub = false;

    /**
     * 是否下开启总计
     */
    @Column(columnDefinition = "int(1) COMMENT '是否开启总计'")
    private boolean totalSum = false;

    /**
     * 是否下载文件
     */
    @Column(columnDefinition = "int(1) COMMENT '是否下载文件'")
    private boolean downFile = false;

    /**
     * 当前页
     */
    @Column(columnDefinition = "int(10) COMMENT '当前页'")
    private Integer pageNo;

    /**
     * 任务Id
     */
    @Column(columnDefinition = "varchar(255) COMMENT '任务Id'")
    private String taskId;

    /**
     * 是否分页
     */
    @Column(columnDefinition = "int(1) COMMENT '是否分页'")
    private boolean pageable = false;

    /**
     * 所属空间ID
     */
    @Transient
    private Long spaceId;

    /**
     * 缓存策略
     *
     * @see CacheStrategy
     */
    @Column(columnDefinition = "int(11) COMMENT '缓存策略'")
    private CacheStrategy cacheStrategy = CacheStrategy.OVERWRITE;

    /**
     * 页大小
     */
    @Column(columnDefinition = "int(11) COMMENT '页大小'")
    private Integer pageSize = 20;

    /**
     * 数据源设置
     */
    @Column(length = 3000, columnDefinition = "varchar(3000) COMMENT '数据源设置'")
    private String settings;

    /**
     * 备注
     */
    @Column(columnDefinition = "varchar(255) COMMENT '备注'")
    private String remarks;

    /**
     * 指标下钻标识
     */
    @Column(columnDefinition = "int(1) COMMENT '指标下钻标识'")
    private boolean measureDetail;

    /**
     * 查询权限人名称
     */
    @Transient
    private String username;

    /**
     * 排序字段
     */
    @Transient
    private List<Order> detailOrderList = new LinkedList<Order>();

    /**
     * 指标、维度的所有配置信息
     */
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @Fetch(FetchMode.SUBSELECT)
    @JoinColumn(name = "data_source_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private List<BaseConfigure> configureList = new LinkedList<>();

    /**
     * 筛选项
     */
    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(name = "data_source_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private List<Filter> filterList = new LinkedList<>();

    /**
     * 筛选树结构
     */
//    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
//    @Cascade({org.hibernate.annotations.CascadeType.ALL})
//    @JoinColumn(name="data_source_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @Transient
    private List<FilterTree> filterTreeList = new LinkedList<FilterTree>();

    /**
     * 所属文件夹
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JsonIncludeProperties({"id", "name"})
    @Cascade({CascadeType.SAVE_UPDATE})
    @JoinColumn(name = "folder_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Folder folder;

    /**
     * 工作空间
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JsonIncludeProperties({"id", "name"})
    @Cascade({org.hibernate.annotations.CascadeType.SAVE_UPDATE})
    @JoinColumn(name = "space_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Space space;

    /**
     * 指标字段排序情况
     */
    @Transient
    private List<MeasureDetailColumnConfigure> detailColumnList = new LinkedList<>();

    /**
     * 指标字段排序情况
     */
    @Transient
    private boolean directQuery = false;

    /**
     * 行汇总
     */
    @Transient
    private boolean rowSum = false;

    /**
     * 列汇总
     */
    @Transient
    private boolean colSum = false;

    /**
     * 是否进行dsl计算
     */
    @Transient
    private boolean dsl = false;

    @Transient
    private boolean dateExit = false;
    @Transient
    private boolean natureTimeDefault = true;
    @Transient
    private boolean dimDateDefault = false;

    @Transient
    private boolean exitDateInfo = false;

    @Transient
    private boolean chartShow = false;

    @Transient
    private boolean dataCache = false;
    @Transient
    private Integer limitNum = 1000;

    @Transient
    private Boolean dataRange = true;

    @Transient
    private Boolean dataAllRange = true;
    @Transient
    private String orderType = "";

    @Transient
    private Set<String> removeWordSet = new HashSet<>();

    @Transient
    private Integer queryType;
    @Transient
    private Integer searchId;
    @Transient
    private String contentCode = "";
    @Transient
    private boolean isSingle = false;
    @Transient
    private boolean isSingleMeas = false;
    @Transient
    private Object ratioRangeTime = null;

    @Transient
    private Boolean isDimDateFilter = false;

    @Transient
    private Boolean isDimDate = false;

    @Transient
    private Boolean isDefaultFilter = false;

    @Transient
    private boolean isShowRatio = false;

    @Transient
    private boolean isData = true;

    @Transient
    private Boolean exitOrder = false;
    @Transient
    private Boolean isSureOrder = false;

    @Transient
    private String wordText;
    @Transient
    private Boolean useCache = true;

    @Transient
    private List<String> unKnowList = new ArrayList<>();

    @Transient
    private List<String> noDataRangeList = new ArrayList<>();

    // 识别到的所有血缘
    @Transient
    private Set<Integer> boolSet = new HashSet<>();
    // 识别到指定血缘
    @Transient
    private Set<Integer> boolValidSet = new HashSet<>();
    @Transient
    Map<String, Object> baseInfoMap = new HashMap<>();


    @Transient
    private String measDealType;

    @Transient
    private Map<String, BaseConfigure> measConfMap = new LinkedHashMap<>();
    @Transient
    private Map<String, BaseConfigure> dimConfMap = new LinkedHashMap<>();

    @Transient
    private List<BaseConfigure> measConfList = new LinkedList<>();
    @Transient
    private List<BaseConfigure> dimConfList = new LinkedList<>();
    @Transient
    private List<BaseConfigure> ratioConfigList = new LinkedList<>();

    @Transient
    private List<BaseConfigure> headerConfList = new LinkedList<>();


    @Transient
    private List<BaseConfigure> deleteDim = new LinkedList<>();
    @Transient
    private List<BaseConfigure> deleteMesa = new LinkedList<>();
    @Transient
    private List<BaseConfigure> addDim = new LinkedList<>();

    @Transient
    private Map<String, BaseConfigure> configureMap = new LinkedHashMap<>();

    @Transient
    private Map<String, Filter> filterMap = new LinkedHashMap<>();

    // 血缘信息
    @Transient
   private String detailSql = "";
    @Transient
    private String routeType = "nlp";
    @Transient
    private Integer tableId = 0;

    @Transient
    private boolean isBoard = false;
//    @Transient
//    private WordSyntaxVo wordSyntaxVo;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        DataSource that = (DataSource) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(this.getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, this.getId());
    }

    public void initCreate() {
        super.initCreate();
    }
}

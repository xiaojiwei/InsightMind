package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.ExecutorPlatform;
import com.graphinsight.indicator.enums.RatioColumnType;
import com.graphinsight.indicator.enums.RatioType;
import com.graphinsight.indicator.enums.RatioValueType;
import com.graphinsight.indicator.service.impl.ChartQueryServiceImpl;
import com.graphinsight.indicator.util.StringUtil;
import lombok.Data;
import org.apache.xpath.operations.Bool;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.*;

/**
 * 构建Sql参数Tuple
 */
@Data
public class BuildSqlTuple implements Serializable {

    /**
     * 数据源Id
     */
    private Long dataSourceId;

    /**
     * 执行引擎平台
     */
    ExecutorPlatform platform;

    /**
     * 是否含有null占位列
     */
    private boolean isNullColumn;

    /**
     * 本次查询唯一ID
     */
    private String taskId;

    /**
     * 可执行sql一共有3类，count、列表、聚合（max、min、avg）。
     * 是否是CountSql
     */
    private boolean isCountSql;

    /**
     * 是否是聚合函数sql
     */
    private boolean isAggSql;

    /**
     * 分页-开始行
     */
    private Integer startPage;

    /**
     * 分页-结束行
     */
    private Integer endPage;

    /**
     * 指标下钻标识
     */
    private boolean measureDetail = false;

    /**
     * 表格类同环比类型zx
     */
    private RatioType ratioType;


    /**
     * ratio 同环比筛选模式
     */
    private Filter ratioFilter;

    /**
     * 同环比日期维度
     */
    private Dimension radioDim;

    /**
     * 唯一日期同环比维度
     */
    private Dimension onlyRadioDim;

    /**
     * fixed 固定筛选条件
     */
    private Boolean fixedFilter;

    public void setRadioDim(Dimension radioDim) {
        this.radioDim = radioDim;
        if (null != radioDim) {
            this.onlyRadioDim = radioDim;
        }
    }

    public Dimension getRadioDim() {
        return radioDim;
    }

    /**
     * 是否需要多重嵌套操作
     * 目前两种情况 1:含有指标操作 2：含有按固定维值排序（按固定维值排序为doris sql解析bug）
     */
    private boolean multipleNesting;


    /**
     * filterTree
     */
    private boolean filterTree;

    /**
     * 所有维度（可能含有派生维度）
     */
    private Set<Dimension> dimensionSet = new LinkedHashSet<>();

    /**
     * 所有指标（可能含有复合、衍生、派生指标）
     */
    private Set<Measure> measureSet = new LinkedHashSet<>();

    /**
     * 用户选择需要显示的所有维度
     */
    private Set<String> displayDimensionCodeSet = new LinkedHashSet<>();

    /**
     * 用户选择需要显示的所有指标
     */
    private Set<String> displayMeasureCodeSet = new LinkedHashSet<>();

    /**
     * 筛选参数
     */
    private QueryParam queryParam;

    /**
     * 目标数据源需要执行的事实表
     * <,>
     */
    private Map<String, List<SingleFactTableSqlAgg>> rootTableMap;

    /**
     * 目标事实表sql
     */
    private Map<String, List<String>> singleFactTableQuerySqlMap;
    /**
     * <sql中from后的表信息编码，事实表信息>
     */
    private Map<String, SingleFactTableSqlAgg> tableFromSqlMap;

    /**
     * 所有维度涉及的表信息
     */
    private List<TableSchemaInfo> dimTableSchemaInfoList;

    /**
     * 事实表和维表交表后的sql
     */
    private String fullJoinGroupSql;

    /**
     * temp 用于跨数据源的内存拼接的sql
     */
    private String fullJoinTempGroupSql;

    private String fullJoinGroupSqlWithLimit;

    /**
     * temp 用于跨数据源的内存拼接的sql
     */
    private String fullJoinTempGroupSqlWithCount;

    private String fullJoinGroupSqlWithCount;
    /**
     * 单个事实表中指标和维度取得完整值后并表聚合tempsql
     */
    private String aggregatorTempSql;
    /**
     * 单个事实表中指标和维度取得完整值后并表聚合
     */
    private String aggregatorSql;

    /**
     * 回显后最终的temp sql
     */
    private String reviewTempSql;

    /**
     * 聚合temp sql
     */
    private String aggregationTempSql;

    /**
     * 表格类型的fullSql
     */
    private String fullTableSql;

    /**
     * 回显后最终的sql
     */
    private String reviewSql;

    /**
     * 含有measOrder的sql
     */
    private String hasMeasOrderSql;
    /**
     * 要引擎执行的sql
     */
    private String executeSql;
    /**
     * 执行引擎
     */
    private ExecutorPlatform executorEngine;

    /**
     * 查询人ldap
     */
    private String userName;

    /**
     * 表别名顺序
     */
    private Integer idx = 0;

    /**
     * dbName
     */
    private String dbName;

    private Integer dbType;

    /**
     * 查询sql执行会话时的优化参数，如并行度、临时变量等。
     */
    private List<String> optimizerParams;

    /**
     * 页面选择的维度
     */
    Set<Dimension> choiceDimensionSet;

    /**
     * 页面选择的指标
     */
    Set<Measure> choiceMeasureSet;

    /**
     * 页面元素中含有权限的指标
     */
    Set<Measure> authMeasureSet = new HashSet<>();

    /**
     * 获取所有需要使用的指标集合（选择、筛选）。
     * @return
     */
    public Set<Measure> getUseMeasureSet() {
        Set<String> useMeasCodeSet = this.getUseAllMeasCode();
        Set<Measure> useMeasSet = new LinkedHashSet<>();
        for (Measure measure : this.measureSet) {

            if (useMeasCodeSet.contains(measure.getCode())) {
                useMeasSet.add(measure);
            }

        }
        return useMeasSet;
    }

    /**
     * 使用的表名
     */
    private Set<String> useTableSet = new HashSet<>();

    private String queryMessage;

    /**
     * 维度code与列名的映射
     */
    private Map<String, String> dimCodeColumnMap = new HashMap<>();

    List<String> statsAllColumnList = new ArrayList<>();

    /**
     * 衍生、派生指标
     */
    private Set<Measure> indDerMeasureSet = new LinkedHashSet<>();

    public void increment() {
        this.idx++;
    }

    public void initIdx() {
        this.idx = 0;
    }

    public BuildSqlTuple() {
        taskId = StringUtil.buildTaskId();
    }

    /**
     * 维度在事实表上对应关系，是否是主维度
     */
    private Map<String, Boolean> factTableDimMasterMap = new HashMap();

    public void setMasterDimInFactTable(String tableName, String factColumn, String dimCode, boolean isMaster) {
        this.getFactTableDimMasterMap().put(tableName + factColumn + dimCode, isMaster);
    }

    public boolean isMasterDimInFactTable(String tableName, String factColumn, String dimCode) {
        String key = tableName + factColumn + dimCode;
        Boolean isMaster = this.getFactTableDimMasterMap().get(key);
        if (null == isMaster) {
            isMaster = false;
        }
        return isMaster;
    }

    //  * 透视表属性
    /**
     * 列轴
     */
    Set<BaseConfigure> columnAxisSet = new LinkedHashSet<>();

    /**
     * 行轴
     */
    Set<BaseConfigure> rowAxisSet = new LinkedHashSet<>();

    public Measure findChoiceMeasure(String measCode, RatioType ratioType,
                                     RatioColumnType ratioColumnType, RatioValueType ratioValueType) {

        Measure meas = null;
        Set<Measure> allMeasureSet = this.getChoiceMeasureSet();
        for (Measure measure : allMeasureSet) {

            boolean isPass = false;
            if (measure.getCode().equals(measCode)) {
                isPass = true;
            }

            if (null != ratioType) {
                isPass = isPass && (ratioType.equals(measure.getRatioType()) || null == measure.getRatioType());
            }

            if (null != ratioColumnType) {
                isPass = isPass && ratioColumnType.equals(measure.getRatioColumnType());
            }

            if (null != ratioValueType) {
                isPass = isPass && ratioValueType.equals(measure.getRatioValueType());
            }

            if (isPass) {
                meas = measure;
                break;
            }

        }

        return meas;

    }

    public Measure findMeasure(String measCode) {

        Measure meas = null;
        Set<Measure> allMeasureSet = this.getMeasureSet();
        for (Measure measure : allMeasureSet) {
            if (measure.getCode().equals(measCode)) {
                meas = measure;
                break;
            }
        }

        return meas;

    }

    public Dimension findDimension(String dimCode) {

        Dimension dim = null;
        Set<Dimension> allDimensionSet = this.getDimensionSet();
        for (Dimension dimension : allDimensionSet) {
            if (dimension.getCode().equals(dimCode)) {
                dim = dimension;
                break;
            }
        }

        return dim;

    }

    /**
     * 所有维度
     */
    public Set<String> getAllDimCode() {

        Set<String> allDimCodeSet = new LinkedHashSet<>();
        if (null != this.dimensionSet && this.dimensionSet.size() > 0) {
            for (Dimension dim : this.dimensionSet) {
                allDimCodeSet.add(dim.getCode());
            }
        }

        return allDimCodeSet;
    }

    /**
     * 可是指标，并且包含筛选项中的指标Code
     */
    public Set<String> getUseAllMeasCode() {

        Set<String> allMeasCodeSet = new LinkedHashSet<>();
        allMeasCodeSet.addAll(this.displayMeasureCodeSet);

        QueryParam queryParam = this.getQueryParam();
        List<Filter> filterList = queryParam.getFilterList();
        if (!CollectionUtils.isEmpty(filterList)) {
            for (Filter filter : filterList) {
                if (ChartQueryServiceImpl.isMeasure(filter)) {
                    allMeasCodeSet.add(filter.getCode());
                }
            }
        }

        return allMeasCodeSet;

    }

    /**
     * 当前登录人的所有指标、维度权限。
     */
    private Set<AuthElement> authElementSet;

    private boolean directQuery = false;
    private boolean chartShow = false;

    /**
     * table类型
     */
    private boolean table = false;

    private boolean pivot = false;

    private boolean memory = false;

    /**
     * 数据库连接信息，从知识图谱读取，优先于默认数据源。
     */
    private DataConnection connection;

    public Set<String> getDimSubtotal() {

        //参数集合
        List<BaseConfigure> allConfigureList = new ArrayList<>();
        Set<BaseConfigure> columnAxisSet = this.getColumnAxisSet();
        Set<BaseConfigure> rowAxisSet = this.getRowAxisSet();
        if (!CollectionUtils.isEmpty(columnAxisSet)) {
            allConfigureList.addAll(columnAxisSet);
        }

        if (!CollectionUtils.isEmpty(rowAxisSet)) {
            allConfigureList.addAll(rowAxisSet);
        }

        Set<String> dimCodeSet = new LinkedHashSet<>();

        boolean hasSubtotal = false;

        for (BaseConfigure baseConfigure : allConfigureList) {

            if (null != baseConfigure.getHasSubtotal() && baseConfigure.getHasSubtotal()) {
                dimCodeSet.add(baseConfigure.getCode());
            }

        }

        return dimCodeSet;
    }

    public boolean hasRowColSubtotal() {
        return this.getQueryParam().isColSum() || this.getQueryParam().isRowSum();
    }

    public boolean hasSubtotal() {

        //参数集合
        List<BaseConfigure> allConfigureList = new ArrayList<>();
        Set<BaseConfigure> columnAxisSet = this.getColumnAxisSet();
        Set<BaseConfigure> rowAxisSet = this.getRowAxisSet();
        if (!CollectionUtils.isEmpty(columnAxisSet)) {
            allConfigureList.addAll(columnAxisSet);
        }

        if (!CollectionUtils.isEmpty(rowAxisSet)) {
            allConfigureList.addAll(rowAxisSet);
        }

        boolean hasSubtotal = false;

        for (BaseConfigure baseConfigure : allConfigureList) {

            if (null != baseConfigure.getHasSubtotal() && baseConfigure.getHasSubtotal()) {
                hasSubtotal = true;
                break;
            }

        }

        return hasSubtotal;

    }

}

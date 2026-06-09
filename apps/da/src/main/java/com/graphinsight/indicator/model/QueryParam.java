package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.CacheStrategy;
import com.graphinsight.indicator.enums.ChartType;
import com.graphinsight.indicator.enums.DataSetType;
import com.graphinsight.indicator.enums.DataSourceType;
import lombok.Data;

import javax.persistence.Transient;
import java.util.*;

@Data
public class QueryParam extends BaseModel {

    public final static Map<ChartType, DataSetType> CHART_TO_DATA = new HashMap<>();
    static {
        CHART_TO_DATA.put(ChartType.TABLE, DataSetType.TABLE);
        CHART_TO_DATA.put(ChartType.LINE, DataSetType.LIST);
        CHART_TO_DATA.put(ChartType.HIST, DataSetType.LIST);
        CHART_TO_DATA.put(ChartType.FUNNEL, DataSetType.LIST);
        CHART_TO_DATA.put(ChartType.CARD, DataSetType.CARD);
        CHART_TO_DATA.put(ChartType.PIE, DataSetType.LIST);
        CHART_TO_DATA.put(ChartType.COMBINE, DataSetType.LIST);
        CHART_TO_DATA.put(ChartType.SYNCFILE, DataSetType.SYNCFILE);
        CHART_TO_DATA.put(ChartType.PIVOT, DataSetType.PIVOT);
    }

    /**
     * 数据源ID
     */
    private Long dataSourceId;

    /**
     * 行汇总
     */
    private boolean rowSum = false;

    /**
     * 列汇总
     */
    private boolean colSum = false;

    /**
     * 是否拥有筛选树
     */
    private boolean isTreeFilter;

    /**
     * 数据源对象
     */
    private DataSource dataSource;

    /**
     * 数据源类型
     * @see DataSourceType
     */
    private DataSourceType sourceType;

    /**
     * 是否开启分页
     */
    private boolean pageable = false;

    /**
     * 请求分页唯一标识
     */
    private String queryCountId;

    /**
     * 是返回sql，无查询
     */
    private boolean isOnlySql;

    /**
     * 是否含有指标操作，如排序、筛选。
     * 备注 原 hasMeasOrder
     */
    private boolean hasMeasOpr;

    /**
     * 是否含有维度自定义排序。
     */
    private boolean hasDimConfigOrder;

    /**
     * 是否含有树结构搜索
     */
    private boolean hasFilterTree;

    /**
     * @see DataSetType
     */
    private DataSetType dataSetType;

    /**
     * @see ChartType
     */
    private ChartType chartType;

    /**
     * 任务Id
     */
    private String taskId;

    /**
     * 设置图标类型
     * @param chartType
     */
    public void setChartType(ChartType chartType) {

        this.chartType = chartType;
        //图标类型决定数据类型
        this.dataSetType = CHART_TO_DATA.get(chartType);

    }

    public void setOnlySql(boolean isOnlySql) {
        this.isOnlySql = isOnlySql;
        if (this.isOnlySql) {
            //如果为sql类型则只回传sql
            this.dataSetType = DataSetType.SQL;
        }
    }

    /**
     * 指标、维度集合
     */
    private List<BaseConfigure> allConfigureList;

    /**
     * X轴维度、指标集合
     */
    private List<BaseConfigure> rowAxisList;

    /**
     * Y轴维度、指标集合
     */
    private List<BaseConfigure> columnAxisList;

    /**
     * 维度集合
     */
    private List<DimensionConfigure> dimensionConfigureList;

    /**
     * 指标集合
     */
    private List<MeasureConfigure> measureConfigureList;

    /**
     * 筛选项查询树
     */
    private List<FilterTree> filterTreeList = new ArrayList<>();

    /**
     * 页面大小
     */
    private Integer pageSize;

    /**
     * 第几页
     */
    private Integer pageNo;

    /**
     * 查询用户
     */
    private String username;

    /**
     * 指标下钻标识
     */
    private boolean measureDetail;

    /**
     *    filters：[{
     *         code:维度或指标id,筛选器数组中的唯一key，一个维度或指标只出现一次。
     *         operation: [{
     *              type:  in-1、not in-2、between（大于、小于-3、 [?greater、less]
     *              data：[],  in、not in中的值列表
     *              begin:开始值,      //between时使用
     *              end：结束值
     *         }]
     *     }]
     */
    private List<Filter> filterList = new LinkedList<>();

    /**
     *    filters：[{
     *         code:维度或指标id,筛选器数组中的唯一key，一个维度或指标只出现一次。
     *         operation: [{
     *              type:  in-1、not in-2、between（大于、小于-3、 [?greater、less]
     *              data：[],  in、not in中的值列表
     *              begin:开始值,      //between时使用
     *              end：结束值
     *         }]
     *     }]
     */
    private List<Filter> treeFilterList = new LinkedList<>();

    /**
     * 排序字段
     */
    private List<Order> orderList = new LinkedList<Order>();

    /**
     * 排序字段
     */
    private List<Order> detailOrderList = new LinkedList<Order>();

    /**
     * 缓存策略
     * @see CacheStrategy
     */
    private CacheStrategy cacheStrategy;

    /**
     * 指标详情排序信息
     */
    private List<MeasureDetailColumnConfigure> detailColumnList = new LinkedList<>();

    /**
     * 当前登录人的所有指标、维度权限。
     */
    private Set<AuthElement> authElementSet;

    private boolean directQuery = false;

    private boolean chartShow = false;

}

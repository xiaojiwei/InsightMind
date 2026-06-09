package com.graphinsight.indicator.constant;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IndicatorConstant {

    public static final String DATA_GPT_AI = "/data/gpt/v1";
    public static final String DATA_GPT_AI_SCREEN = "/data/gpt/v1/screen";

    public static final String API_V1 = "/bi/v1";

    public static final Integer DEF_PAGE_SIZE = 10;

    public static final String ALIAS = "T";

    public static final String BI_NULL = "_Null_";

    public static final String BI_PIVOT_NULL = "_CubeNull_";

    public static final String BI_MEASURE_NULL = "";

    public static final String ROLLUP_CUBE_ALL = "_全部_";

    public static final String MEASSAGE_COPY = "副本";

    public static final String STANDARD_DIMENSION_NO_TABLE = "dimension_value_info";

    public static final String DIM_WITHOUT_TABLE_DOIRS_SCHEMA = "indicator";

    public static final String MEASURE_CODE_PREFIX = "MEAS_";

    public static final String DIMSENSION_CODE_PREFIX = "DIM_";

    public static final String LEVEL_CODE_PREFIX = "LEVEL_";

    public static final String HIERARCHY_CODE_PREFIX = "HIE_";

    public static final String CATEGORY_CODE_PREFIX = "CAT_";


    public static final String MEASURE_GROUP_CODE_PREFIX = "MEASGROUP_";

    public final static String DIM_COLUMN_PREFIX = "_d_alis_";

    public final static String MEASS_COLUMN_PREFIX = "_m_alis_";

    public static final String WARNING_MEASS = "You downloaded too much data,The downfall of the company was caused by secret disclosure.";

    public static final String DIM_WITHOUT_TABLE_PRIMARY_KEY = "v_key";

    public static final String DIM_WITHOUT_TABLE_DIMCOLUMN_NAME = "v_value";

    /**
     * 顶级部门ID
     */
    public static final Integer TOP_DEPT_ID = -1;

    /**
     * 运营架构理想汽车部门ID
     */
    public static final String OPERATE_LIXIANG_DEPT_ID = "0";


    /**
     * 匿名部门ID
     */
    public static final Integer ANONYMOUS_DEPT = -2;

    /**
     * 匿名用户ID
     */
    public static final Integer ANONYMOUS_USER = -1;

    /**
     * 公司级指标
     */
    public static final Integer TOP_DEPT_LEVEL = 0;

    public static final Set<String> MEASURE_DATA_TYPES = new HashSet<>(Arrays.asList("bigint", "largeint", "smallint", "tinyint", "decimal", "double", "float", "int"));

    /**
     * 同步部门定时任务的redis key
     */
    public static final String SYNC_DEPT_LOCK_KEY = "sync_dept_lock_key";

    /**
     * 同步用户定时任务的redis key
     */
    public static final String SYNC_USER_LOCK_KEY = "sync_user_lock_key";

    /**
     * 同步用户数量定时任务的redis key
     */
    public static final String SYNC_DEPT_USERNUM_LOCK_KEY = "sync_dept_user_num_lock_key";

    /**
     * 同步用户数量定时任务的redis key
     */
    public static final String HISTOGRAM_LOCK_KEY = "histogram_lock_key";

    /**
     * 定时检查列信息
     */
    public static final String COLUMN_CHECK_KEY = "sr_column_chekct_key";


    /**
     * 定时检查维表信息
     */
    public static final String DIMTABLE_CHECK_KEY = "dimtable_chekct_key";


    /**
     * doris同步字段列表时 屏蔽的数据类型
     */
    public static final List<String> DATA_TYPE_BLACK_LIST = Arrays.asList("unknown");

    /**
     * 未分类ID
     */
    public static final Integer UNCATEGORIZED_ID = -100;

    /**
     * 多维分析基尼系数计算任务Task队列名称
     */
    public static final String DIMENSION_ANALYSIS_TASK_QUEUE = "dimension_analysis_task_queue";

    /**
     * 多维分析基尼系数计算任务Task队列名称
     */
    public static final String DIMENSION_ANALYSIS_TASK_QUEUE_PROGESS_PREFIX = "dimension_analysis_task_queue_prefix_";


    /**
     * 贡献度Code
     */
    public static final String CONTRIBUION_CODE = "CONTRIBUION_CODE";

    /**
     * 贡献占比Code
     */
    public static final String CONTRIBUION_RATE_CODE = "CONTRIBUION_RATE_CODE";

    /**
     * 贡献占比Code
     */
    public static final String CONTRIBUION_ABS_RATE_CODE = "CONTRIBUION_ABS_RATE_CODE";

    /**
     * 本期值Code
     */
    public static final String CURRENT_VALUE_CODE = "CURRENT_VALUE_CODE";

    /**
     * 基期值Code
     */
    public static final String BASE_VALUE_CODE = "BASE_VALUE_CODE";

    /**
     * 基期值Code
     */
    public static final String DELTA_VALUE_RATE_CODE = "DELTA_VALUE_RATE_CODE";


    /**
     * 自然日维度英文名
     */
    public static final String INDICATOR_NATUAL_DIM_DAY = "indicator_natural_dim_day";

    /**
     * 自然周维度英文名
     */
    public static final String INDICATOR_NATUAL_DIM_WEEK = "indicator_natural_dim_week";

    /**
     * 自然月维度英文名
     */
    public static final String INDICATOR_NATUAL_DIM_MONTH = "indicator_natural_dim_month";

    /**
     * 自然季维度英文名
     */
    public static final String INDICATOR_NATUAL_DIM_SEASON = "indicator_natural_dim_season";

    /**
     * 自然年维度英文名
     */
    public static final String INDICATOR_NATUAL_DIM_YEAR = "indicator_natural_dim_year";

    /**
     * 自然维度集合
     */
    public static final List<String> INDICATOR_NATURAL_DIMENSIONS = Arrays.asList(INDICATOR_NATUAL_DIM_DAY, INDICATOR_NATUAL_DIM_WEEK, INDICATOR_NATUAL_DIM_MONTH, INDICATOR_NATUAL_DIM_SEASON, INDICATOR_NATUAL_DIM_YEAR);

    /**
     * 任务调度功能开关KEY
     */
    public static final String SCHEDULER_CONFIG_KEY = "scheduler.enable";

    /**
     * 指标预警调度key
     */
    public static final String MEASURE_MONITOR_JOB_KEY = "measure_monitor_job_key";

    public static final String ALL_SUBTATAL = "总计";


    /**
     * 指标预警调度分组key
     */
    public static final String MEASURE_MONITOR_JOB_GROUP = "measure_monitor_job_group";


    /**
     * 指标预警开关
     */
    public static Boolean MEASURE_MONITOR_ENABLE = false;

    public static String COL_TYPE_DATETIME = "DATETIME";


    /**
     * 拆解树查询进度 redis key 前缀
     */
    public static final String DISMANTLING_TREE_QUERY_PROGRESS_PREFIX = "dismantling_tree_query_progress_prefix_";
    /**
     * 拆解树查询结果 redis key 前缀
     */
    public static final String DISMANTLING_TREE_QUERY_RESULT_PREFIX = "dismantling_tree_query_result_prefix_";
    /**
     * 进度初始化值
     */
    public static final int PROGRESS_INITIALIZATION = 0;
    /**
     * 进度完成值
     */
    public static final int PROGRESS_COMPLETED = 100;

    public static final String FIN_MONTH = "<!财务月>";
}

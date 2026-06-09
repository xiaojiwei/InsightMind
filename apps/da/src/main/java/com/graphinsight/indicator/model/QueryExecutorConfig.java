package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.SourceType;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 根据不同查询引擎配置的替代符号信息
 *  doris有bitmap类型，mysql hive存储引擎没有
 *
 * @author lidaijiang
 * @date 2021/3/11
 */
@Data
public class QueryExecutorConfig {
    public static final String QUERY_ENGINE_PREFIX = "#engine#";
    public static final String QUOTE = "#q#";
    /**
     * 处理除数为0的函数名称,presto需要指定否则报错，mysql、hive不需要,会自动给null.
     */
    public static final String EXP_DIVIDE_BY_ZERO_BEGIN = "#coalesce_try#";

    /**
     * 处理除数为0时的默认值,presto需要指定否则报错，mysql、hive不需要,会自动给null.
     */
    public static final String EXP_DIVIDE_BY_ZERO_END = "#comma_null#";

    /**
     * 指标类型转换 函数名称,presto需要指定否则报错.
     */
    public static final String EXP_CAST_COLUMN_BEGIN = "#coalesce_cast_try#";

    /**
     * 指标类型转换,presto需要指定否则报错.
     */
    public static final String EXP_CAST_COLUMN_END = "#comma_cast_null#";

    public static Map<String, String> queryEnginePrefixMap = new HashMap<>();
    public static Map<String, String> quoteMap = new HashMap<>();

    /**
     * 类型异常转换
     */
    public static Map<String, String> caseColumnBeginMap = new HashMap<>();

    /**
     * 类型异常转换
     */
    public static Map<String, String> caseColumnEndMap = new HashMap<>();

    /**
     * 处理除数为0的函数名称
     */
    public static Map<String, String> divideByZeroBeginMap = new HashMap<>();

    /**
     * 处理除数为0的默认值
     */
    public static Map<String, String> divideByZeroEndMap = new HashMap<>();

    static {
        /**
         * 在schema/db信息前加怎么样的配置信息
         */
        queryEnginePrefixMap.put(SourceType.DORIS.toString(), "");
        queryEnginePrefixMap.put(SourceType.MYSQL.toString(), "");


        /**
         * 实际由presto执行，而非doris或者mysql计算引擎
         */
        quoteMap.put(SourceType.MYSQL.toString(), "`");
        /**
         * doris用的mysql解析器
         */
        quoteMap.put(SourceType.DORIS.toString(), "`");
        quoteMap.put(SourceType.MYSQL.toString(), "`");
        quoteMap.put("spark", "");
        quoteMap.put("presto", "\"");

        /**
         */
        caseColumnBeginMap.put(SourceType.MYSQL.toString(), "");
        /**
         */
        caseColumnBeginMap.put(SourceType.DORIS.toString(), "");
        caseColumnBeginMap.put(SourceType.MYSQL.toString(), "");
        caseColumnBeginMap.put("spark", "");
        caseColumnBeginMap.put("presto", "COALESCE(TRY(");
        /**
         */
        caseColumnEndMap.put(SourceType.MYSQL.toString(), "");
        /**
         * doris用的mysql解析器
         */
        caseColumnEndMap.put(SourceType.DORIS.toString(), "");
        caseColumnEndMap.put(SourceType.MYSQL.toString(), "");
        caseColumnEndMap.put("spark", "");
        caseColumnEndMap.put("presto", "), 0)");
        /**
         * 实际由presto执行，而非doris或者mysql计算引擎
         */
        divideByZeroBeginMap.put(SourceType.MYSQL.toString(), "");
        /**
         * doris用的mysql解析器
         */
        divideByZeroBeginMap.put(SourceType.DORIS.toString(), "");
        divideByZeroBeginMap.put(SourceType.MYSQL.toString(), "");
        divideByZeroBeginMap.put("spark", "");
        divideByZeroBeginMap.put("presto", "COALESCE(TRY(");
        /**
         * 实际由presto执行，而非doris或者mysql计算引擎
         */
        divideByZeroEndMap.put(SourceType.MYSQL.toString(), "");
        /**
         * doris用的mysql解析器
         */
        divideByZeroEndMap.put(SourceType.DORIS.toString(), "");
        divideByZeroEndMap.put(SourceType.MYSQL.toString(), "");
        divideByZeroEndMap.put("spark", "");
        divideByZeroEndMap.put("presto", "), NULL)");
    }
}

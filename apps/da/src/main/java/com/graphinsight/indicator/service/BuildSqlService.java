package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.BuildSqlTuple;
import com.graphinsight.indicator.model.Measure;
import com.graphinsight.indicator.model.SingleFactTableSqlAgg;
import com.graphinsight.indicator.model.TableSchemaInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface BuildSqlService {

    /**
     * 获取指标明细SQl
     * @param tuple
     * @return
     */
    List<String> buildMeasureDetailRootSqls(BuildSqlTuple tuple);

    /**
     * 根据sourceMap构建 buildRootSql
     * @param tuple 元组
     * @return
     */
    List<String> buildRootSqls(BuildSqlTuple tuple);

    /**
     * 根据数据表来源切分rootSql
     * @param tuple 元组
     * @return
     */
    Map<String, List<SingleFactTableSqlAgg>> getBySourceTable(BuildSqlTuple tuple);

    /**
     * 构建full join group，合并跨数据源的sql处理sql
     * @param tuple 元组
     * @return
     */
    String buildFullJoinGroupSql(BuildSqlTuple tuple);

    /**
     * 根据返还的数据形成临时表进行分类汇总。
     * @param tmpTable 数据临时表名
     * @param tuple 元组
     * @return
     */
    String buildFullJoinGroupSql(String tmpTable, BuildSqlTuple tuple);

    /**
     * 进行衍生指标的计算
     * @param tuple 元组
     * @return
     */
    String buildAggregatorSql(BuildSqlTuple tuple);

    /**
     * 构建含有指标排序操作的sql，同时增加row_number.
     * @param tuple 元组
     * @return
     */
    String buildHasMeasOprSql(BuildSqlTuple tuple);

    /**
     * 构建含有指标排序操作的sql，同时增加row_number.
     * @param tuple 元组
     * @return
     */
    String buildHasMeasDetailOprSql(BuildSqlTuple tuple);

    /**
     * 构建含有指标排序操作的分页sql
     * @param tuple 元组
     * @return
     */
    String buildHasMeasOprPageSql(BuildSqlTuple tuple);

    /**
     * 统计总数sql。
     * @param fullJoinGroupSql
     * @param tuple 元组
     * @return
     */
    String buildCountSql(String fullJoinGroupSql, BuildSqlTuple tuple);


    /**
     * 分页sql。
     * @param tuple 元组
     * @return
     */
    String buildPageSql(BuildSqlTuple tuple);

    /**
     * 维度回显
     * @param tuple 元组
     * @return
     */
    String buildReViewSQL(BuildSqlTuple tuple);

    /**
     * 聚合计算 sum、max、min、aver
     * @param aggregatorSql
     * @param tuple 元组
     * @return
     */
    String buildAggregationSQL(String aggregatorSql, BuildSqlTuple tuple);

    /**
     *
     * @param tuple
     * @return
     */
    List<TableSchemaInfo> getDimTableInfo(BuildSqlTuple tuple);

    /**
     * 预处理指标
     *    1.派生指标中如果拥有派生维度，需要根据筛选条件进行替换。
     * @param measures
     */
    void pretreatment(Collection<Measure> measures);

    /**
     * 检查是否需要开启同环比信息
     * @param tuple 元组
     * @return
     */
    boolean checkOpenRadioInfo(BuildSqlTuple tuple);

    /**
     * 构建RootTable
     * @param measure
     * @param sourceTableMap
     * @param tuple
     * @param useDimCodeSet
     */
    void buildRootTable(Measure measure, Map<String, List<SingleFactTableSqlAgg>> sourceTableMap, BuildSqlTuple tuple, Set<String> useDimCodeSet);

    /**
     * 构建sql
     * @param tuple 元组
     * @return
     */
    String buildRadioSql(BuildSqlTuple tuple, String sql);

}

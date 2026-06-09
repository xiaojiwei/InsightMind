package com.graphinsight.indicator.util;

import com.google.common.base.Joiner;
import com.graphinsight.indicator.model.dto.BuildSqlParam;
import com.graphinsight.indicator.model.dto.ColumnItemExp;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.internal.FormatStyle;
import org.junit.Assert;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/3/10
 * Desc:
 */
@Slf4j
public class BuildSqlUtil {

    private static final String OUT_LAYER_SQL = "SELECT ${COLUMNS_ALIAS} FROM ( ${ROOT_SQL} ) AS T0 ;";

    private static final String ROOT_SQL = "SELECT ${COLUNMS} FROM ${SCHEMA}.${TABLE_NAME} ${WHRER_CONDITIONS} ";

    private static final String COLUMNS_PLACEHOLDER = "${COLUNMS}";

    private static final String TABLE_NAME_PLACEHOLDER = "${TABLE_NAME}";

    private static final String WHRER_CONDITIONS_PLACEHOLDER = "${WHRER_CONDITIONS}";

    private static final String SCHEMA_PLACEHOLDER = "${SCHEMA}";

    private static final String COLUMNS_ALIAS = "${COLUMNS_ALIAS}";

    private static final String ROOT_SQL_TEMPLATE = "${ROOT_SQL}";


    /**
     * 是否含有sql注入，返回true表示含有
     *
     * @param obj
     * @return
     */
    public static boolean containsSqlInjection(Object obj) {
        Pattern pattern = Pattern.compile(
                "\\b(exec|insert|select|drop|grant|alter|delete|update|chr|mid|master|truncate|char|declare)");
        Matcher matcher = pattern.matcher(obj.toString());
        return matcher.find();
    }

    public static String buildSql(BuildSqlParam buildSqlParam){
        Assert.assertNotNull("参数不能为空",buildSqlParam);
        Assert.assertNotNull("事实表不能为空",buildSqlParam.getFactTable());
        Assert.assertNotNull("列信息不能为空",buildSqlParam.getColumnExps());

        String factTable = buildSqlParam.getFactTable();
        List<ColumnItemExp> columnExps = buildSqlParam.getColumnExps();
        List<String> columnAggFuns = columnExps.stream().map(ColumnItemExp::convertAggFun).collect(Collectors.toList());
        Joiner joiner = Joiner.on(",");
        String rootColumns = joiner.join(columnAggFuns);

        List<String> columnAlias = columnExps.stream().map(ColumnItemExp::getColumnAlias).collect(Collectors.toList());
        Joiner aliasJoiner = Joiner.on(",");
        String alias = aliasJoiner.join(columnAlias);

        StringBuilder whereConditionBuilder = new StringBuilder();
        for (int i = 0; i < columnExps.size(); i++) {
            ColumnItemExp ce = columnExps.get(i);
            if (StringUtils.hasLength(ce.getWhereCondition())){
                whereConditionBuilder.append(ce.getWhereCondition());
                whereConditionBuilder.append(" ");
                if (i != columnExps.size() -1){
                    whereConditionBuilder.append(ce.getAndOr());
                    whereConditionBuilder.append(" ");
                }
            }
        }
        String whereConditions = whereConditionBuilder.toString();
        if (StringUtils.hasText(whereConditions)){
            whereConditions = " where " + whereConditions;
        }
        String sql = ROOT_SQL;
        String rootSql = sql.replace(COLUMNS_PLACEHOLDER, rootColumns)
                .replace(WHRER_CONDITIONS_PLACEHOLDER, whereConditions)
                .replace(TABLE_NAME_PLACEHOLDER, factTable)
                .replace(SCHEMA_PLACEHOLDER, buildSqlParam.getSchema());
        if (buildSqlParam.isLimit0()){
            rootSql += " limit 0 ";
        }

        String outSql = OUT_LAYER_SQL;
        String replace = outSql.replace(COLUMNS_ALIAS, alias)
                .replace(ROOT_SQL_TEMPLATE, rootSql);
        String format = FormatStyle.BASIC.getFormatter().format(replace);
        log.info("sql:{}",format);
        return replace;
    }
}

package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.enums.SqlAggFunType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import lombok.Data;

/**
 * Date: 2022/3/10
 * Desc:
 */
@Data
public class ColumnItemExp {

    private String columnName;

    private SqlAggFunType sqlAggFunType;

    private String columnExp;

    private String whereCondition;

    private String andOr = "and";

    private String alias;

    public String getColumnAlias(){
        return "SUM ( `" + alias + "` )";
    }

    public ColumnItemExp(String columnName, SqlAggFunType sqlAggFunType) {
        this.columnName = columnName;
        this.alias = "alias_" + columnName;
        this.sqlAggFunType = sqlAggFunType;

    }

    /**
     *  COUNT(2,"count"),
     *     MAX(3, "max"),
     *     MIN(4, "min"),
     *     AVG(5, "avg"),
     * @return
     */
    public String convertAggFun(){
        String result = "";
        switch (sqlAggFunType){
            case MAX:
            case MIN:
            case COUNT:
            case AVG:
            case STDDEV:
            case SUM:
                result = sqlAggFunType.name() + "(`"+ columnName +"`) AS " + alias;
                break;
            case DISTINCTCOUNT:
                result = "COUNT (DISTINCT `"+ columnName +"`) AS " + alias;
                break;
            default:
                throw IndicatorParamNotValidException.error("暂时不支持函数:" + sqlAggFunType.getDesc());
        }
        return result;
    }


}

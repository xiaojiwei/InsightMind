package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.auto.entity.Dimension;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MeasureMonitorDimGroupQueryResult {

    private Boolean trigger = Boolean.FALSE;

    private String realValue;

    private String dimGroupKey;

    private String[] dates;

    private String[] values;


    public String genCompareDateDesc(){
        String res = "";
        if (dates[0]!=null && values[0]!=null){
            res += dates[0]+" 为 "+values[0] + " ";
        }
        if (dates[1]!=null && values[1]!=null){
            res += dates[1]+" 为 "+values[1]+ " ";
        }
        return res;
    }
}

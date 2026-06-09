package com.graphinsight.indicator.lax.measopt;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Author: lixiaolong
 * Date: 2023/6/5
 * Desc:
 */
public class IndicatorTest {

    private static final String NEWLINE = "\n";

    private static Map<String, Object> dataMap = new HashMap<>();

    {
        dataMap.put("[MEAS_a]","1");
        dataMap.put("[MEAS_b]","2");
    }


    @Test
    public void test2(){
        System.out.println(value("[MEAS_a]+2"));
        System.out.println(value("[MEAS_a]+[MEAS_b]"));
        System.out.println(value("[MEAS_a] / ([MEAS_a]+[MEAS_b])"));
    }



    @Test
    public void test1(){
        System.out.println(value("1+2"));
        System.out.println(value("1+(2*3)"));
        System.out.println(value("2*(2*3)/(2+2)"));
        System.out.println(value("a=4" + NEWLINE + "a+1"));

    }


    private Object value(String oriText){
        Node analysis = Runner.grammaAnalysis(oriText + NEWLINE,dataMap);
        return analysis.value();

    }

}

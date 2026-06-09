package com.graphinsight.indicator.lax.ifelse.iffunction;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Author: lixiaolong
 * Date: 2023/6/6
 * Desc:
 */
public class IFElseTest {
    private static final String NEWLINE = "\n";

    private static Map<String, Object> dataMap = new HashMap<>();

    {
        dataMap.put("[MEAS_a]","1");
        dataMap.put("[MEAS_b]","2");
    }


    @Test
    public void test2(){
        System.out.println("[MEAS_a] = 1, [MEAS_b] = 2");
        System.out.println("[MEAS_a] + [MEAS_b] = " + value("[MEAS_a]+[MEAS_b]"));
        System.out.println("[MEAS_a] / ([MEAS_a]+[MEAS_b]) = " + value("[MEAS_a] / ([MEAS_a]+[MEAS_b])"));
        System.out.println("较大的指标:" + value("if([MEAS_b] > [MEAS_a], [MEAS_b], [MEAS_a])"));
        System.out.println("较小的指标:" + value("if([MEAS_b] < [MEAS_a], [MEAS_b], [MEAS_a])"));
    }



    @Test
    public void test1(){
        System.out.println(value("1+2"));
        System.out.println(value("1+(2*3)"));
        System.out.println(value("2*(2*3)/(2+2)"));

    }


    private Object value(String oriText){
        Node analysis = Runner.grammaAnalysis(oriText + NEWLINE,dataMap);
        return analysis.numberic();

    }
}

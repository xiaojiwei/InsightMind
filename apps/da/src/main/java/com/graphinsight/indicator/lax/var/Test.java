package com.graphinsight.indicator.lax.var;

/**
 * Author: lixiaolong
 * Date: 2023/6/5
 * Desc:
 */
public class Test {

    private static final String NEWLINE = "\n";


    @org.junit.Test
    public void cal(){
        System.out.println(value("1+2"));
        System.out.println(value("1+(2*3)"));
        System.out.println(value("2*(2*3)/(2+2)"));
        System.out.println(value("a=4" + NEWLINE + "a+1"));

    }


    private Object value(String oriText){
        Node analysis = Runner.grammaAnalysis(oriText + NEWLINE);
        return analysis.value();

    }

}

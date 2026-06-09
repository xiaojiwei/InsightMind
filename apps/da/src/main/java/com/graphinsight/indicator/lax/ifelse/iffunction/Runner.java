package com.graphinsight.indicator.lax.ifelse.iffunction;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.Map;

/**
 * Author: lixiaolong
 * Date: 2023/6/5
 * Desc:
 */
public class Runner {

    public static Node grammaAnalysis(String text){
        return grammaAnalysis(CharStreams.fromString(text));
    }


    public static Node grammaAnalysis(String text,Map<String, Object> dataMap){
        return grammaAnalysis(CharStreams.fromString(text), dataMap);
    }

    public static Node grammaAnalysis(CharStream cs){
        IFElseLexer varLexer = new IFElseLexer(cs);
        IFElseParser varParser = new IFElseParser(new CommonTokenStream(varLexer));

        IFFunctionVisitor visitor = new IFFunctionVisitor();
        return visitor.visit(varParser.gra());
    }


    public static Node grammaAnalysis(CharStream cs, Map<String, Object> dataMap){
        IFElseLexer varLexer = new IFElseLexer(cs);
        IFElseParser varParser = new IFElseParser(new CommonTokenStream(varLexer));

        IFFunctionVisitor visitor = new IFFunctionVisitor(dataMap);
        return visitor.visit(varParser.gra());
    }
}

package com.graphinsight.indicator.lax.measopt;

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
        VarLexer varLexer = new VarLexer(cs);
        VarParser varParser = new VarParser(new CommonTokenStream(varLexer));

        CusVarVisitor visitor = new CusVarVisitor();
        return visitor.visit(varParser.gra());
    }


    public static Node grammaAnalysis(CharStream cs, Map<String, Object> dataMap){
        VarLexer varLexer = new VarLexer(cs);
        VarParser varParser = new VarParser(new CommonTokenStream(varLexer));

        CusVarVisitor visitor = new CusVarVisitor(dataMap);
        return visitor.visit(varParser.gra());
    }
}

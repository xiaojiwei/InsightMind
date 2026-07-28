package com.graphinsight.indicator.lax.ifelse.mu;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.Map;

/**
 * Date: 2023/6/5
 * Desc:
 */
public class Runner {

    public static Value grammaAnalysis(String text){
        return grammaAnalysis(CharStreams.fromString(text));
    }


    public static Value grammaAnalysis(String text,Map<String, Object> dataMap){
        return grammaAnalysis(CharStreams.fromString(text), dataMap);
    }

    public static Value grammaAnalysis(CharStream cs){
        MULexer varLexer = new MULexer(cs);
        MUParser varParser = new MUParser(new CommonTokenStream(varLexer));

        EvalVisitor visitor = new EvalVisitor();
        return visitor.visit(varParser.parse());
    }


    public static Value grammaAnalysis(CharStream cs, Map<String, Object> dataMap){
        MULexer varLexer = new MULexer(cs);
        MUParser varParser = new MUParser(new CommonTokenStream(varLexer));

        EvalVisitor visitor = new EvalVisitor();
        return visitor.visit(varParser.parse());
    }
}

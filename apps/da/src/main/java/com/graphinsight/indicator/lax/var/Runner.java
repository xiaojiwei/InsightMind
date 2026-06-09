package com.graphinsight.indicator.lax.var;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * Author: lixiaolong
 * Date: 2023/6/5
 * Desc:
 */
public class Runner {

    public static Node grammaAnalysis(String text){
        return grammaAnalysis(CharStreams.fromString(text));
    }

    public static Node grammaAnalysis(CharStream cs){
        VarLexer varLexer = new VarLexer(cs);
        VarParser varParser = new VarParser(new CommonTokenStream(varLexer));

        CusVarVisitor visitor = new CusVarVisitor();
        return visitor.visit(varParser.gra());
    }
}

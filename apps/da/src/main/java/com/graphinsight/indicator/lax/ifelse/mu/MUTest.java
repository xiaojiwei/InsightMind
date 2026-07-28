package com.graphinsight.indicator.lax.ifelse.mu;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.Test;

/**
 * Date: 2023/6/6
 * Desc:
 */
public class MUTest {

    @Test
    public void test1(){
        value("if(false, 1 + 1, 2 + 2)");
        value("if(true, 1 + 1, 2 + 2)");
        value("if(1 > 0, 1 + 1, 2 + 2)");
    }

    private Object value(String oriText){
        Value analysis = Runner.grammaAnalysis(oriText);
        return analysis;

    }


    public static void main(String[] args) throws Exception {
        CharStream cs = CharStreams.fromFileName("src/main/java/com/graphinsight/indicator/lax/ifelse/mu/test.mu");
        MULexer muLexer = new MULexer(cs);
        MUParser parser = new MUParser(new CommonTokenStream(muLexer));
        ParseTree tree = parser.parse();
        EvalVisitor visitor = new EvalVisitor();
        Value value = visitor.visit(tree);
        System.out.println(value);
    }
}

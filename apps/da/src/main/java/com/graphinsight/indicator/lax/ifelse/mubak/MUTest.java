package com.graphinsight.indicator.lax.ifelse.mubak;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Date: 2023/6/6
 * Desc:
 */
public class MUTest {

    public static void main(String[] args) throws Exception {
        CharStream cs = CharStreams.fromFileName("src/main/java/com/graphinsight/indicator/lax/ifelse/mu/test.mu");
        MULexer muLexer = new MULexer(cs);
        MUParser parser = new MUParser(new CommonTokenStream(muLexer));
        ParseTree tree = parser.parse();
        EvalVisitor visitor = new EvalVisitor();
        visitor.visit(tree);
    }
}

/***
 * Excerpted from "The Definitive ANTLR 4 Reference",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material, 
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose. 
 * Visit http://www.pragmaticprogrammer.com/titles/tpantlr2 for more book information.
***/

package com.graphinsight.indicator.xlax.lod;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.FileInputStream;
import java.io.InputStream;

public class Test {
    public static void main(String[] args) throws Exception {
//        String inputFile = null;
//        inputFile = "/Users/xiaojiwei/indicator/indicator/src/main/java/com/graphinsight/indicator/xlax/lod/t.expr";
//        InputStream is = System.in;
//        if ( inputFile!=null ) is = new FileInputStream(inputFile);
//        ANTLRInputStream input = new ANTLRInputStream(is);

        String expression = "Calculate([MEAS_mmm],fixed:([DIM_aab],[DIM_aaa]), filters:(([DIM_bbb] in '北京'),([DIM_ddd] in '北京')))*Calculate([MEAS_mmm],fixed:([DIM_aab],[DIM_aaa]))-Calculate([MEAS_ci3])";
        CharStream cs = CharStreams.fromString(expression);
        LodExprLexer lexer = new LodExprLexer(cs);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LodExprParser parser = new LodExprParser(tokens);
        ParseTree tree = parser.prog(); // parse

        EvalVisitor eval = new EvalVisitor();
        Object obj = eval.visit(tree);
        System.out.println("final:" + obj);
    }
}

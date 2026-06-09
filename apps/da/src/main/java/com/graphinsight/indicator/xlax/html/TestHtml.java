/***
 * Excerpted from "The Definitive ANTLR 4 Reference",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material, 
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose. 
 * Visit http://www.pragmaticprogrammer.com/titles/tpantlr2 for more book information.
***/

package com.graphinsight.indicator.xlax.html;

import com.graphinsight.indicator.xlax.html.EvalVisitor;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class TestHtml {
    public static void main(String[] args) throws Exception {
        String inputFile = null;
//        inputFile = "/Users/xiaojiwei/indicator/indicator/src/main/java/com/graphinsight/indicator/xlax/html/t1.expr";
//        InputStream is = System.in;
//        if ( inputFile!=null ) is = new FileInputStream(inputFile);
//        ANTLRInputStream input = new ANTLRInputStream(is);


        String exp = "<p><strong>测试 &nbsp;</strong><a href=\"www.baidu.com\" target=\"_blank\">百度</a> <img src=\"/storage_area/public/config/123456789/1234567890/dataReporting/image/36442bba-bea4-f66c-e465-3a17a1477ce6/1697600880549/产品需求.png\" alt=\"\" data-href=\"\" style=\"\"/></p>";
        CharStream cs = CharStreams.fromString(exp);
        HTMLLexer lexer = new HTMLLexer(cs);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        HTMLParser parser = new HTMLParser(tokens);
        ParseTree tree = parser.htmlDocument(); // parse

        EvalVisitor eval = new EvalVisitor();
        Object obj = eval.visit(tree);
        List<Map> mapLinkedList = eval.mapLinkedList;
        System.out.println("final:" + obj);
    }
}

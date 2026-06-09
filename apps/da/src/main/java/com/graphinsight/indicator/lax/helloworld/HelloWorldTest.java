package com.graphinsight.indicator.lax.helloworld;

import com.graphinsight.indicator.lax.helloworld.gen.HelloLexer;
import com.graphinsight.indicator.lax.helloworld.gen.HelloParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * Author: lixiaolong
 * Date: 2023/6/1
 * Desc:
 */
public class HelloWorldTest {

    public static void main(String[] args) throws Exception {
        HelloLexer lexer = new HelloLexer(CharStreams.fromString("hello parrt"));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        HelloParser parser = new HelloParser(tokens);
        HelloParser.RContext r = parser.r();
        System.out.println(r.toStringTree(parser));

    }
}

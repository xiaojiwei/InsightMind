package com.graphinsight.indicator.xlax.html;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.*;

public class EvalVisitor extends HTMLParserBaseVisitor<List> {

    /** "memory" for our calculator; variable/value pairs go here */
    public List<Map> mapLinkedList = new LinkedList<>();

    @Override
    public List visitHtmlAttribute(HTMLParser.HtmlAttributeContext ctx) {
        Token start = ctx.getStart();
        Token stop = ctx.getStop();

        String src = start.getText();

        ParserRuleContext parent = ctx.getParent();
        if (use(parent) && "src".equalsIgnoreCase(src)) {

            Map<String, String> nodeMap = new HashMap<>();
            nodeMap.put("type", "img");
            nodeMap.put("content", stop.getText());
            mapLinkedList.add(nodeMap);

        }

        return super.visitHtmlAttribute(ctx);
    }

    private boolean use(ParserRuleContext parserRuleContext) {

        List<ParseTree> children = parserRuleContext.children;
        for (ParseTree child : children) {
            if ("IMG".equalsIgnoreCase(child.getText())) {
                return true;
            }
        }

        return false;

    }

    @Override
    public List visitHtmlChardata(HTMLParser.HtmlChardataContext ctx) {

        Token token = ctx.getStart();
        System.out.println(token.getText());

        Map<String, String> nodeMap = new HashMap<>();
        nodeMap.put("type", "text");
        nodeMap.put("content", token.getText());

        mapLinkedList.add(nodeMap);

        return super.visitHtmlChardata(ctx);
    }
}

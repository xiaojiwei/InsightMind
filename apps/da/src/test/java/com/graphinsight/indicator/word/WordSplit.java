package com.graphinsight.indicator.word;

import org.ansj.domain.Result;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.BaseAnalysis;
import org.ansj.splitWord.analysis.DicAnalysis;
import org.nlpcn.commons.lang.tire.domain.Forest;
import org.nlpcn.commons.lang.tire.domain.Value;
import org.nlpcn.commons.lang.tire.library.Library;

import java.util.ArrayList;
import java.util.List;

public class WordSplit {

    public static void main(String[] args) {
        String ruiec = "今天 昨天 本周 3日 三日 3周 三周 三月 3月 3年 三年";
//        String ruiec = "";

        Forest forest = null;
        /**
         * 自定义词库
         */
        List<Value> valueList = new ArrayList<Value>();
        //自定义词、词性。此处指标、维度、维度值都定义成名词。
        Value v = new Value("响应时长", "n", "1000");

        valueList.add(v);
        forest = Library.makeForest(valueList);

        Result result = DicAnalysis.parse(ruiec, forest);
        List<Term> termList = result.getTerms();
        for (Term term : termList) {

            //词  指标、维度值都是名次
            System.out.println(term.getName());
            //词性
            System.out.println(term.getNatureStr());

            System.out.println(term);

        }

//        System.out.println(result);
//        System.out.println("基本分词："+ BaseAnalysis.parse(ruiec));
    }

}

package com.graphinsight.indicator.service.wordNlp;

import com.graphinsight.indicator.service.wordNlp.chain.*;
import com.graphinsight.indicator.service.wordNlp.chain.WordGroupChain;
import com.graphinsight.indicator.service.wordNlp.chain.WordWhereChain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class WordChainFactory {

    @Autowired
    @Qualifier("wordReplaceChain")
    private WordReplaceChain wordReplaceChain;

    @Autowired
    @Qualifier("dateSyntaxChain")
    private DateSyntaxChain dateSyntaxChain;

    @Autowired
    @Qualifier("orderSyntaxChain")
    private OrderSyntaxChain orderSyntaxChain;

    @Autowired
    @Qualifier("groupSyntaxChain")
    GroupSyntaxChain groupSyntaxChain;


    @Autowired
    @Qualifier("opSyntaxChain")
    OpSyntaxChain opSyntaxChain;

    @Autowired
    @Qualifier("wordSplitChain")
    private WordSplitChain wordSplitChain;

    @Autowired
    @Qualifier("wordStandChain")
    private WordStandChain wordStandChain;

    @Autowired
    @Qualifier("wordDiscernChain")
    private WordDiscernChain wordDiscernChain;

    @Autowired
    @Qualifier("wordWhereChain")
    private WordWhereChain wordWhereChain;

    @Autowired
    @Qualifier("wordGroupChain")
    private WordGroupChain wordGroupChain;


    @Autowired
    @Qualifier("wordBloodChain")
    WordBloodChain wordBloodChain;

    @Autowired
    @Qualifier("subConChain")
    SubConChain subConChain;

    @Autowired
    @Qualifier("subConV2Chain")
    SubConV2Chain subConV2Chain;



    @Autowired
    @Qualifier("subConCompareChain")
    SubConCompareChain subConCompareChain;

    @PostConstruct
    public void init() {
        // 句式判断
        wordReplaceChain.setNext(dateSyntaxChain);
        dateSyntaxChain.setNext(orderSyntaxChain);
        orderSyntaxChain.setNext(groupSyntaxChain);
        groupSyntaxChain.setNext(opSyntaxChain);
        opSyntaxChain.setNext(wordSplitChain);
        // 词分割
        wordSplitChain.setNext(wordStandChain);
        // 词标准化
        wordStandChain.setNext(wordDiscernChain);
        // 词匹配
        wordDiscernChain.setNext(wordWhereChain);
        // 词sql条件判断
        wordWhereChain.setNext(wordGroupChain);
        // 词sql分组判断
        wordGroupChain.setNext(wordBloodChain);
        wordBloodChain.setNext(subConChain);
        subConChain.setNext(subConCompareChain);
        subConCompareChain.setNext(null);
    }

    public WordChain wordChain() {
        return wordReplaceChain;
    }


}

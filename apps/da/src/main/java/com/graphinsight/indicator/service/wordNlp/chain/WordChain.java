package com.graphinsight.indicator.service.wordNlp.chain;

import com.graphinsight.indicator.model.vo.WordSyntaxVo;


public interface WordChain {


    WordSyntaxVo handleProcess(WordSyntaxVo wordSyntaxVo);

    void setNext(WordChain wordChain);


}

package com.graphinsight.indicator.service.wordNlp.chain;

import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import lombok.Data;

import java.util.Objects;



@Data
public abstract class AbstractWordChain implements WordChain {

    protected WordChain next;

    @Override
    public WordSyntaxVo handleProcess(WordSyntaxVo wordSyntaxVo) {
        wordSyntaxVo = execProcess(wordSyntaxVo);
        if (Objects.nonNull(next)) {
            return next.handleProcess(wordSyntaxVo);
        }
        return wordSyntaxVo;
    }



    @Override
    public void setNext(WordChain wordChain){
        this.next =  wordChain;
    }

    protected abstract WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo);
}

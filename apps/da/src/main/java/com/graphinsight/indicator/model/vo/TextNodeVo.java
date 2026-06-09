package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.service.wordNlpV2.enums.NodeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TextNodeVo {
    //近一年suv的挂牌量比例是多少
    // 每个车型的挂牌量不大于10万个的车型中，成交金额最多的有多少？
    // 按挂牌量由小到大给出10个各车型名称
   // public Node nodeInfo;
    public String code; // 编码
    public String name; // 指标、维度、维值名称
    public String wordName; // 拆解后的原始词语
    public NodeType nodeType; // 节点类型：指标、维度、维度值、运算符、排序、限制
    public String valueType;// 字符串、数字、日期
    public String wordType; // 词性：名词、动词、形容词、副词、介词、连词、助词、标点、其他
    public Integer wordIndex;
    public Integer deep;
    public String parentWordUnique;
    public List<TextNodeVo> children;

//    @Data
//    @NoArgsConstructor
//    @AllArgsConstructor
//    @Builder
//    public static class Node {
//        public String code; // 编码
//        public String name; // 指标、维度、维值名称
//        public String wordName; // 拆解后的原始词语
//        public NodeType nodeType; // 节点类型：指标、维度、维度值、运算符、排序、限制
//        public String valueType;// 字符串、数字、日期
//        public String wordType; // 词性：名词、动词、形容词、副词、介词、连词、助词、标点、其他
//        public Integer wordIndex; //  原词所在的位置
//        public Integer deep;
//        public String parentWordUnique;
//    }

    @Data
    public static class SubConditionNode {
//        String uniqueKey;
        String type;
//        List<TextNodeVo> nodeList = new ArrayList<>();
        Map<String, List<TextNodeVo>> subMap = new LinkedHashMap<>();
    }

}

package com.graphinsight.indicator.service.wordNlp.chain;

import com.graphinsight.indicator.model.vo.TextNodeVo;
import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import com.graphinsight.indicator.service.wordNlp.WordSyntax;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Component
@Slf4j
public class SubConChain extends AbstractWordChain {


    @Value("${nlp.url:https://da-nlp-dev.inner.chj.cloud/hanlp/dep?text=}")
    private String daNlpUrl;

    private boolean nextNoFlag = false;

    @Override
    protected WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {
        nextNoFlag = false;
        wordSyntaxVo = textParse(wordSyntaxVo);
        return wordSyntaxVo;
    }

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    WordSyntax wordSyntax;

    public WordSyntaxVo textParse(WordSyntaxVo wordSyntaxVo) {

        String currentDatePatter = "(比例|占比|多少倍|百分比)";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(wordSyntaxVo.getOriginalText());

        if (!matcher.find()) {
            return wordSyntaxVo;
        }
        if (matcher.group().equals("百分比")) {
            wordSyntaxVo.setFormatType("ratio");
        }

        Map<String, WordSyntaxVo.WordItem> mapWord = wordSyntaxVo.getWordItemList().stream().collect(Collectors.toMap(WordSyntaxVo.WordItem::getMatchText, item -> item, (ex, re) -> ex));
        TextNodeVo textNodeTree = getTextNode(wordSyntaxVo.getOriginalText(), wordSyntaxVo);


        textParseTest(textNodeTree, mapWord, wordSyntaxVo);
        return wordSyntaxVo;
    }


    public Map<String, List<TextNodeVo>> textParseTest(TextNodeVo textNodeTree, Map<String, WordSyntaxVo.WordItem> mapWord, WordSyntaxVo wordSyntaxVo) {

        //
        TextNodeVo.SubConditionNode subConditionNode = new TextNodeVo.SubConditionNode();
        Map<String, List<TextNodeVo>> subVoMap = findAndRecordNodes(textNodeTree, mapWord, subConditionNode);

        // todo 时间类型 dep == 指标dep的 暂时不处理
        // 如果只有一个，条件作用于分子

        WordSyntaxVo.SubCondition subCondition = new WordSyntaxVo.SubCondition();

        Integer subNum = subConditionNode.getSubMap().size();
        // 如果条件只有1个, 条件作用于分子
        if (subNum == 1) {
            subCond(subConditionNode.getSubMap().values().iterator().next(), Arrays.asList(subConditionNode.getSubMap().keySet().iterator().next().split("~")), mapWord, subCondition);
            wordSyntaxVo.setNumerator(subCondition);
            wordSyntaxVo.getDenominator().setUniqueKey(subCondition.getUniqueKey());
            return subVoMap;
        }

        switch (subConditionNode.getType()) {
            case "parentAll":

                // 前面的作用于分子，后面的作用于分母
                for (Map.Entry<String, List<TextNodeVo>> entry : subConditionNode.getSubMap().entrySet()) {
                    List<String> subKArr = Arrays.asList(entry.getKey().split("~"));
                    if (!Objects.equals(subKArr.get(0), "other")) {

                        if (null == wordSyntaxVo.getNumerator().getUniqueKey()) {
                            subCond(entry.getValue(), subKArr, mapWord, wordSyntaxVo.getNumerator());
                        } else {
                            subCond(entry.getValue(), subKArr, mapWord, wordSyntaxVo.getDenominator());
                        }
                    }
                }
                break;
            case "siblingAll":
                for (Map.Entry<String, List<TextNodeVo>> entry : subConditionNode.getSubMap().entrySet()) {
                    List<String> subKArr = Arrays.asList(entry.getKey().split("~"));
                    // 如果是一个指标，将兄弟节点的条件，也作用于分母
                    if (wordSyntaxVo.getMeasNum() == 1) {
                        if (null == wordSyntaxVo.getNumerator().getUniqueKey()) {
                            subCond(entry.getValue(), subKArr, mapWord, wordSyntaxVo.getNumerator());
                            wordSyntaxVo.getDenominator().setUniqueKey(wordSyntaxVo.getNumerator().getUniqueKey());
                        } else {
                            subCond(entry.getValue(), subKArr, mapWord, subCondition);
                            if (Objects.equals(subCondition.getUniqueKey(), wordSyntaxVo.getNumerator().getUniqueKey())) {
                                wordSyntaxVo.getNumerator().getSonTextList().addAll(subCondition.getSonTextList());
                            }
                        }

                    } else {
                        if (!Objects.equals(subKArr.get(0), "other")) {

                            if (null == wordSyntaxVo.getNumerator().getUniqueKey()) {
                                subCond(entry.getValue(), subKArr, mapWord, wordSyntaxVo.getNumerator());
                            } else {
                                subCond(entry.getValue(), subKArr, mapWord, wordSyntaxVo.getDenominator());
                            }
                        }
                    }
                }
            case "parentSibling":
                for (Map.Entry<String, List<TextNodeVo>> entry : subConditionNode.getSubMap().entrySet()) {
                    List<String> subKArr = Arrays.asList(entry.getKey().split("~"));
                    // 如果是一个指标，将兄弟节点的条件，也作用于分母
                    if (wordSyntaxVo.getMeasNum() == 1) {

                        if (null == wordSyntaxVo.getNumerator().getUniqueKey()) {
                            subCond(entry.getValue(), subKArr, mapWord, wordSyntaxVo.getNumerator());
                            wordSyntaxVo.getDenominator().setUniqueKey(wordSyntaxVo.getNumerator().getUniqueKey());
                        } else {
                            subCond(entry.getValue(), subKArr, mapWord, subCondition);
                            if (Objects.equals(subCondition.getUniqueKey(), wordSyntaxVo.getNumerator().getUniqueKey())) {
                                wordSyntaxVo.getNumerator().getSonTextList().addAll(subCondition.getSonTextList());
                            }
                        }

                    } else {
                        // parent sibling parent 或者 parent sibling
                        if (Objects.equals(subKArr.get(0), "parent")) {
                            if (null == wordSyntaxVo.getNumerator().getUniqueKey()) {
                                subCond(entry.getValue(), subKArr, mapWord, wordSyntaxVo.getNumerator());
                            } else {
                                subCond(entry.getValue(), subKArr, mapWord, wordSyntaxVo.getDenominator());
                            }
                        } else if (Objects.equals(subKArr.get(0), "sibling")) {
                            subCond(entry.getValue(), subKArr, mapWord, wordSyntaxVo.getDenominator());
                        }
                    }
                }
                break;

            //break;
            case "parentOther":
                for (Map.Entry<String, List<TextNodeVo>> entry : subConditionNode.getSubMap().entrySet()) {
                    List<String> subKArr = Arrays.asList(entry.getKey().split("~"));
                    if (Objects.equals(subKArr.get(0), "parent")) {
                        subCond(entry.getValue(), subKArr, mapWord, subCondition);
                        wordSyntaxVo.setNumerator(subCondition);
                        // 分母不设置条件
                        wordSyntaxVo.getDenominator().setUniqueKey(subCondition.getUniqueKey());
                    }
                }
                break;
            case "siblingOther":
                for (Map.Entry<String, List<TextNodeVo>> entry : subConditionNode.getSubMap().entrySet()) {
                    List<String> subKArr = Arrays.asList(entry.getKey().split("~"));
                    if (Objects.equals(subKArr.get(0), "sibling")) {
                        subCond(entry.getValue(), subKArr, mapWord, subCondition);
                        wordSyntaxVo.setNumerator(subCondition);
                        // 分母不设置条件
                        wordSyntaxVo.getDenominator().setUniqueKey(subCondition.getUniqueKey());
                    }
                }
                break;
            case "parentSiblingOther":
                for (Map.Entry<String, List<TextNodeVo>> entry : subConditionNode.getSubMap().entrySet()) {
                    List<String> subKArr = Arrays.asList(entry.getKey().split("~"));

                    if (Objects.equals(subKArr.get(0), "parent")) {
                        if (null == wordSyntaxVo.getNumerator().getUniqueKey()) {
                            subCond(entry.getValue(), subKArr, mapWord, wordSyntaxVo.getNumerator());
                        } else {
                            subCond(entry.getValue(), subKArr, mapWord, wordSyntaxVo.getDenominator());
                        }
                    } else if (Objects.equals(subKArr.get(0), "sibling")) {
                        subCond(entry.getValue(), subKArr, mapWord, wordSyntaxVo.getDenominator());
                    } else {
                        subCond(entry.getValue(), subKArr, mapWord, subCondition);
                        if (!subCondition.getSonTextList().isEmpty()) {
                            if (subCondition.getUniqueKey().equals(wordSyntaxVo.getDenominator().getUniqueKey())) {
                                wordSyntaxVo.getDenominator().getSonTextList().addAll(subCondition.getSonTextList());
                            } else {
                                // 记录一下
                                log.info("more info {}", subCondition);
                            }
                        }
                    }
                }
                break;
        }

        return subVoMap;
    }

    public void subCond(List<TextNodeVo> nodeVoList, List<String> subKArr, Map<String, WordSyntaxVo.WordItem> mapWord, WordSyntaxVo.SubCondition subCondition) {
        subCondition.setSonTextList(new ArrayList<>());
        for (TextNodeVo item : nodeVoList) {
            if (null != mapWord.get(item.getWordName())) {
                WordSyntaxVo.WordItem wordItem = mapWord.get(item.getWordName());
                if (wordItem.getMatchType().equals("date")) {
                    continue;
                }
                subCondition.setUniqueKey(subKArr.get(2) + "~" + subKArr.get(1));
                subCondition.getSonTextList().add(item.getWordName());
            }
        }
    }


    public static Map<String, List<TextNodeVo>> findAndRecordNodes(TextNodeVo root, Map<String, WordSyntaxVo.WordItem> mapWord, TextNodeVo.SubConditionNode subConditionNode) {
        Map<String, List<TextNodeVo>> subMap = new LinkedHashMap<>();


        Set<String> visited = new HashSet<>();
        traverseTree(root, null, mapWord, subMap, visited, subConditionNode);
        // 遍历没有识别到的条件
        traverseTreeOtherCond(root, null, mapWord, subMap, visited, subConditionNode);
        return subMap;
    }

    private static void traverseTree(TextNodeVo currentNode, TextNodeVo parentNode, Map<String, WordSyntaxVo.WordItem> mapWord, Map<String, List<TextNodeVo>> subMap, Set<String> visited, TextNodeVo.SubConditionNode subConditionNode) {
        if (currentNode == null || visited.contains(currentNode.getWordName() + "~" + currentNode.getWordIndex())) {
            return;
        }

        // If the current node is of search type
        if (null != mapWord.get(currentNode.getWordName())
                && null != mapWord.get(currentNode.getWordName()).getMatchType()
                && mapWord.get(currentNode.getWordName()).getMatchType().equals("measure")) {
            visited.add(currentNode.getWordName() + "~" + currentNode.getWordIndex());
            List<TextNodeVo> conditionNodes = new ArrayList<>();
            findConditionNodes(currentNode, mapWord, conditionNodes, visited);

            if (!conditionNodes.isEmpty()) {
                subMap.put("parent" + "~" + currentNode.getWordName() + "~" + currentNode.getWordIndex(), conditionNodes);
                if (subConditionNode.getType() != null && !subConditionNode.getType().equals("parentAll")) {
                    subConditionNode.setType("parentSibling");
                } else {
                    subConditionNode.setType("parentAll");
                }

                subConditionNode.getSubMap().put("parent" + "~" + currentNode.getWordName() + "~" + currentNode.getWordIndex(), conditionNodes);
            }
            List<TextNodeVo> conditionSiblingNodes = findConditionSiblings(currentNode, parentNode, mapWord, visited);
            if (!conditionSiblingNodes.isEmpty()) {
                subMap.put("sibling" + "~" + currentNode.getWordName() + "~" + currentNode.getWordIndex(), conditionSiblingNodes);
                if (subConditionNode.getType() != null && !subConditionNode.getType().equals("siblingAll")) {
                    subConditionNode.setType("parentSibling");
                } else {
                    subConditionNode.setType("siblingAll");
                }

                subConditionNode.getSubMap().put("sibling" + "~" + currentNode.getWordName() + "~" + currentNode.getWordIndex(), conditionSiblingNodes);
            }
        }

        for (TextNodeVo child : currentNode.children) {
            traverseTree(child, currentNode, mapWord, subMap, visited, subConditionNode);
        }
    }

    private static void traverseTreeOtherCond(TextNodeVo currentNode, TextNodeVo parentNode, Map<String, WordSyntaxVo.WordItem> mapWord, Map<String, List<TextNodeVo>> subMap, Set<String> visited, TextNodeVo.SubConditionNode subConditionNode) {
        if (currentNode == null || visited.contains(currentNode.getWordName() + "~" + currentNode.getWordIndex())) {
            return;
        }
        visited.add(currentNode.getWordName() + "~" + currentNode.getWordIndex());
        if (null != mapWord.get(currentNode.getWordName())
                && null != mapWord.get(currentNode.getWordName()).getSqlType()
                && mapWord.get(currentNode.getWordName()).getSqlType().equals("where")) {
            subMap.put("other" + "~" + currentNode.getWordName() + "~" + currentNode.getWordIndex(), Collections.singletonList(currentNode));

            if (subConditionNode.getType() == null) {
                subConditionNode.setType("otherAll");
            } else {
                switch (subConditionNode.getType()) {
                    case "otherAll":
                        subConditionNode.setType("otherAll");
                        break;
                    case "parentAll":
                        subConditionNode.setType("parentOther");
                        break;
                    case "siblingAll":
                        subConditionNode.setType("siblingOther");
                        break;
                    case "parentSibling":
                        subConditionNode.setType("parentSiblingOther");
                        break;
                }

            }


            subConditionNode.getSubMap().put("other" + "~" + currentNode.getWordName() + "~" + currentNode.getWordIndex(), Collections.singletonList(currentNode));
        }

        for (TextNodeVo child : currentNode.children) {
            traverseTreeOtherCond(child, currentNode, mapWord, subMap, visited, subConditionNode);
        }
    }


    private static void findConditionNodes(TextNodeVo node, Map<String, WordSyntaxVo.WordItem> mapWord, List<TextNodeVo> conditionNodes, Set<String> visited) {
        for (TextNodeVo child : node.children) {
            visited.add(child.getWordName() + "~" + child.getWordIndex());
            if (null != mapWord.get(child.getWordName())
                    && null != mapWord.get(child.getWordName()).getSqlType()
                    && mapWord.get(child.getWordName()).getSqlType().equals("where")) {
                conditionNodes.add(child);
            }
            // Recursive call to check deeper levels
            findConditionNodes(child, mapWord, conditionNodes, visited);
        }
    }

    private static List<TextNodeVo> findConditionSiblings(TextNodeVo node, TextNodeVo parentNode, Map<String, WordSyntaxVo.WordItem> mapWord, Set<String> visited) {
        List<TextNodeVo> conditionSiblings = new ArrayList<>();
        if (node != null && parentNode != null) {
            for (TextNodeVo sibling : parentNode.children) {
                if (sibling != node && null != mapWord.get(sibling.getWordName())
                        && null != mapWord.get(sibling.getWordName()).getSqlType()
                        && mapWord.get(sibling.getWordName()).getSqlType().equals("where")) {
                    visited.add(sibling.getWordName() + "~" + sibling.getWordIndex());
                    conditionSiblings.add(sibling);
                }
            }
        }
        return conditionSiblings;
    }


    public TextNodeVo getTextNode(String wordText, WordSyntaxVo wordSyntaxVo) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        String url =daNlpUrl + wordText;
        if (wordSyntaxVo.getMatchTextList() != null && !wordSyntaxVo.getMatchTextList().isEmpty()) {
            url += "&dic=" + wordSyntaxVo.getMatchTextList().stream().collect(Collectors.joining(","));
        }
        TextNodeVo textNodeTree = restTemplate.getForObject(url, TextNodeVo.class);

        if (textNodeTree == null) {
            throw new RuntimeException("请求" + url + "接口异常:");
        }

        return textNodeTree;
    }


}

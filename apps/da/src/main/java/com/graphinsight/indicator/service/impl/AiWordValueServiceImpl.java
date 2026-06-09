package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.mapper.*;
import com.graphinsight.indicator.auto.service.ITSpaceService;
import com.graphinsight.indicator.auto.entity.AiQuestionInfo;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.manager.BloodManager;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.cache.MeasureCache;
import com.graphinsight.indicator.model.vo.*;
import com.graphinsight.indicator.service.AiRecommendQuestionService;
import com.graphinsight.indicator.service.AiWordValueService;
import com.graphinsight.indicator.service.wordNlp.WordDictService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
@EnableScheduling
@Slf4j
public class AiWordValueServiceImpl implements AiWordValueService {


    @Autowired
    WordValuesMapper wordValuesMapper;
    @Autowired
    UserManager userManager;
    @Autowired
    WordDictService wordDictService;

    @Override
    public void addBusiness(AiBusinessVo aiBusinessVo) {
        List<String> keyValueList = Arrays.asList(aiBusinessVo.getKeyValue().split(","));
        checkInfo(aiBusinessVo, keyValueList, true);

        Date curTime = new Date();
        List<WordValues> wordValuesList = new ArrayList<>();
        for (String keyValue : keyValueList) {
            WordValues wordValues = new WordValues();
            wordValues.setKey(aiBusinessVo.getKeyWord());
            wordValues.setValue(keyValue);
            wordValues.setCreateDate(curTime);
            wordValues.setUpdateDate(curTime);
            wordValues.initCreate();
            wordValuesList.add(wordValues);
        }
        wordValuesMapper.insertBatch(wordValuesList);

        wordDictService.init();
    }

    @Override
    public void updateBusiness(AiBusinessVo aiBusinessVo) {
        List<String> keyValueList = Arrays.asList(aiBusinessVo.getKeyValue().split(","));

        Wrapper<WordValues> lambdaQuery = Wrappers.<WordValues>lambdaQuery().select(WordValues::getKey, WordValues::getValue)
                .eq(WordValues::getKey, aiBusinessVo.getKeyWord()).or().in(WordValues::getValue, keyValueList);

        List<WordValues> listValues = wordValuesMapper.selectList(lambdaQuery);
        for (WordValues wordValues : listValues) {
            if (!Objects.equals(wordValues.getKey(), aiBusinessVo.getKeyWord()) && Objects.equals(wordValues.getValue(), aiBusinessVo.getKeyWord())) {
                throw new RuntimeException("关键字，在专业术语汇中存在，不允许，关键字为" + wordValues.getKey());
            }
            if (!Objects.equals(wordValues.getKey(), aiBusinessVo.getKeyWord()) && keyValueList.contains(wordValues.getKey())) {
                String errInfo = "专业术语包含关键字，不允许，关键字为" + wordValues.getKey();
                throw new RuntimeException(errInfo);
            }
            if(!Objects.equals(wordValues.getKey(), aiBusinessVo.getKeyWord())){
                if (keyValueList.contains(wordValues.getValue())) {
                    String errInfo = "已存在相同的术语，已出现在其他关键字中，关键字为" + wordValues.getKey();
                    throw new RuntimeException(errInfo);
                }
            }
        }

        wordValuesMapper.delete(Wrappers.<WordValues>lambdaQuery().eq(WordValues::getKey, aiBusinessVo.getKeyWord()));
        wordValuesMapper.delete(Wrappers.<WordValues>lambdaQuery().in(WordValues::getValue, keyValueList));
        Date curTime = new Date();
        List<WordValues> wordValuesList = new ArrayList<>();
        for (String keyValue : keyValueList) {
            WordValues wordValues = new WordValues();
            wordValues.setKey(aiBusinessVo.getKeyWord());
            wordValues.setValue(keyValue);
            wordValues.setCreateDate(curTime);
            wordValues.setUpdateDate(curTime);
            wordValues.initCreate();
            wordValuesList.add(wordValues);
        }
        wordValuesMapper.insertBatch(wordValuesList);
        wordDictService.init();
    }

    @Override
    public void deleteBusiness(AiBusinessDelVo aiBusinessVo) {
        wordValuesMapper.delete(Wrappers.<WordValues>lambdaQuery().in(WordValues::getKey, aiBusinessVo.getKeyWordList()));
        wordDictService.init();
    }


    @Override
    public IPage<AiBusinessListVo> listBusiness(AiBusinessSearchVo aiBusinessVo) {

        Page<WordValues> page = new Page<>(aiBusinessVo.getPageNo(), aiBusinessVo.getPageSize());
        aiBusinessVo.setKeyWord(aiBusinessVo.getKeyWord().trim());
        IPage<AiBusinessListVo> aiBusinessList = wordValuesMapper.selectPageInfo(page, aiBusinessVo);

        // 通过关键字获取到详细信息
        List<String> updaterList = aiBusinessList.getRecords().stream().map(AiBusinessListVo::getUpdater).collect(Collectors.toList());
        Map<String, User> updaterUserMap = userManager.getUserMapByUsernames(updaterList);

        aiBusinessList.getRecords().forEach(vo -> {
            if (null != updaterUserMap.get(vo.getUpdater())) {
                vo.setUpdaterInfo(updaterUserMap.get(vo.getUpdater()));
            }
        });

        return aiBusinessList;
    }

    @Override
    public PageVO<RecommendListVo> subjectRecommendInfo(com.graphinsight.indicator.model.DataSource dataSource) {
        List<RecommendListVo> recommendVoList = subjectRecommendDefaultInfo(true, 2);
        Long size = Long.valueOf(recommendVoList.size());
        if (size > 0) {
            for (RecommendListVo recommendListVo : recommendVoList) {
                recommendListVo.setText(replaceDate(recommendListVo.getText()));
            }
        }
        
        PageVO<RecommendListVo> recommendPage = new PageVO<>(size, recommendVoList);
        return recommendPage;
    }

    public List<RecommendListVo> subjectRecommendDefaultInfo(Boolean isLimit, Integer limitNum) {

        String userName = UserThreadLocalUtil.getUserName();

        List<AiRecommendQuestionEntity> listQuest = aiRecommendQuestionService.getBaseMapper().selectList(Wrappers.<AiRecommendQuestionEntity>lambdaQuery()
                .eq(AiRecommendQuestionEntity::getCreator, userName));
        if (listQuest.isEmpty()) {
            listQuest = aiRecommendQuestionService.getBaseMapper().selectList(Wrappers.<AiRecommendQuestionEntity>lambdaQuery()
                    .isNull(AiRecommendQuestionEntity::getCreator));
        }

        List<RecommendListVo> recommendVoList = new ArrayList<>();
        if (listQuest.size() >= limitNum) {
            Collections.shuffle(listQuest);
            int index = 0;
            for (AiRecommendQuestionEntity text : listQuest) {
                if (index >= limitNum && isLimit) {
                    break;
                }
                index++;
                RecommendListVo recommendListVo = new RecommendListVo();
                recommendListVo.setText(text.getInfo());
                recommendVoList.add(recommendListVo);
            }
        }
        return recommendVoList;

    }


    @Autowired
    private BloodManager bloodManager;

    @Autowired
    private CacheManager cacheManager;

    @Override
    public PageVO<RecommendListVo> subjectRecommend(com.graphinsight.indicator.model.DataSource dataSource) {
        List<String> recommendList = new ArrayList<>();

        RelatedSet relatedSet = new RelatedSet();
        Boolean isHaseMeas = false;
        if (!dataSource.getMeasConfList().isEmpty()) {
            for (com.graphinsight.indicator.model.BaseConfigure baseConfigure : dataSource.getMeasConfList()) {
                if (null == baseConfigure.getId()) {
                    continue;
                }
                if (baseConfigure.getCode().contains("MEAS")) {
                    isHaseMeas = true;
                    relatedSet.getMeasureSet().add(Math.toIntExact(baseConfigure.getId()));
                } else {
                    relatedSet.getDimensionSet().add(Math.toIntExact(baseConfigure.getId()));
                }
            }
        }
        // 获取血缘关系
        RelatedSet resultRelatedSet = bloodManager.listRelatedSet(relatedSet);

        if (isHaseMeas) {
            for (Integer measId : relatedSet.getMeasureSet()) {
                MeasureCache measureCache = cacheManager.getMeasureCache(measId);
                if (measureCache.getMeasure().getOnline() == 0) {
                    continue;
                }
                String curOneYear = "近一年的";
                String curMonth = "当月的";
                curOneYear += measureCache.getMeasure().getCnName();
                curMonth += measureCache.getMeasure().getCnName();
                recommendList.add(curOneYear);
                recommendList.add(curMonth);
            }
        } else {
            for (Integer measId : resultRelatedSet.getMeasureSet()) {
                MeasureCache measureCache = cacheManager.getMeasureCache(measId);
                if (measureCache.getMeasure().getOnline() == 0) {
                    continue;
                }
                String curOneYear = "近一年的";
                String curMonth = "当月的";
                curOneYear += measureCache.getMeasure().getCnName();
                curMonth += measureCache.getMeasure().getCnName();
                recommendList.add(curOneYear);
                recommendList.add(curMonth);
            }
        }
        Collections.shuffle(recommendList);
        List<RecommendListVo> recommendVoList = new ArrayList<>();
        if (recommendList.size() >= 2) {
            int index = 0;
            for (String text : recommendList) {
                if (index >= 2) {
                    break;
                }
                index++;
                RecommendListVo recommendListVo = new RecommendListVo();
                recommendListVo.setText(text);
                recommendVoList.add(recommendListVo);
            }
        }
        Long size = Long.valueOf(recommendVoList.size());
        PageVO<RecommendListVo> recommendPage = new PageVO<>(size, recommendVoList);
        return recommendPage;
    }

    @Autowired
    ITSpaceService itSpaceService;

    @Autowired
    AiSearchInfoMapper aiSearchInfoMapper;

    @Autowired
    AiSessionInfoMapper aiSessionInfoMapper;

    @Autowired
    AiQuestionInfoMapper aiQuestionInfoMapper;

    @Override
    public PageVO<RecommendListVo> subjectInputRecommend() {
        String userName = UserThreadLocalUtil.getUserName();

        List<AiQuestionInfo> aiSessionInfos = aiQuestionInfoMapper.selectList(Wrappers.<AiQuestionInfo>lambdaQuery()
                .select(AiQuestionInfo::getContent, AiQuestionInfo::getCreateTime)
                .eq(AiQuestionInfo::getReplyType, "success")
                .eq(AiQuestionInfo::getUser, userName)
                .orderByDesc(AiQuestionInfo::getCreateTime)
                .groupBy(AiQuestionInfo::getContent, AiQuestionInfo::getCreateTime).last("limit 20"));
        if (aiSessionInfos.isEmpty()) {
            List<RecommendListVo> recommendDefaultVoList = subjectRecommendDefaultInfo(true, 8);
            Long size = Long.valueOf(recommendDefaultVoList.size());
            if (size > 0) {
                for (RecommendListVo recommendListVo : recommendDefaultVoList) {
                    recommendListVo.setText(replaceDate(recommendListVo.getText()));
                }
            }
            PageVO<RecommendListVo> recommendPage = new PageVO<>(size, recommendDefaultVoList);
            return recommendPage;
        }
        Set<String> exitSet = new HashSet<>();
        int index = 0;
        List<RecommendListVo> recommendVoList = new ArrayList<>();
        for (AiQuestionInfo aiUserSearchVo : aiSessionInfos) {

            if (index >= 8) {
                break;
            }
            if (exitSet.contains(aiUserSearchVo.getContent())) {
                continue;
            }
            index++;
            exitSet.add(aiUserSearchVo.getContent());
            RecommendListVo recommendListVo = new RecommendListVo();
            recommendListVo.setText(aiUserSearchVo.getContent());
            recommendVoList.add(recommendListVo);
        }
        if (recommendVoList.size() < 8) {
            List<RecommendListVo> needAdd = subjectRecommendDefaultInfo(true, 8 - recommendVoList.size());
            recommendVoList.addAll(needAdd);
        }

        Long size = Long.valueOf(recommendVoList.size());
        if (size > 0) {
            for (RecommendListVo recommendListVo : recommendVoList) {
                recommendListVo.setText(replaceDate(recommendListVo.getText()));
            }
        }
        PageVO<RecommendListVo> recommendPage = new PageVO<>(size, recommendVoList);
        return recommendPage;
    }

    @Autowired
    AiRecommendQuestionService aiRecommendQuestionService;


    @Override
    public PageVO<AnalysisListVo> subjectAnalysis() {
        String userName = UserThreadLocalUtil.getUserName();

        List<AiRecommendQuestionEntity> listQuest = aiRecommendQuestionService.getBaseMapper().selectList(Wrappers.<AiRecommendQuestionEntity>lambdaQuery()
                .eq(AiRecommendQuestionEntity::getCreator, userName));
        if (listQuest.isEmpty()) {
            listQuest = aiRecommendQuestionService.getBaseMapper().selectList(Wrappers.<AiRecommendQuestionEntity>lambdaQuery()
                    .isNull(AiRecommendQuestionEntity::getCreator));
        }

        List<AnalysisListVo> analysisListVoList = new ArrayList<>();

        Map<String, AnalysisListVo> titleMap = new HashMap<>();
        for (AiRecommendQuestionEntity authMeasName : listQuest) {

            String textInfo = replaceDate(authMeasName.getInfo());

            if (null != titleMap.get(authMeasName.getType())) {
//                titleMap.get(authMeasName.getType()).getTextList().add(authMeasName.getInfo());
                titleMap.get(authMeasName.getType()).getTextList().add(textInfo);
            } else {
                AnalysisListVo analysisListVo = new AnalysisListVo();
                analysisListVo.setTitle(authMeasName.getTitle());
                analysisListVo.setType(authMeasName.getType());
//                analysisListVo.getTextList().add(authMeasName.getInfo());
                analysisListVo.getTextList().add(textInfo);
                analysisListVoList.add(analysisListVo);
                titleMap.put(authMeasName.getType(), analysisListVo);

            }
        }

        Long size = Long.valueOf(analysisListVoList.size());
        PageVO<AnalysisListVo> recommendPage = new PageVO<>(size, analysisListVoList);
        return recommendPage;
    }

    public static String replaceDate(String textInfo) {
        if (null != textInfo && textInfo.contains(IndicatorConstant.FIN_MONTH)) {
            LocalDate currentDate = LocalDate.now();
            Integer dayOfMonth = currentDate.getDayOfMonth();
            LocalDate targetDate = LocalDate.now();

            //小于6号取上上个月，大于6号取上月。如9月1号，结果7月。大于6号结果8月。
            if (dayOfMonth < 6) {
                targetDate = currentDate.minusMonths(2);
            } else {
                targetDate = currentDate.minusMonths(1);
            }

            Integer year = targetDate.getYear();
            Integer month = targetDate.getMonthValue();

            String date = year + "年" + month + "月";

            textInfo = textInfo.replaceAll(IndicatorConstant.FIN_MONTH, date);

        }

        return textInfo;
    }

    private void checkInfo(AiBusinessVo aiBusinessVo, List<String> keyValueList, Boolean isAdd) {
        List<String> info = new ArrayList<>();
        info.add(aiBusinessVo.getKeyWord());
        info.addAll(keyValueList);
        Wrapper<WordValues> lambdaQuery = Wrappers.<WordValues>lambdaQuery().select(WordValues::getKey, WordValues::getValue)
                .eq(WordValues::getKey, aiBusinessVo.getKeyWord()).or().in(WordValues::getValue, info);

        List<WordValues> listValues = wordValuesMapper.selectList(lambdaQuery);

        for (WordValues wordValues : listValues) {
            if (isAdd && Objects.equals(wordValues.getKey(), aiBusinessVo.getKeyWord())) {
                throw new RuntimeException("已存在相同的关键字，关键字为" + wordValues.getKey());
            }
            if (Objects.equals(wordValues.getValue(), aiBusinessVo.getKeyWord())) {
                throw new RuntimeException("关键字，在专业术语汇中存在，不允许，关键字为" + wordValues.getKey());
            }
            if (keyValueList.contains(wordValues.getKey())) {
                String errInfo = "专业术语包含关键字，不允许，关键字为" + wordValues.getKey();
                throw new RuntimeException(errInfo);
            }

            if (keyValueList.contains(wordValues.getValue())) {
                String errInfo = "已存在相同的术语，已出现在其他关键字中，关键字为" + wordValues.getKey();
                throw new RuntimeException(errInfo);
            }
        }
    }
}

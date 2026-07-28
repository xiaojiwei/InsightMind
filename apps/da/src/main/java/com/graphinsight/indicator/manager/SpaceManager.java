package com.graphinsight.indicator.manager;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.auto.entity.Category;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.service.IMeasureService;
import com.graphinsight.indicator.dao.SpaceDao;
import com.graphinsight.indicator.model.Space;
import com.graphinsight.indicator.model.cache.SpaceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Date: 2022/5/12
 * Desc: 空间管理类
 */
@Service
@Slf4j
@DS("mysql")
public class SpaceManager {

    @Autowired
    private SpaceDao spaceDao;
    @Autowired
    private CategoryManager categoryManager;
    @Autowired
    private IMeasureService measureService;

    public List<Space> getAllSpaces() {
        return spaceDao.findAll();
    }

    public Map<Long, SpaceContext> getSpaceContextMap() {
        List<Space> spaceList = spaceDao.findAll();
        if (!CollectionUtils.isEmpty(spaceList)) {
            return spaceList.stream().map(space -> convert(space)).collect(Collectors.toMap(SpaceContext::getId, spaceContext -> spaceContext));
        }
        return Collections.emptyMap();
    }

    public SpaceContext getSpaceContext(Long spaceId) {
        Space space = spaceDao.findById(spaceId).orElse(null);
        return convert(space);
    }

    private SpaceContext convert(Space space) {
        if (Objects.isNull(space)) {
            return null;
        }
        SpaceContext spaceContext = new SpaceContext();
        BeanUtils.copyProperties(space, spaceContext);
        // 指标分类
        Set<String> measCategoryCodes = Optional.ofNullable(space.getClassificationSet()).map(classifications -> classifications.stream().map(classification -> classification.getClassCode()).collect(Collectors.toSet())).orElse(Collections.EMPTY_SET);

        Set<Integer> measCategoryIdsWithChildren = new HashSet<>();
        measCategoryCodes.forEach(code -> {
            List<Category> allChildren = categoryManager.findAllChildren(Integer.valueOf(code));
            Set<Integer> collect = allChildren.stream().map(Category::getId).collect(Collectors.toSet());
            measCategoryIdsWithChildren.addAll(collect);
        });
        List<Measure> measures = measureService.list();
        Set<Integer> measIdsWithChildren = new HashSet<>();
        measures.forEach(m -> {
            if (measCategoryIdsWithChildren.contains(m.getLeafCategoryId())) {
                measIdsWithChildren.add(m.getId());
            }
        });
        spaceContext.setMeasCategoryIdsWithChildren(measCategoryIdsWithChildren);
        spaceContext.setMeasCategoryIds(measCategoryCodes);
        spaceContext.setMeasIdsWithChildren(measIdsWithChildren);
        return spaceContext;
    }
}

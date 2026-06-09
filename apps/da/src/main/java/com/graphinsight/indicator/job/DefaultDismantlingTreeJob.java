package com.graphinsight.indicator.job;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.DismantlingTreeManager;
import com.graphinsight.indicator.model.dto.DismantlingConfigTree;
import com.graphinsight.indicator.service.SpaceService;

@Component
@Slf4j
public class DefaultDismantlingTreeJob {
    private static final DateTimeFormatter Formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String LogPrefix = "[DefaultDismantlingTreeJob execute]";
    private static final long SpaceId = 4;

    @Resource
    private CacheManager cacheManager;
    @Resource
    DismantlingTreeManager dismantlingTreeManagerV2;
    @Autowired
    private SpaceService spaceService;

    //@Scheduled(fixedDelay = 2, timeUnit = TimeUnit.HOURS)
    public void execute() {
        try {
            log.info("{} start at {}.", LogPrefix, LocalDateTime.now().format(Formatter));
            Collection<Measure> collection = cacheManager.getMetadataCache().getAllMeasureCodeMap().values();
            if (collection == null || collection.isEmpty()) {
                log.info("{} measure collection is empty", LogPrefix);
                return;
            }

            for (Measure measure : collection) {
                if (dismantlingTreeManagerV2.hasSomeTree(SpaceId, measure.getCode())) {
                    log.info("{} the decision tree already exists code:{} name:{}", LogPrefix, measure.getCode(), measure.getCnName());
                    continue;
                }
                DismantlingConfigTree dismantlingConfigTree = dismantlingTreeManagerV2.buildDismantlingConfigTree(SpaceId, measure.getCode());
                if (dismantlingConfigTree == null) {
                    log.info("{} the dismantlingConfigTree is empty code:{} name:{}", LogPrefix, measure.getCode(), measure.getCnName());
                    continue;
                }

                dismantlingTreeManagerV2.assemble(dismantlingConfigTree);
                dismantlingTreeManagerV2.save(dismantlingConfigTree);
            }
        } catch (Exception e) {
            log.error("{} error :{}", LogPrefix, e.getMessage(), e);
        } finally {
            log.info("{} end at {}.", LogPrefix, LocalDateTime.now().format(Formatter));
        }
    }
}

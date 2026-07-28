package com.graphinsight.indicator.controller;

import com.graphinsight.indicator.enums.RatioType;
import com.graphinsight.indicator.manager.DorisQueryManager;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.Ratio;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.RatioQuery;
import com.graphinsight.indicator.schedule.ScheduleManager;
import org.quartz.SchedulerException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * Date: 2022/10/9
 * Desc:
 */
@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    @Resource
    ScheduleManager scheduleManager;
    @Resource
    DorisQueryManager dorisQueryManager;

    @PostMapping("/ratio")
    public Response testRatio(@RequestBody RatioQuery ratioQuery){
        Ratio ratio = new Ratio();
        ratio.setRatioType(RatioType.getTypeByCode(ratioQuery.getRatioType()));
        PageData query = dorisQueryManager.ratioQuery(ratioQuery.getSpaceId(), ratioQuery.getMeasCode(), ratioQuery.getDimCode(), ratio, Collections.EMPTY_LIST,null);
        return Response.ok(query);
    }

    @PostMapping("/create")
    public Response create(@RequestBody ScheduleVO scheduleVO){
        try {
            scheduleManager.createJob(scheduleVO.getName(),scheduleVO.getGroup());
        } catch (SchedulerException e) {
        }
        return Response.ok();
    }

    @PostMapping("/del")
    public Response del(@RequestBody ScheduleVO scheduleVO){
        try {
            scheduleManager.del(scheduleVO.getName(),scheduleVO.getGroup());
        } catch (SchedulerException e) {
        }
        return Response.ok();
    }
    @PostMapping("/resume")
    public Response resume(@RequestBody ScheduleVO scheduleVO){
        try {
            scheduleManager.resume(scheduleVO.getName(),scheduleVO.getGroup());
        } catch (SchedulerException e) {
        }
        return Response.ok();
    }
    @PostMapping("/shutdown")
    public Response shutdown(@RequestBody ScheduleVO scheduleVO){
        try {
            scheduleManager.shutDown(scheduleVO.getName(),scheduleVO.getGroup());
        } catch (SchedulerException e) {
        }
        return Response.ok();
    }

}

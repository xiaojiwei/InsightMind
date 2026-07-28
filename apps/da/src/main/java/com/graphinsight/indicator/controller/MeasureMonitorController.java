package com.graphinsight.indicator.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.annotation.AuthIgnore;
import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.auto.entity.MeasureMonitor;
import com.graphinsight.indicator.auto.entity.MeasureMonitorAlertLog;
import com.graphinsight.indicator.auto.service.IMeasureMonitorService;
import com.graphinsight.indicator.manager.FeiShuMsgManager;
import com.graphinsight.indicator.manager.MeasureMonitorManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.feishu.ChatGroup;
import com.graphinsight.indicator.model.vo.MeasureMonitorQuery;
import com.graphinsight.indicator.model.vo.MeasureMonitorVO;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Date: 2022/10/11
 * Desc:
 */
@RestController
@CrossOrigin
@RequestMapping("/measure/monitor")
public class MeasureMonitorController {

    @Resource
    MeasureMonitorManager measureMonitorManager;

    @Autowired
    FeiShuMsgManager feiShuMsgManager;


    @Resource
    IMeasureMonitorService measureMonitorService;

    @OperateLog
    @ApiOperation("更新/保存 预警")
    @PostMapping("/save")
    public Response<MeasureMonitorVO> save(@RequestBody @Validated MeasureMonitorVO measureMonitorVO){
        measureMonitorManager.saveOrUpdate(measureMonitorVO);
        return Response.ok();
    }

    @ApiOperation("获取预警列表")
    @PostMapping("/list")
    public Response<List<MeasureMonitorVO>> list(@RequestBody @Validated MeasureMonitorQuery query){
        List<MeasureMonitorVO> monitorVOS = measureMonitorManager.list(query);
        return Response.ok(monitorVOS);
    }

    @ApiOperation("预警详情")
    @GetMapping("/detail/{id}")
    @Transactional
    public Response<MeasureMonitorVO> detail(@PathVariable Long id){
        MeasureMonitorVO monitorVO = measureMonitorManager.detail(id);
        if(monitorVO.getLogicType() == null) {
            monitorVO.setLogicType(0);
        }
        return Response.ok(monitorVO);
    }

    @OperateLog
    @ApiOperation("删除预警")
    @GetMapping("/delete/{id}")
    @Transactional
    public Response<MeasureMonitorVO> delete(@PathVariable Long id){
        measureMonitorManager.delete(id);
        return Response.ok();
    }

    @OperateLog
    @ApiOperation("启用预警")
    @GetMapping("/on/{id}")
    public Response<MeasureMonitorVO> on(@PathVariable Long id){
        measureMonitorManager.on(id);
        return Response.ok();
    }



    @OperateLog
    @ApiOperation("停用预警")
    @GetMapping("/off/{id}")
    public Response<MeasureMonitorVO> off(@PathVariable Long id){
        measureMonitorManager.off(id);
        return Response.ok();
    }

    @OperateLog
    @ApiOperation("批量停用预警")
    @GetMapping("/off")
    public Boolean batchOff(){
        List<Long> list = measureMonitorService.list().stream().map(e -> e.getId()).collect(Collectors.toList());
        System.out.println(list.size());
        list.forEach(e->{
            measureMonitorManager.off(e);
        });
        return true;
    }

    @GetMapping("/groups")
    @ApiOperation("获取飞书群")
    public Response<ChatGroup> test(){
        List<ChatGroup> groups = feiShuMsgManager.getGroups();
        return Response.ok(groups);
    }

    @GetMapping("/log/{id}")
    @ApiOperation("预警日志")
    public Response log(@PathVariable Long id){
        MeasureMonitorAlertLog alertLog = new MeasureMonitorAlertLog();
        List<MeasureMonitorAlertLog> logs = alertLog.selectList(Wrappers.<MeasureMonitorAlertLog>lambdaQuery().eq(MeasureMonitorAlertLog::getMonitorId,id));
        return Response.ok(logs);
    }

}

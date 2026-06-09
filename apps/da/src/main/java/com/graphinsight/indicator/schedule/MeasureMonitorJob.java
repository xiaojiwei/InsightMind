package com.graphinsight.indicator.schedule;

import com.alibaba.fastjson.JSON;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.service.*;
import com.graphinsight.indicator.auto.service.impl.DismantlingTreeServiceImpl;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.manager.*;
import com.graphinsight.indicator.model.Filter;
import com.graphinsight.indicator.model.Operator;
import com.graphinsight.indicator.model.dto.MeasureMonitorDimGroupQueryResult;
import com.graphinsight.indicator.model.dto.MeasureMonitorResult;
import com.graphinsight.indicator.model.feishu.Element;
import com.graphinsight.indicator.model.feishu.FeishuCardMessage;
import com.graphinsight.indicator.model.feishu.Field;
import com.graphinsight.indicator.model.feishu.Header;
import com.graphinsight.indicator.model.feishu.TagType;
import com.graphinsight.indicator.model.feishu.Text;
import com.graphinsight.indicator.model.vo.MeasureMonitorVO;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/10/13
 * Desc:
 */
@Slf4j
@Component
public class MeasureMonitorJob implements Job {

    @Resource
    IMeasureMonitorReceiverService measureMonitorReceiverService;
    @Resource
    IMeasureMonitorSendLogService logService;
    @Resource
    MeasureMonitorManager measureMonitorManager;
    @Resource
    IMeasureMonitorService measureMonitorService;
    @Resource
    FeiShuMsgManager feiShuMsgManager;
    @Resource
    UserManager userManager;

    @Resource
    CacheManager cacheManager;

    @Resource
    IMeasureMonitorConfigDescService measureMonitorConfigDescService;

    @Resource
    IMeasureMonitorRuleDetailService measureMonitorRuleDetailService;

    @Resource
    IDismantlingTreeService iDismantlingTreeService;

    @Value("${url}")
    private String url;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        JobDataMap jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        long id = jobDataMap.getLongFromString(IndicatorConstant.MEASURE_MONITOR_JOB_KEY);
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        MeasureMonitor measureMonitor = measureMonitorService.getById(id);
        if (measureMonitor == null) {
            log.error("监控ID:{} 不存在，但是调度任务还在运行，请关注", id);
        }
        if (MeasureMonitorStatusEnum.OFF.getCode() == measureMonitor.getStatus()) {
            // 预警已禁用
            measureMonitorManager.off(measureMonitor.getId());
            return;
        }
        log.info("指标预警: {} 开始执行,jobKey: {} ", measureMonitor.getName(), jobExecutionContext.getJobDetail().getKey().toString());
        List<MeasureMonitorResult> monitorResults = new LinkedList<>();
        try {
            monitorResults = measureMonitorManager.executeMonitor(id).stream().filter(e->e.getTrigger()).collect(Collectors.toList());
            log.info("指标预警：{}, 执行结束:{}", measureMonitor.getName(), monitorResults);
        }catch (Exception e){
            log.error("指标预警执行异常，{}",e);
            sendSystemError(measureMonitor, e);
        }
        if (!CollectionUtils.isEmpty(monitorResults)) {
            log.info("指标预警：{}, 发送飞书消息:{}",measureMonitor.getName(), JSON.toJSONString(monitorResults));
            sendMsg(monitorResults, measureMonitor);
            // 告警次数+1
            Integer triggerCount = measureMonitor.getTriggerCount() == null ? 0 : measureMonitor.getTriggerCount();
            triggerCount++;
            measureMonitor.setTriggerCount(triggerCount);
            measureMonitor.setLastTriggerTime(LocalDateTime.now());
            measureMonitorService.updateById(measureMonitor);
        }
    }

    private void sendSystemError(MeasureMonitor measureMonitor, Exception exception){
        List<Element> elements = new ArrayList<>();

        Element element = Element.builder().tag("div")
                .text(Text.builder().content(exception.getMessage()).tag("lark_md").build())
                .build();

        elements.add(element);
        FeishuCardMessage cardMessage = FeishuCardMessage.builder()
                .header(Header.builder().template("red").title(Text.builder().content("预警执行异常【" + measureMonitor.getName() + "】，请及时关注").build()).build())
                .elements(elements)
                .build();

        String msg = JSON.toJSONString(cardMessage);
        try {
            feiShuMsgManager.sendTextMessageByEmail("xueqi@graphinsight.com", msg, false);
        }catch (Exception e) {
            log.error("指标预警：{}, 预警执行异常消息发送异常，{}", measureMonitor.getName(), e);
        }
    }

    private void sendMsg(List<MeasureMonitorResult> monitorResults, MeasureMonitor measureMonitor) {
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        FeishuCardMessage cardMessage = FeishuCardMessage.builder()
                .header(Header.builder().template("red").title(Text.builder().content("监控报告【" + measureMonitor.getName() + "】").build()).build())
                .elements(listElements(monitorResults, measureMonitor))
                .build();


        List<MeasureMonitorReceiver> list = measureMonitorReceiverService.list(Wrappers.<MeasureMonitorReceiver>lambdaQuery()
                .eq(MeasureMonitorReceiver::getMonitorId, measureMonitor.getId()).eq(MeasureMonitorReceiver::getReceiverType, 0));
        List<MeasureMonitorReceiver> receivers = list.stream().filter(r -> Objects.equals(r.getSendFeishu(), YesNoType.YES.getCode())).collect(Collectors.toList());

        List<MeasureMonitorReceiver> chatGroups = measureMonitorReceiverService.list(Wrappers.<MeasureMonitorReceiver>lambdaQuery()
                .eq(MeasureMonitorReceiver::getMonitorId, measureMonitor.getId()).eq(MeasureMonitorReceiver::getReceiverType, 2));
        String msg = JSON.toJSONString(cardMessage);
        // try {
        //     feiShuMsgManager.sendTextMessageByEmail("zhangxinran@graphinsight.com",msg ,false);
        // } catch (Exception e) {
        // }
        //发送给个人
        if (CollectionUtils.isNotEmpty(receivers)) {
            receivers.forEach(r -> {
                User user = userManager.getUserByName(r.getReceiverCode());
                try {
                    feiShuMsgManager.sendTextMessageByEmail(user.getEmail(), msg, false);
                    log(measureMonitor, msg, YesNoType.YES.getCode(), user.getUsername(), "success");
                } catch (Exception e) {
                    log.error("飞书消息发送失败:", e);
                    log(measureMonitor, msg, YesNoType.NO.getCode(), user.getUsername(), e.toString());
                }
            });
        }

        //发送给飞书群
        if (CollectionUtils.isNotEmpty(chatGroups)) {
            chatGroups.forEach(e -> {
                try {
                    feiShuMsgManager.sendMsgToFeiShuChatGroup(e.getReceiverCode(), msg, false);
                    log(measureMonitor, msg, YesNoType.YES.getCode(), e.getReceiverCode(), "success");
                } catch (Exception exception) {
                    log.error("发送到飞书群失败,chatGroup：{}", e, exception);
                    log(measureMonitor, msg, YesNoType.NO.getCode(), e.getReceiverCode(), exception.toString());
                }
            });
        }
    }

    private void log(MeasureMonitor measureMonitor, String msg, Integer success, String receivers, String errorMsg) {
        MeasureMonitorSendLog sendLog = new MeasureMonitorSendLog();
        sendLog.setMessage(msg);
        sendLog.setMonitorId(measureMonitor.getId());
        sendLog.setStatus(success);
        sendLog.setReceivers(receivers);
        sendLog.setErrorMsg(errorMsg);
        logService.save(sendLog);
    }

    private List<Field> listFields(List<MeasureMonitorResult> monitorResults, MeasureMonitor measureMonitor) {
        List<Field> fields = new ArrayList<>();
        Field field = Field.builder()
                .text(Text.builder().content(getContent(monitorResults, measureMonitor))
                        .tag(TagType.LARD_MD.getCode()).build())
                .is_short(true)
                .build();
        fields.add(field);
        return fields;
    }

    private List<Element> listElements(List<MeasureMonitorResult> monitorResults, MeasureMonitor measureMonitor) {
        List<Element> result = new ArrayList<>();
        try {
            Element element = Element.builder().tag(TagType.DIV.getCode())
                    .fields(listFields(monitorResults, measureMonitor))
                    .build();

            result.add(element);
        } catch (Exception e) {
            log.error("生成告警内容失败, monitorResults: {}", monitorResults, e);
        }
        return result;
    }

    private String getContent(List<MeasureMonitorResult> monitorResults, MeasureMonitor measureMonitor) {
        log.info("监控指标为 ： {}", measureMonitor.toString());
        StringBuilder res = new StringBuilder();
        List<String> contents = new LinkedList<>();
        res.append("日期范围: " + DateTimeFormat.forPattern("yyyy-MM-dd").print(DateTime.now()));
        res.append("\n");
        res.append("告警内容：" + "\n");

        for (MeasureMonitorResult monitorResult : monitorResults) {
            List<String> list = buildAlertContent(monitorResult, measureMonitor);
            contents.addAll(list);
        }

        for (int i = 1; i <= contents.size(); i++) {
            String content = contents.get(i - 1);
            res.append("(" + i + ")" + content + "\n");
        }
        res.append("\n");
        for (MeasureMonitorResult monitorResult : monitorResults) {
            List<MeasureMonitorConfigDesc> measureMonitorConfigDescList = measureMonitorConfigDescService.list(Wrappers.<MeasureMonitorConfigDesc>lambdaQuery().eq(MeasureMonitorConfigDesc::getMonitorId, measureMonitor.getId()).eq(MeasureMonitorConfigDesc::getMeasure, monitorResult.getMeasure().getCode()));
            for (MeasureMonitorConfigDesc measureMonitorConfigDesc : measureMonitorConfigDescList) {
                log.info("配置解读为 ： {}", measureMonitorConfigDesc.toString());
                MeasureMonitorRuleDetail measureMonitorRuleDetail = measureMonitorRuleDetailService.getOne(Wrappers.<MeasureMonitorRuleDetail>lambdaQuery().eq(MeasureMonitorRuleDetail::getRuleId, monitorResult.getRuleId()));
                log.info("规则细则为 ： {}", measureMonitorRuleDetail.toString());
                String measCode = monitorResult.getMeasure().getCode();
                Calendar calendar = Calendar.getInstance();
                int year = calendar.get(Calendar.YEAR);
                int month = calendar.get(Calendar.MONTH) + 1; // 注意月份是从0开始计数的
                int day = calendar.get(Calendar.DAY_OF_MONTH);
                String date = year + "-" + month + "-" + day;
                DismantlingTree dismantlingTree = iDismantlingTreeService.getOne(Wrappers.<DismantlingTree>lambdaQuery().eq(DismantlingTree::getSpaceId, measureMonitor.getSpaceId()).eq(DismantlingTree::getName, measureMonitorConfigDesc.getDismantlingTree()));
                res.append("\n");
                res.append("[" + monitorResult.getMeasure().getCnName() + "_" + measureMonitorConfigDesc.getDismantlingTree() + "]" + "(" + url + measureMonitor.getSpaceId() + "/analysis/decomposition-tree" + "?measCode=" + measCode + "&dismantlingTree=" + dismantlingTree.getId() + "&dimCode=" + measureMonitorRuleDetail.getDimCode() + "&date=" + date + ")");
                log.info("链接为" + url + measureMonitor.getSpaceId() + "/analysis/decomposition-tree" + "?measCode=" + measCode + "&dismantlingTree=" + dismantlingTree.getId() + "&dimCode=" + measureMonitorRuleDetail.getDimCode() + "&date=" + date);
                res.append("\n");
            }
        }

        logAlertContent(measureMonitor.getId(), res.toString());

        return res.toString();
    }

    private List<String> buildAlertContent(MeasureMonitorResult result, MeasureMonitor measureMonitor) {

        List<String> res = new LinkedList<>();

        //指标名
        String measureName = result.getMeasure().getCnName();

        //比较方式
        String statPeriod = result.getStatPeriodEnum().getDesc();
        IndicatorRatioType ratioType = result.getRatioType();
        String ratioDesc = ratioType.getDesc();

        //规则生成
        String compareWay = result.getCompareWayEnum().getDesc();
        String threshold = result.getThresholdValue();
        if (!ratioType.getCode().equals(RatioType.DEFAULT.getCode())) {
            threshold = formatThreshold(threshold);
        }
        String ruleDesc = measureName + " " + statPeriod + ratioDesc + " " + compareWay + " " + threshold;

        //过滤器描述生成
        String filterDesc = buildFilterDesc(result);


        String alertContent = measureMonitor.getAlertContent();

        if (StringUtils.isBlank(alertContent)) {
            alertContent = "【过滤条件】，【分组值】，【指标名称】的【比较方式】为【数值】，【对比时间数据】，触发了规则：【规则】";
        }

        List<MeasureMonitorDimGroupQueryResult> dimGroupResults = result.getResults();

        for (MeasureMonitorDimGroupQueryResult groupQueryResult : dimGroupResults) {
            String dateCompareDesc = groupQueryResult.genCompareDateDesc();
            String realValue = groupQueryResult.getRealValue();
            String key = groupQueryResult.getDimGroupKey();
            if (groupQueryResult.getTrigger()) {
                String s = alertContent + "";
                s = s.replace("【过滤条件】", filterDesc);
                s = s.replace("【分组值】", key);
                s = s.replace("【指标名称】", measureName);
                s = s.replace("【比较方式】", ratioDesc);
                s = s.replace("【规则】", ruleDesc);
                s = s.replace("【数值】", realValue);
                s = s.replace("【对比时间数据】", dateCompareDesc);
                res.add(s);
            }
        }
        return res;
    }

    private String formatThreshold(String threshold) {
        String res = "";
        DecimalFormat df = new DecimalFormat("0.00%");
        try {
            String[] thresholds = threshold.split(",");
            double doubleValue = new DecimalFormat().parse(thresholds[0]).doubleValue();
            res += df.format(doubleValue);
            if (thresholds.length == 2) {
                double doubleValue1 = new DecimalFormat().parse(thresholds[0]).doubleValue();
                res += "-";
                res += df.format(doubleValue1);
            }
            return res;
        } catch (Exception e) {
            log.error("阈值解析异常", e);
            return threshold;
        }
    }

    private void logAlertContent(Long monitorId, String content) {
        MeasureMonitorAlertLog alertLog = new MeasureMonitorAlertLog();
        alertLog.setMonitorId(monitorId);
        alertLog.setContent(content);
        alertLog.insert();
    }


    //过滤条件生成
    private String buildFilterDesc(MeasureMonitorResult result) {
        String s1 = "";
        Map<String, Dimension> allDimensionCodeMap = cacheManager.getMetadataCache().getAllDimensionCodeMap();
        try {
            List<Filter> filters = result.getFilters();
            for (Filter filter : filters) {
                String code = filter.getCode();
                Dimension dimension = allDimensionCodeMap.get(code);
                String dimName = dimension.getCnName();
                s1 += dimName;
                List<Operator> operators = filter.getOperatorList();
                for (Operator operator : operators) {
                    List<String> dataList = operator.getDataList();
                    s1 += operator.getSqlOprType().getDesc();
                    for (String data : dataList) {
                        s1 += data + " ";
                    }
                    if (operator.getSqlLogicalType() != null) {
                        s1 += operator.getSqlLogicalType().getDesc();
                    }
                }
            }
        } catch (Exception e) {
            log.error("生成过滤器描述失败", e);
        }
        return s1;
    }
}

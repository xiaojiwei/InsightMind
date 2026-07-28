package com.graphinsight.indicator.schedule;

import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Date: 2022/10/9
 * Desc:
 */
@Slf4j
@Component
public class ScheduleManager {

    @Resource
    Scheduler scheduler;


    public void createCronJob(String jobParamKey, String jobParam,String jobName, String jobGroup,String cron,boolean startNow) throws SchedulerException {
        log.info("开始创建Job:{}",jobName);
        JobDetail jobDetail = JobBuilder.newJob(MeasureMonitorJob.class)
                .usingJobData(jobParamKey, jobParam)
                .withIdentity(jobName, jobGroup)
                .build();


        TriggerBuilder<CronTrigger> builder = TriggerBuilder.newTrigger()
                /**给当前JobDetail添加参数，K V形式，链式调用，可以传入多个参数，在Job实现类中，可以通过jobExecutionContext.getTrigger().getJobDataMap().get("orderNo")获取值*/
                .usingJobData(jobParamKey, jobParam)
                .withIdentity(jobName, jobGroup)
                .withSchedule(
                        CronScheduleBuilder.cronSchedule(cron)
                );

        Trigger trigger = builder.build();//
        scheduler.scheduleJob(jobDetail,trigger);
        if (! startNow){
            scheduler.pauseTrigger(TriggerKey.triggerKey(jobName,jobGroup));
        } else {
            scheduler.resumeTrigger(TriggerKey.triggerKey(jobName,jobGroup));
        }
    }

    public void createJob(String jobName,String jobGroup) throws SchedulerException {

        JobDetail jobDetail = JobBuilder.newJob(MyJob.class)
                .usingJobData("data", jobName + jobGroup)
                .withIdentity(jobName, jobGroup)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                /**给当前JobDetail添加参数，K V形式，链式调用，可以传入多个参数，在Job实现类中，可以通过jobExecutionContext.getTrigger().getJobDataMap().get("orderNo")获取值*/
                .usingJobData("data", jobName + jobGroup)
                .withIdentity(jobName,jobGroup)
                /**立即生效*/
                .withSchedule(
                        SimpleScheduleBuilder.simpleSchedule()
                                /**每隔3s执行一次,api方法有好多规则自行查看*/
                                .withIntervalInSeconds(3)
                                /**一直执行,如果不写,定时任务就执行一次*/
                                .repeatForever()
                )
                .startNow()
                .build();//

        scheduler.scheduleJob(jobDetail,trigger);
    }

    public void shutDown(String jobName,String jobGroup) throws SchedulerException {
        log.info("暂停Job:{}",jobName);
        scheduler.pauseTrigger(TriggerKey.triggerKey(jobName,jobGroup));
    }

    public void resume(String jobName,String jobGroup) throws SchedulerException {
        log.info("恢复Job:{}",jobName);
        scheduler.resumeTrigger(TriggerKey.triggerKey(jobName,jobGroup));
    }

    public void del(String jobName,String jobGroup) throws SchedulerException {
        log.info("删除Job:{}",jobName);
        scheduler.pauseTrigger(TriggerKey.triggerKey(jobName,jobGroup));
        scheduler.unscheduleJob(TriggerKey.triggerKey(jobName,jobGroup));
        scheduler.deleteJob(JobKey.jobKey(jobName,jobGroup));
    }
}

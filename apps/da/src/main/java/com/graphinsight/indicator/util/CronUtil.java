package com.graphinsight.indicator.util;

import com.graphinsight.indicator.model.vo.TaskScheduleVO;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Cron表达式工具类（quartz类）
 * 符号表示的值：
 * * 表示所有值；
 * ? 表示未说明的值，即不关心它为何值；
 * - 表示一个指定的范围；
 * , 表示附加一个可能值；
 * / 符号前表示开始时间，符号后表示每次递增的值；
 * L("last") ("last") "L" 用在day-of-month字段意思是 "这个月最后一天"；
 * W("weekday") 只能用在day-of-month字段。用来描叙最接近指定天的工作日（周一到周五）
 * @author lenovo
 */
public class CronUtil {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("ss mm HH dd MM ? yyyy");

    /**
     * 年 （可选） 留空
     * 允许的特殊字符：留空, 1970-2099 , - * /
     */
    private String year;
    /**
     * 星期 可以用数字1-7表示（1 ＝ 星期日）或用字符口串“SUN, MON, TUE, WED, THU, FRI and SAT”表示 ,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
     * 允许的特殊字符：1-7 或者 SUN-SAT , - * ? / L C #
     */
    private String week;
    /**
     * 月  可以用0-11 或用字符串  “JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCT, NOV and DEC” 表示
     * 允许的特殊字符：1-12 或者 JAN-DEC , - * /
     */
    private String month;
    /**
     * 日 可以用数字1-31 中的任一一个值，但要注意一些特别的月份
     * 允许的特殊字符：1-31 , - * ? / L W C
     */
    private String day;
    /**
     * 时 可以用数字0-23表示
     * 允许的特殊字符：0-23, - * /
     */
    private String hour;
    /**
     * 分 可以用数字0－59 表示
     * 允许的特殊字符：0-59,- * /
     */
    private String minutes;
    /**
     * 秒 可以用数字0－59 表示
     * 允许的特殊字符：0-59,- * /
     */
    private String seconds ;

    /***
     *  日期转换cron表达式 例如 "0 07 10 15 1 ? 2016"
     * @param date 时间点
     * @return
     */
    public static String getCron(Date date) {
        String formatTimeStr = null;
        if (Objects.nonNull(date)) {
            formatTimeStr = sdf.format(date);
        }
        return formatTimeStr;
    }

    /**
     * 获取指定日期的cron表达式
     * @param year 年
     * @param week 星期 可以用数字1-7表示（1 ＝ 星期日）或用字符口串“SUN, MON, TUE, WED, THU, FRI and SAT”表示
     * @param month 月 可以用0-11 或用字符串  “JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCT, NOV and DEC” 表示
     * @param day 日 可以用数字1-31 中的任一一个值，但要注意一些特别的月份
     * @param hour 时 可以用数字0-23表示
     * @param minutes 分 可以用数字0－59 表示
     * @param seconds 秒 可以用数字0－59 表示
     * @return
     */
    public static String getCron(String year,String week,String month,String day,String hour,String minutes,String seconds) {
        return seconds+" "+minutes+" "+hour+" "+day+" "+month+" "+week+" "+year;
    }

    /**
     * 获取指定日期的cron表达式
     * @param week 星期 可以用数字1-7表示（1 ＝ 星期日）或用字符口串“SUN, MON, TUE, WED, THU, FRI and SAT”表示
     * @param month 月 可以用0-11 或用字符串  “JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCT, NOV and DEC” 表示
     * @param day 日 可以用数字1-31 中的任一一个值，但要注意一些特别的月份
     * @param hour 时 可以用数字0-23表示
     * @param minutes 分 可以用数字0－59 表示
     * @param seconds 秒 可以用数字0－59 表示
     * @return
     */
    public static String getCron(String week,String month,String day,String hour,String minutes,String seconds) {
        return getCron("*",week,month,day,hour,minutes,seconds);
    }

    /**
     * 获取指定日期的cron表达式
     * @param month 月 可以用0-11 或用字符串  “JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCT, NOV and DEC” 表示
     * @param day 日 可以用数字1-31 中的任一一个值，但要注意一些特别的月份
     * @param hour 时 可以用数字0-23表示
     * @param minutes 分 可以用数字0－59 表示
     * @param seconds 秒 可以用数字0－59 表示
     * @return
     */
    static String getCron(String month,String day,String hour,String minutes,String seconds) {
        return getCron("?",month,day,hour,minutes,seconds);
    }

    /**
     * 获取指定范围的Cron表达式 例如 13-14 30-31 11-12 20-21 04-05 1-2 2021-2022
     * @param year 年 使用（year1-year2） year1<=year2
     * @param week 星期 使用（week1-week2） 可以用数字1-7表示（1 ＝ 星期日）或用字符口串“SUN, MON, TUE, WED, THU, FRI and SAT”表示
     * @param month 月 使用（month1-month2） 可以用0-11 或用字符串  “JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCT, NOV and DEC” 表示
     * @param day 日  使用（day1-day2） 可以用数字1-31 中的任一一个值，但要注意一些特别的月份
     * @param hour 时 使用（hour1-hour2） 可以用数字0-23表示
     * @param minutes 分  使用（minutes1-minutes2） 可以用数字0－59 表示
     * @param seconds 秒  使用（seconds1-seconds2） 可以用数字0－59 表示
     * @return
     */
    public static String getCronByRange(String year,String week,String month,String day,String hour,String minutes,String seconds) {
        return seconds+" "+minutes+" "+hour+" "+day+" "+month+" "+week+" "+year;
    }

    /**
     * 获取指定范围的Cron表达式 例如 13-14 30-31 11-12 20-21 04-05 1-2
     * @param week 星期 使用（week1-week2） 可以用数字1-7表示（1 ＝ 星期日）或用字符口串“SUN, MON, TUE, WED, THU, FRI and SAT”表示
     * @param month 月 使用（month1-month2） 可以用0-11 或用字符串  “JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCT, NOV and DEC” 表示
     * @param day 日  使用（day1-day2） 可以用数字1-31 中的任一一个值，但要注意一些特别的月份
     * @param hour 时 使用（hour1-hour2） 可以用数字0-23表示
     * @param minutes 分  使用（minutes1-minutes2） 可以用数字0－59 表示
     * @param seconds 秒  使用（seconds1-seconds2） 可以用数字0－59 表示
     * @return
     */
    public static String getCronByRange(String week,String month,String day,String hour,String minutes,String seconds) {
        return getCron("*",week,month,day,hour,minutes,seconds);
    }

    /**
     * 获取指定范围的Cron表达式  例如 13-14 30-31 11-12 20-21 04-05
     * @param month 月 使用（month1-month2） 可以用0-11 或用字符串  “JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCT, NOV and DEC” 表示
     * @param day 日  使用（day1-day2） 可以用数字1-31 中的任一一个值，但要注意一些特别的月份
     * @param hour 时 使用（hour1-hour2） 可以用数字0-23表示
     * @param minutes 分  使用（minutes1-minutes2） 可以用数字0－59 表示
     * @param seconds 秒  使用（seconds1-seconds2） 可以用数字0－59 表示
     * @return
     */
    static String getCronByRange(String month,String day,String hour,String minutes,String seconds) {
        return getCron("?",month,day,hour,minutes,seconds);
    }

    public static String createCronExpression(TaskScheduleVO taskScheduleModel){
        StringBuffer cronExp = new StringBuffer("");

        if (null != taskScheduleModel.getMinute() && null != taskScheduleModel.getHour()) {
            //秒
            cronExp.append(0).append(" ");
            //分
            cronExp.append(taskScheduleModel.getMinute()).append(" ");
            //小时
            cronExp.append(taskScheduleModel.getHour()).append(" ");

            //每天
            if(taskScheduleModel.getJobType().intValue() == 1){
                cronExp.append("* ");//日
                cronExp.append("* ");//月
                cronExp.append("?");//周
            }

            //按每周
            else if(taskScheduleModel.getJobType().intValue() == 3){
                //一个月中第几天
                cronExp.append("? ");
                //月份
                cronExp.append("* ");
                //周
                Integer[] weeks = taskScheduleModel.getDayOfWeeks();
                for(int i = 0; i < weeks.length; i++){
                    int num = weeks[i]+1;
                    if (num>7) {
                        num = 1;
                    }
                    if(i == 0){
                        cronExp.append(num);
                    } else{
                        cronExp.append(",").append(num);
                    }
                }

            }

            //按每月
            else if(taskScheduleModel.getJobType().intValue() == 2){
                //一个月中的哪几天
                Integer[] days = taskScheduleModel.getDayOfMonths();
                for(int i = 0; i < days.length; i++){
                    if(i == 0){
                        cronExp.append(days[i]);
                    } else{
                        cronExp.append(",").append(days[i]);
                    }
                }
                //月份
                cronExp.append(" * ");
                //周
                cronExp.append("?");
            }
        }
        else {
            return null;
        }
        return cronExp.toString();
    }

    public static void main(String[] args) {
        TaskScheduleVO taskScheduleVO = new TaskScheduleVO();
        taskScheduleVO.setJobType(3);
        taskScheduleVO.setHour(9);
        taskScheduleVO.setMinute(0);
        List<Integer> list = new LinkedList<>();
        list.add(1);
        list.add(2);
        taskScheduleVO.setDayOfWeeks(new Integer[]{1,2,3});
        taskScheduleVO.setDayOfMonths(new Integer[]{1,2,3});
        System.out.println(createCronExpression(taskScheduleVO));
        System.out.println(createDescription(taskScheduleVO));
    }

    /**
     *
     *方法摘要：生成计划的详细描述
     *@param  taskScheduleModel
     *@return String
     */
    public static String createDescription(TaskScheduleVO taskScheduleModel){
        StringBuffer description = new StringBuffer("");


        if (null != taskScheduleModel.getMinute()
                && null != taskScheduleModel.getHour()) {
            //按每天
            if(taskScheduleModel.getJobType().intValue() == 1){
                description.append("每天");
                description.append(taskScheduleModel.getHour()).append("时");
                description.append(taskScheduleModel.getMinute()).append("分");
                description.append("执行");
            }

            //按每周
            else if(taskScheduleModel.getJobType().intValue() == 3){
                if(taskScheduleModel.getDayOfWeeks() != null && taskScheduleModel.getDayOfWeeks().length > 0) {
                    String days = "";
                    for(int i : taskScheduleModel.getDayOfWeeks()) {
                        days += "周" + i;
                    }
                    description.append("每周的").append(days).append(" ");
                }
                if (null != taskScheduleModel.getMinute()
                        && null != taskScheduleModel.getHour()) {
                    description.append(",");
                    description.append(taskScheduleModel.getHour()).append("时");
                    description.append(taskScheduleModel.getMinute()).append("分");
                }
                description.append("执行");
            }

            //按每月
            else if(taskScheduleModel.getJobType().intValue() == 2){
                //选择月份
                if(taskScheduleModel.getDayOfMonths() != null && taskScheduleModel.getDayOfMonths().length > 0) {
                    String days = "";
                    for(int i : taskScheduleModel.getDayOfMonths()) {
                        days += i + "号";
                    }
                    description.append("每月的").append(days).append(" ");
                }
                description.append(taskScheduleModel.getHour()).append("时");
                description.append(taskScheduleModel.getMinute()).append("分");
                description.append("执行");
            }

        }
        return description.toString();
    }

}

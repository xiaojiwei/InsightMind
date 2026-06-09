package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum ViewType {
    CHARACTER(0, "字符"),
    DAY(1, "日"),
    WEEK(2, "周"),
    MONTH(3, "月"),
    SEASON(4, "季"),
    YEAR(5, "年"),
    HOUR(6, "小时"),
    NUMBER(7, "数值");

    private Integer value;

    private String name;

    public static void main(String[] args) {
        ViewType[] values = ViewType.values();
        for (ViewType value : values) {
            System.out.println(value.getName() + "  " + getDefaultTime(value.getValue()));
        }
    }

    public static Boolean switchable(Integer value) {
        ViewType viewType = findByInt(value).orElse(null);
        if (viewType == null) {
            return false;
        }
        DateTimeFormatter format;
        switch (viewType) {
            case MONTH:
            case DAY:
            case YEAR:
                return true;
            case SEASON:
            case WEEK:
                return false;
            default:
                return false;
        }
    }


    //指标预警同环比查询，根据统计周期生成日期
    public static String[] getDefaultTime(Integer value, Integer stataPeriod, Integer ratioType) {
        String cur = "";
        String base = "";
        ViewType viewType = findByInt(value).orElse(null);
        if (viewType == null) {
            return null;
        }
        int plus = stataPeriod == 1 ? 0 : -1;

        DateTimeFormatter format;
        switch (viewType) {
            case MONTH:
                format = DateTimeFormat.forPattern("yyyyMM");
                DateTime curMonth = DateTime.now().plusMonths(plus);
                cur = format.print(curMonth);
                base = format.print(curMonth.plusMonths(-1));
                if (getBaseForRatio(curMonth, format, ratioType) != null) {
                    base = getBaseForRatio(curMonth, format, ratioType);
                }
                break;
            case DAY:
                format = DateTimeFormat.forPattern("yyyy-MM-dd");
                DateTime curDay = DateTime.now().plusDays(plus);
                cur = format.print(curDay);
                base = format.print(curDay.plusDays(-1));
                if (getBaseForRatio(curDay, format, ratioType) != null) {
                    base = getBaseForRatio(curDay, format, ratioType);
                }
                break;
            case YEAR:
                format = DateTimeFormat.forPattern("yyyy");
                DateTime curYear = DateTime.now().plusYears(plus);
                cur = format.print(curYear);
                base = format.print(curYear.plusYears(-1));
                if (getBaseForRatio(curYear, format, ratioType) != null) {
                    base = getBaseForRatio(curYear, format, ratioType);
                }
                break;
            case SEASON:
                int month = DateTime.now().getMonthOfYear();
                format = DateTimeFormat.forPattern("yyyy");
                DateTime year;
                int quarter;
                if (stataPeriod == 1) {
                    year = DateTime.now();
                    quarter = month / 3 + 1;
                } else {
                    year = DateTime.now().plusMonths(-3);
                    quarter = month / 4 + 1;
                }
                cur = format.print(year) + "" + quarter;
                if (ratioType.equals(RatioType.YEARYEMOM.getCode())) {
                    base = format.print(year.plusYears(-1)) + "" + quarter;
                } else if (quarter == 1) {
                    base = format.print(year.plusYears(-1)) + 4;
                } else {
                    base = format.print(year) + (quarter - 1);
                }
                break;
            case WEEK:
                format = DateTimeFormat.forPattern("yyyyww");
                DateTime curWeek = DateTime.now().plusWeeks(plus);
                cur = format.print(curWeek);
                base = format.print(curWeek.plusWeeks(-1));
                if (getBaseForRatio(curWeek, format, ratioType) != null) {
                    base = getBaseForRatio(curWeek, format, ratioType);
                }
                break;
            default:
        }

        String[] res = new String[]{base, cur};
        return res;
    }

    //同比日期生成
    private static String getBaseForRatio(DateTime cur, DateTimeFormatter formatter, Integer ratioType) {
        if (ratioType.equals(RatioType.YEARYEMOM.getCode())) {
            return formatter.print(cur.plusYears(-1));
        } else if (ratioType.equals(RatioType.MONTHMOM.getCode())) {
            return formatter.print(cur.plusMonths(-1));
        } else if (ratioType.equals(RatioType.WEEKMOM.getCode())) {
            return formatter.print(cur.plusWeeks(-1));
        }
        return null;
    }


    public static String getDefaultTime(Integer value) {
        ViewType viewType = findByInt(value).orElse(null);
        if (viewType == null) {
            return null;
        }
        DateTimeFormatter format;
        switch (viewType) {
            case MONTH:
                format = DateTimeFormat.forPattern("yyyyMM");
                return format.print(DateTime.now().plusMonths(-1));
            case DAY:
                format = DateTimeFormat.forPattern("yyyy-MM-dd");
                return format.print(DateTime.now().plusDays(-1));
            case YEAR:
                format = DateTimeFormat.forPattern("yyyy");
                return format.print(DateTime.now().plusYears(-1));
            case SEASON:
                int i = DateTime.now().monthOfYear().get();
                format = DateTimeFormat.forPattern("yyyy");
                String year = format.print(DateTime.now().plusMonths(-3));
                int quarter = i / 4 + 1;
                return year + quarter;
            case WEEK:
                format = DateTimeFormat.forPattern("yyyyww");
                return format.print(DateTime.now().plusWeeks(-1));
            default:
                return null;
        }
    }

    public static List<String> getDefaultNlpTime(Integer value) {
        ViewType viewType = findByInt(value).orElse(null);
        if (viewType == null) {
            return null;
        }
        DateTimeFormatter format;
        List<String> defaultList = new ArrayList<>();

        switch (viewType) {
            case MONTH:
                format = DateTimeFormat.forPattern("yyyyMM");
                defaultList.add(format.print(DateTime.now().plusMonths(-11)));
                defaultList.add(format.print(DateTime.now()));
                return defaultList;
            case DAY:
                format = DateTimeFormat.forPattern("yyyy-MM-dd");
                defaultList.add(format.print(DateTime.now().plusMonths(-29)));
                defaultList.add(format.print(DateTime.now()));
                return defaultList;
            case YEAR:
                format = DateTimeFormat.forPattern("yyyy");
                defaultList.add(format.print(DateTime.now().plusMonths(-4)));
                defaultList.add(format.print(DateTime.now()));
                return defaultList;
            case SEASON:
                int i = DateTime.now().monthOfYear().get();
                format = DateTimeFormat.forPattern("yyyy");
                String year = format.print(DateTime.now());
                String yearBefore = format.print(DateTime.now().plusMonths(-3));
                int quarter = i / 4 + 1;

                defaultList.add(yearBefore + quarter);
                defaultList.add(year + quarter);
                return defaultList;
            case WEEK:
                format = DateTimeFormat.forPattern("yyyyww");

                defaultList.add(format.print(DateTime.now().plusWeeks(-9)));
                defaultList.add(format.print(DateTime.now()));
                return defaultList;
            default:
                return null;
        }
    }

    public static boolean isDate(Integer value) {
        ViewType viewType = findByInt(value).orElse(null);
        if (viewType == null) {
            return false;
        }
        switch (viewType) {
            case MONTH:
            case DAY:
            case YEAR:
            case SEASON:
            case WEEK:
                return true;
            default:
                return false;
        }
    }

    ViewType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public static Optional<ViewType> findByInt(Integer value) {
        for (ViewType item : ViewType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<ViewType> findByString(String name) {
        for (ViewType item : ViewType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static ViewType findNullableByString(String name) {
        for (ViewType item : ViewType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public String ietoString() {
        return String.valueOf(this.toInt());
    }

    public int toInt() {
        return this.value;
    }

}

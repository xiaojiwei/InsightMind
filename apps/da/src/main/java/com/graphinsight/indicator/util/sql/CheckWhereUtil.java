package com.graphinsight.indicator.util.sql;

import com.google.common.base.Joiner;
import com.graphinsight.indicator.model.dto.BuildSqlParam;
import com.graphinsight.indicator.model.dto.ColumnItemExp;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.internal.FormatStyle;
import org.junit.Assert;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/3/10
 * Desc:
 */
@Slf4j
public class CheckWhereUtil {


    // WHERE 安全检查标志定义,每一位对应一个检查类型, /
    /**
     * 禁用危险关键字(disable SQL dangerous key)
     */
    public static final int CWF_DISABLE_SQLKEY = 0x01;
    /**
     * 禁用常量表达式(disable constant expression)
     */
    public static final int CWF_DISABLE_CONST_EXP = 0x02;
    /**
     * 禁用常量等价表达式(disable constant equation expression)
     */
    public static final int CWF_DISABLE_EQUATION_EXP = 0x04;
    /**
     * 禁用常量IN表达式(disable constant IN expression)
     */
    public static final int CWF_DISABLE_IN_EXP = 0x08;
    /**
     * 安全检查标志, {@link #checkWhere(String)} 会根据此标志确定是否执行指定的检查
     */
    private static int whereCheckFlag = CWF_DISABLE_SQLKEY;

    private static Matcher regexMatcher(String regex, int flags, String input) {
        return Pattern.compile(regex, flags).matcher(input);
    }

    /**
     * 检查输入的字符串是否指定指定的正则表达,如果找到匹配则抛出{@link IllegalArgumentException}异常
     *
     * @param checkFlags 是否执行正则表达匹配的检查标志,参见 CWF_DISABLE_xxx 系列定义
     * @param regex      正则表达式
     * @param flags      正则表达式匹配标志参见 {@link Pattern#compile(String, int)}
     * @param input      SQL 字符串
     * @param errmsg     抛出异常时输出的字符串
     */
    private static void checkMatchFind(int checkFlags, String regex, int flags, String input, String errmsg) {
        if (isEnable(checkFlags)) {
            Matcher matcher = regexMatcher(regex, flags, input);
            if (matcher.find()) {
                throw new IllegalArgumentException(String.format(errmsg + " '%s'", matcher.group()));
            }
        }
    }

    private static boolean isEnable(int checkflag) {
        return (whereCheckFlag & checkflag) == checkflag;
    }

    /**
     * 对 where SQL 语句安全性(防注入攻击)检查
     *
     * @param where
     * @return always where
     * @throws IllegalArgumentException where 语句有安全问题
     */
    public static String checkWhere(String where) {
        where = null == where ? "" : where.trim();
        if (!where.isEmpty()) {
            if (!where.toUpperCase().startsWith("WHERE")) {
                throw new IllegalArgumentException("WHERE expression must start with 'WHERE'(case insensitive)");
            }
            /** 禁止字符串常量比较
             *  如 'owner_id'='owner' "_id"
             *  禁止左右完全相等的比较
             *  如 hello=hello
             *  禁止数字常量比较
             *  如 7.0=7
             *  如  .12='12'
             *  如 ".1"=.1
             * */
            checkMatchFind(CWF_DISABLE_EQUATION_EXP, "((\'[^\']*\'\\s*|\"[^\"]*\\\"\\s*)+\\s*=\\s*(\'[^\']*\'\\s*|\"[^\"]*\"\\s*)+|([+-]?(?:\\d*\\.)?\\d+)\\s*=\\s*[+-]?(?:\\d*\\.)?\\d+|([^\'\"\\s]+)\\s*=\\s*\\5\\b|([+-]?(?:\\d*\\.)?\\d+)\\s*=\\s*(\'|\")[+-]?(?:\\d*\\.)?\\d+\\s*\\7|(\'|\")([+-]?(?:\\d*\\.)?\\d+)\\s*\\8\\s*=\\s*[+-]?(?:\\d*\\.)?\\d+)", 0, where, "INVALID WHERE equation  expression");
            if (isEnable(CWF_DISABLE_CONST_EXP)) {
                /**
                 * 禁止恒为true的判断条件
                 * -- 禁止 非0数字常量为判断条件
                 * -- 禁止 not false,not true
                 * 如: where "-055.55asdfsdfds0"  or  true  or not false
                 */
                Matcher m1 = regexMatcher("((?:where|or)\\s+)(not\\s+)?(false|true|(\'|\")([+-]?\\d+(\\.\\d+)?).*\\4)", Pattern.CASE_INSENSITIVE, where);
                while (m1.find()) {
                    boolean not = null != m1.group(2);
                    String g3 = m1.group(3);
                    Boolean isTrue;
                    if (g3.equalsIgnoreCase("true")) {
                        isTrue = true;
                    } else if (g3.equalsIgnoreCase("false")) {
                        isTrue = false;
                    } else {
                        String g5 = m1.group(5);
                        isTrue = 0 != Double.valueOf(g5);
                    }
                    if (not) {
                        isTrue = !isTrue;
                    }
                    if (isTrue) {
                        throw new IllegalArgumentException(String.format("INVALID WHERE const true  expression '%s'", m1.group()));
                    }
                }
            }
            /**
             * 禁止字符串常量或数字常量开头的 IN语句
             * 如 7.0 IN ( 15, 7)
             * 如 'hello' IN ( 'hello', 'world')
             * */
            checkMatchFind(CWF_DISABLE_IN_EXP, "(((\'|\")[^\']*\\3\\s*)|[\\d\\.+-]+\\s*)\\s+IN\\s+\\(.*\\)", Pattern.CASE_INSENSITIVE, where, "INVALID IN expression");
            /** 删除where中所有字符串常量,再进行关键字检查,避免字符串中的包含的关键引起误判 */
            String nonestr = where.replaceAll("(\'[^\']*\'|\"[^\"]*\")", "");

//            private static final Pattern SQL_SYNTAX_PATTERN = Pattern.compile("(insert|delete|update|select|create|drop|truncate|grant|alter|deny|revoke|call|execute|exec|declare|show|rename|set|case|when)" +
//                    "\\s+.*(into|from|set|where|table|database|view|index|on|cursor|procedure|trigger|for|password|union|and|or)|(select\\s*\\*\\s*from\\s+)|(and|or)\\s+.*(like|=|>|<|in|between|is|not|exists)", Pattern.CASE_INSENSITIVE);
//            // 注释字符
//            private static final Pattern SQL_COMMENT_PATTERN = Pattern.compile("'.*(or|union|--|#|/\\*|;)", Pattern.CASE_INSENSITIVE);


            checkMatchFind(CWF_DISABLE_SQLKEY, "\\b(exec|create|insert|delete|update|join|union|master|truncate|drop|rename)\\b", Pattern.CASE_INSENSITIVE, nonestr, "ILLEGAL SQL key");
        }
        return where;
    }

    /**
     * 设置安全检查标志，默认{@value #CWF_DISABLE_SQLKEY}
     *
     * @param whereCheckFlag
     */
    public static void setWhereCheckFlag(int whereCheckFlag) {
        CheckWhereUtil.whereCheckFlag = whereCheckFlag;
    }

    /**
     * 调用 {@link #checkWhere(String)}检查输入的SQL字符串是否合法,
     *
     * @param input       WHERE SQL 字符串
     * @param assertLegal 字符串合法性预定义值
     * @throws IllegalArgumentException SQL字符串不合法
     * @throws AssertionError           如果{@code assertLegal}为true,checkWhere 没有检查出异常则抛出此异常
     *                                  如果{@code assertLegal}为false,checkWhere 检查出异常则抛出此异常
     */
    public static void testCheckWhere(String input, boolean assertLegal) {
        try {
            checkWhere(input);
            if (!assertLegal) {
                throw new AssertionError();
            }
        } catch (Exception e) {
            System.out.printf("%s\n", e.getMessage());
            if (assertLegal) {
                throw new AssertionError();
            }
        }
    }

    /**
     * WHERE语句全要素检测测试
     */
    public static void main(String[] args) {
        setWhereCheckFlag(0xffffffff);
        testCheckWhere("WHERE ", true);
        testCheckWhere("WHERE name='1342342' or age=15", true);
        testCheckWhere("WHERE name like '1342342%' and age>15 and birthdate='1990-01-01'", true);
        testCheckWhere("WHERE name='1342342%' and age>15 and birthdate='1990-01-01'", true);
        testCheckWhere("WHERE 1=1", false);
        testCheckWhere("WHERE 1=1.0", false);
        testCheckWhere("WHERE 1=1.0", false);
        testCheckWhere("WHERE  .12=\'12\' or  \".1\"=.10 1=1 \"hello\"=\'world\'   hello=hello", false);
        testCheckWhere("WHERE true", false);
        testCheckWhere("WHERE false", true);
        testCheckWhere("WHERE not false", false);
        testCheckWhere("WHERE '12345'='1342342' or age=15", false);
        testCheckWhere("WHERE age=15 or 1=2", false);
        testCheckWhere("WHERE age in ()", true);
        testCheckWhere("WHERE age in (1,2,3,45)", true);
        testCheckWhere("WHERE 1 in ()", false);
        testCheckWhere("WHERE 1 in (1,2,3,45)", false);
        testCheckWhere("WHERE 'hello' in ('hello')", false);
        testCheckWhere("WHERE 'hello' in ('hello')", false);
        testCheckWhere("WHERE a=1 union select * from systemtable", false);
        testCheckWhere("WHERE a in ( select a from systemtable)", true);
        /** 允许字符串中有危险关键字 */
        testCheckWhere("WHERE name='union' or age=15", true);
    }

}


package com.graphinsight.indicator;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.util.JdbcConstants;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import java.util.Set;

import java.util.List;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TestSQL {

    public static void main(String[] args) {

        boolean check = false;

//        String sql = "SELECT concat(aa.TABLE_SCHEMA,‘.’,aa.TABLE_NAME) as id,aa.TABLE_NAME,aa.TABLE_SCHEMA,aa.TABLE_COMMENT,aa.TABLE_ROWS,aa.TABLE_TYPE,bb.COLUMN_NAMES FROM information_schema.TABLES aa inner join (select TABLE_SCHEMA,TABLE_NAME,CONCAT(GROUP_CONCAT( DISTINCT CONCAT(COLUMN_NAME,’ ‘, COLUMN_TYPE,IF(COLUMN_DEFAULT IS NULL, ‘’, CONCAT(’ DEFAULT ‘, COLUMN_DEFAULT)),IF(IS_NULLABLE = ‘NO’, ’ NOT NULL’, ‘’),IF(COLUMN_COMMENT = ‘’, ‘’, CONCAT(’ COMMENT ‘’‘, COLUMN_COMMENT, ‘’’')) ) ORDER BY ORDINAL_POSITION SEPARATOR ',' )) as COLUMN_NAMES from information_schema.columns` group by TABLE_SCHEMA,TABLE_NAME ) bb on aa.TABLE_SCHEMA=bb.TABLE_SCHEMA and aa.TABLE_NAME=bb.TABLE_NAME WHERE aa.TABLE_SCHEMA=‘111’";

//        String sql = "'一线城市', '新一线城市','二线城市','三线城市','四线城市','五线城市','其它'";
//        sql = "'2025-06-09'";
//        sql = "1";
//        sql = "不满意";
//        sql = "select distinct fuel_type from da_mart_compare.dwd_market_competition_insur_df"
//        sql = "202505,202506";
//        sql = "d. week_long_desc";
        String sql = "20250601,20250602,20250603,20250604,20250605,20250606,20250607,20250608,20250609,20250610,20250611,20250612,20250613,20250614,20250615,20250616,20250617,20250618,20250619,20250620,20250621,20250622,20250623,20250624,20250625,20250626,20250627,20250628,20250629";
//        sql = "'汽油和天然气双燃料','插电式混合动力','天然气','甲醇','汽油','燃料电池','普通混合动力','纯电动','柴油','汽油和液化石油气双燃料','常规混合动力'";

        //1、判断是否是纯数字
        check = isNumeric(sql);
        System.out.println("is Number = " + check);

        //2、是否纯汉字
        check = isAllChinese(sql);
        System.out.println("is Chinese = " + check);

        //3、字符串替换成？
        String output = sql.replaceAll("\"[^\"]*\"", "?");
        output = output.replaceAll("‘[^’]*’", "?");
        System.out.println(output);

        //4、将数字替换成？
        output = sql.replaceAll("\\d+", "?");
        System.out.println(output);

        //5、将所有'？‘、','号替换成null
        output = output.replaceAll("\\?", "");
        output = output.replaceAll(",", "");

        //6、判断是否为小于3个字符
        check = isLengthLessThanThree(output);
        System.out.println("is LessThanThree = " + check);

        //7、转换为小写
        output = output.toLowerCase();

        //8、MD5 构造
        String md5 = toMD5(output);
        System.out.println("md5 = " + md5);

        System.out.println("final = " + output);


    }

    /**
     * 字符串小于3个。
     * @param str
     * @return
     */
    public static boolean isLengthLessThanThree(String str) {
        if (null == str && str.isEmpty()) {
            return true;
        }

        return str.length() < 3;
    }

    public static String toMD5(String input) {
        if (input == null) {
            return null; // 如果输入为 null，则返回 null
        }
        try {
            // 获取 MD5 摘要算法实例
            MessageDigest md = MessageDigest.getInstance("MD5");

            // 将输入字符串转换为字节数组并计算哈希值
            byte[] digest = md.digest(input.getBytes());

            // 将字节数组转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b); // 转为十六进制
                if (hex.length() == 1) {
                    hexString.append('0'); // 补零
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 hashing failed", e); // 抛出运行时异常
        }

    }

    public static boolean isNumeric(String str) {
        return str != null && str.matches("\\d+");
    }

    public static boolean isAllChinese(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (char c : str.toCharArray()) {
            if (c < '\u4e00' || c > '\u9fa5') {
                return false;
            }
        }

        return true;
    }

}

package com.graphinsight.indicator.temp;

import org.owasp.esapi.ESAPI;
import org.owasp.esapi.codecs.MySQLCodec;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class SqliteSQLGeneratorWrite {

    private static Connection conn = null;
    private static Statement sm = null;

    public static void main(String[] args) throws Exception {

        connectSQL("com.mysql.jdbc.Driver", "jdbc:mysql://192.168.58.132:3306/indicator?allowMultiQueries=true&useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&autoReconnect=true&failOverReadOnly=false", "root", "123456");//连接数据库
        readFile();
    }

    /**
     * 连接数据库 创建statement对象
     *
     * @param driver
     * @param url
     * @param UserName
     * @param Password
     */
    public static void connectSQL(String driver, String url, String UserName, String Password) {
        try {
            Class.forName(driver).newInstance();
            conn = DriverManager.getConnection(url, UserName, Password);
            sm = conn.createStatement();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void execSql(Connection conn, String sql) {

    }

    public static String readFile() {
        String path = "/Users/xiaojiwei/Documents/db2022-04-07.sql";
        File file = new File(path);
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));//构造一个BufferedReader类来读取文件
            String s = null;
            int i = 1;
            while ((s = br.readLine()) != null) {//使用readLine方法，一次读一行
                try {
                    String enters = ESAPI.encoder().encodeForSQL(new MySQLCodec(MySQLCodec.Mode.ANSI), s);
                    sm.execute(enters);
                } catch (Exception ex) {
                    System.err.println(s);
                    ex.printStackTrace();
                }
                System.out.println(i++);

            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

}

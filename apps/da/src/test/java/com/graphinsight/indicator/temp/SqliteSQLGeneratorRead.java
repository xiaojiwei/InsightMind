package com.graphinsight.indicator.temp;

import org.owasp.esapi.ESAPI;
import org.owasp.esapi.codecs.MySQLCodec;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class SqliteSQLGeneratorRead {

    private static Connection conn = null;
    private static Statement sm = null;
    private static String insert = "REPLACE INTO";//插入sql
    private static String values = "VALUES";//values关键字
    private static List<String> tableList = new ArrayList<String>();//全局存放表名列表
    private static List<String> insertList = new ArrayList<String>();//全局存放insertsql文件的数据
    private static String filePath = "/Users/xiaojiwei/temp/test.sql";//绝对路径 导出数据的文件

    static {
        tableList.add("base_configure");
//        tableList.add("cache_reload_task");
        tableList.add("category");
        tableList.add("complex_measure_dependency_tree");
        tableList.add("data_base_info");
        tableList.add("data_source");
//        tableList.add("department");
//        tableList.add("dim_all_values");
        tableList.add("dimension");
        tableList.add("dimension_application");
        tableList.add("dimension_dimtable_connect");
        tableList.add("dimension_filter");
        tableList.add("dimension_operator");
        tableList.add("dimension_operator_value");
        tableList.add("dimension_values");
        tableList.add("dw_column");
        tableList.add("dw_table");
        tableList.add("filter");
        tableList.add("folder");
        tableList.add("hibernate_sequence");
        tableList.add("hierarchy");
//        tableList.add("java_info");
        tableList.add("level");
        tableList.add("measure");
        tableList.add("measure_application");
        tableList.add("measure_grade");
        tableList.add("mesa_grade_connect");
        tableList.add("operator");
        tableList.add("operator_data_list");
        tableList.add("order_value");
        tableList.add("order_data_list");
//        tableList.add("query_plan");
//        tableList.add("request");
        tableList.add("t_order");
//        tableList.add("user");
        tableList.add("value_format");
    }

    public static String executeSelectSQLFile() throws Exception {
        List<String> listSQL = new ArrayList<String>();
        connectSQL("com.mysql.jdbc.Driver", "jdbc:mysql://192.168.58.132:3306/indicator?allowMultiQueries=true&useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&autoReconnect=true&failOverReadOnly=false", "root", "123456");//连接数据库
        listSQL = createSQL();//创建查询语句
        executeSQL(conn, sm, listSQL, tableList);//执行sql并拼装
        createFile();//创建文件
        return null;
    }

    /**
     *
     * @return
     * @throws Exception
     */
    private static List<String> createSQL() throws Exception {

        List<String> listSQL = new ArrayList<String>();
        for (String s : tableList) {
            listSQL.add("select * from " + s);
        }

        return listSQL;

    }



    /**
     * 创建insertsql.txt并导出数据
     */
    private static void createFile() {
        File file = new File(filePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.out.println("创建文件名失败！！");
                e.printStackTrace();
            }
        }
        FileWriter fw = null;
        BufferedWriter bw = null;
        try {
            fw = new FileWriter(file);
            bw = new BufferedWriter(fw);
            if (insertList.size() > 0) {
                for (int i = 0; i < insertList.size(); i++) {
                    bw.append(insertList.get(i));
                    bw.append("\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bw.close();
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

    /**
     * 执行sql并返回插入sql
     *
     * @param conn
     * @param sm
     * @param listSQL
     * @throws java.sql.SQLException
     */
    public static void executeSQL(Connection conn, Statement sm, List listSQL, List listTable) throws SQLException {
        List<String> insertSQL = new ArrayList<String>();
        ResultSet rs = null;
        try {
            rs = getColumnNameAndColumeValue(sm, listSQL, listTable, rs);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rs.close();
            sm.close();
            conn.close();
        }
    }

    private static String formatValue(String value) {
        if (null != value) {
            value = ESAPI.encoder().encodeForSQL(new MySQLCodec(MySQLCodec.Mode.ANSI), value);
        }
        return value;
    }

    /**
     * 获取列名和列值
     *
     * @param sm
     * @param listSQL
     * @param rs
     * @return
     * @throws java.sql.SQLException
     */
    private static ResultSet getColumnNameAndColumeValue(Statement sm,
                                                         List listSQL, List listTable, ResultSet rs) throws SQLException {

        for (int j = 0; j < listSQL.size(); j++) {
            String sql = String.valueOf(listSQL.get(j));
            rs = sm.executeQuery(sql);
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();
            while (rs.next()) {
                StringBuffer ColumnName = new StringBuffer();
                StringBuffer ColumnValue = new StringBuffer();
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    if (null != value) {
                        value = value.replaceAll("\r", "");
                        value = value.replaceAll("\n", "");
                    }
                    if (i == columnCount) {
                        ColumnName.append(rsmd.getColumnName(i));
                        if (Types.CHAR == rsmd.getColumnType(i) || Types.VARCHAR == rsmd.getColumnType(i)
                                || Types.LONGVARCHAR == rsmd.getColumnType(i)) {
                            if (value == null) {
                                ColumnValue.append("null");
                            } else {
                                value = formatValue(value);
                                ColumnValue.append("'").append(value).append("'");
                            }
                        } else if (Types.SMALLINT == rsmd.getColumnType(i) || Types.INTEGER == rsmd.getColumnType(i)
                                || Types.BIGINT == rsmd.getColumnType(i) || Types.FLOAT == rsmd.getColumnType(i)
                                || Types.DOUBLE == rsmd.getColumnType(i) || Types.NUMERIC == rsmd.getColumnType(i)
                                || Types.DECIMAL == rsmd.getColumnType(i)) {
                            if (value == null) {
                                ColumnValue.append("null");
                            } else {
                                value = formatValue(value);
                                ColumnValue.append(value);
                            }
                        } else if (Types.DATE == rsmd.getColumnType(i) || Types.TIME == rsmd.getColumnType(i)
                                || Types.TIMESTAMP == rsmd.getColumnType(i)) {
                            if (value == null) {
                                ColumnValue.append("null");
                            } else {
//                                ColumnValue.append("timestamp'").append(value).append("'");
                                value = formatValue(value);
                                ColumnValue.append("'").append(value).append("'");
                            }
                        } else {
                            if (value == null) {
                                ColumnValue.append("null");
                            } else {
                                value = formatValue(value);
                                ColumnValue.append(value);
                            }
                        }
                    } else {
                        ColumnName.append(rsmd.getColumnName(i) + ",");
                        if (Types.CHAR == rsmd.getColumnType(i) || Types.VARCHAR == rsmd.getColumnType(i)
                                || Types.LONGVARCHAR == rsmd.getColumnType(i)) {
                            if (value == null) {
                                ColumnValue.append("null,");
                            } else {
                                value = formatValue(value);
                                ColumnValue.append("'").append(value).append("',");
                            }
                        } else if (Types.SMALLINT == rsmd.getColumnType(i) || Types.INTEGER == rsmd.getColumnType(i)
                                || Types.BIGINT == rsmd.getColumnType(i) || Types.FLOAT == rsmd.getColumnType(i)
                                || Types.DOUBLE == rsmd.getColumnType(i) || Types.NUMERIC == rsmd.getColumnType(i)
                                || Types.DECIMAL == rsmd.getColumnType(i)) {
                            if (value == null) {
                                ColumnValue.append("null,");
                            } else {
                                value = formatValue(value);
                                ColumnValue.append(value).append(",");
                            }
                        } else if (Types.DATE == rsmd.getColumnType(i) || Types.TIME == rsmd.getColumnType(i)
                                || Types.TIMESTAMP == rsmd.getColumnType(i)) {
                            if (value == null) {
                                ColumnValue.append("null,");
                            } else {
//                                ColumnValue.append("timestamp'").append(value).append("',");
                                value = formatValue(value);
                                ColumnValue.append("'").append(value).append("',");
                            }
                        } else {
                            if (value == null) {
                                ColumnValue.append("null,");
                            } else {
                                value = formatValue(value);
                                ColumnValue.append(value).append(",");
                            }
                        }
                    }
                }
                //System.out.println(ColumnName.toString());
                //System.out.println(ColumnValue.toString());
                insertSQL(listTable.get(j).toString(), ColumnName, ColumnValue);
            }
        }
        return rs;
    }

    /**
     * 拼装insertsql 放到全局list里面
     *
     * @param ColumnName
     * @param ColumnValue
     */
    private static void insertSQL(String TableName, StringBuffer ColumnName,
                                  StringBuffer ColumnValue) {
        StringBuffer insertSQL = new StringBuffer();
        insertSQL.append(insert).append(" ").append(TableName).append("(").append(ColumnName.toString())
                .append(")").append(values).append("(").append(ColumnValue.toString()).append(");");
        insertList.add(insertSQL.toString());
        System.out.println(insertSQL.toString());
    }

    public static void main(String[] args) throws Exception {
        executeSelectSQLFile();
    }

    public static void exportDB(HttpServletResponse response) {
        try {
            executeSelectSQLFile();
            writeSheet(response);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public static void writeSheet(HttpServletResponse response) throws IOException {

        // 清空response
        response.reset();
        // 设置response的Header
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, PATCH, DELETE, PUT");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.setHeader("Content-Disposition", "attachment;filename=db.sql");
        OutputStream os = new BufferedOutputStream(response.getOutputStream());
        response.setContentType("application/vnd.ms-excel;charset=gb2312;filename=db.sql");
        // 将excel写入到输出流中
        BufferedOutputStream buff = null;
        StringBuffer write = new StringBuffer();
        String enter = "\r\n";
        ServletOutputStream outSTr = null;
        try {
            outSTr = response.getOutputStream(); // 建立
            buff = new BufferedOutputStream(outSTr);
            //把内容写入文件
            if(insertList.size()>0){
                for (int i = 0; i < insertList.size(); i++) {
                    write.append(insertList.get(i));
                    write.append(enter);
                }
            }

            buff.write(write.toString().getBytes("UTF-8"));
            buff.flush();
            buff.close();
            os.flush();
            os.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                buff.close();
                outSTr.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

}

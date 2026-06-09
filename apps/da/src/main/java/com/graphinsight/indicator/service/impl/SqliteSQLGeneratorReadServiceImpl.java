package com.graphinsight.indicator.service.impl;

import org.mortbay.log.Log;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.codecs.MySQLCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service("sqliteSQLGeneratorReadService")
public class SqliteSQLGeneratorReadServiceImpl {

    @Value(value = "${spring.datasource.dynamic.datasource.mysql.url}")
    private String url;

    @Value(value = "${spring.datasource.dynamic.datasource.mysql.driver-class-name}")
    private String driverClassName;

    @Value(value = "${spring.datasource.dynamic.datasource.mysql.username}")
    private String userName;

    @Value(value = "${spring.datasource.dynamic.datasource.mysql.password}")
    private String password;

    private static Connection conn = null;
    private static Statement sm = null;
    private static String insert = "REPLACE INTO";//插入sql
    private static String values = "VALUES";//values关键字
    //private static List<String> tableList = new ArrayList<String>();//全局存放表名列表
    private static List<String> blackTableList = new ArrayList<String>();//全局存放表名列表
    private static List<String> insertList = new ArrayList<String>();//全局存放insertsql文件的数据
    private static List<String> remakeList = new ArrayList<String>();//全局存放remakeList文件的数据
    private static String filePath = "/Users/xiaojiwei/temp/test.sql";//绝对路径 导出数据的文件


    @Value("#{'${tables:organization}'.split(',')}")
    private List<String> tableList;

    static {
//        blackTableList.add("audit_log");
//        blackTableList.add("cache_reload_task");
//        blackTableList.add("department");
//        blackTableList.add("t_super_admin");
//        blackTableList.add("dim_all_values");
//        blackTableList.add("employee");
//        blackTableList.add("employee_context");
//        blackTableList.add("hibernate_sequence");
//        blackTableList.add("user_audit_log");
//        blackTableList.add("amp_outsource_user");
//        blackTableList.add("java_info");
//        blackTableList.add("employee_context");
//        blackTableList.add("operate_grant_config");
//        blackTableList.add("user_data_log");
//        blackTableList.add("widget_detail");
//        blackTableList.add("indicator_auth");
//        blackTableList.add("query_plan");
//        blackTableList.add("request");
//        blackTableList.add("tur_employee");
//        //blackTableList.add("organization");
//        blackTableList.add("vendor_staff");
//        blackTableList.add("user");
//        blackTableList.add("wx_push_log");
//        blackTableList.add("table_histogram");
//        blackTableList.add("measure_similarity");
//        blackTableList.add("down_file");
//        blackTableList.add("widget");
//        blackTableList.add("widget_detail");
//        blackTableList.add("dashboard");
//        blackTableList.add("dashboard_folder");
//        blackTableList.add("dashboard_version");
//        blackTableList.add("query_base_configure");
//        blackTableList.add("query_base_table");
//        blackTableList.add("query_data_source");
        //tableList.add("organization");
           /*
//        tableList.add("employee");
        tableList.add("auth_blacklist");
        tableList.add("auth_element");
        tableList.add("auth_element_measure");
        tableList.add("base_configure");
//        tableList.add("cache_reload_task");
        tableList.add("category");
        tableList.add("classification");
        tableList.add("complex_measure_dependency_tree");
        tableList.add("data_base_info");
        tableList.add("data_source");
        tableList.add("decision_tree");
        tableList.add("decision_tree_detail");
//        tableList.add("department");
//        tableList.add("dim_all_values");
        tableList.add("dim_context_relation");
        tableList.add("dimension");
        tableList.add("dimension_application");
        tableList.add("dimension_dimtable_connect");
        tableList.add("dimension_filter");
        tableList.add("dimension_operator");
        tableList.add("dimension_operator_value");
        tableList.add("dimension_values");
        tableList.add("dw_column");
        tableList.add("dw_table");
//        tableList.add("employee");
//        tableList.add("employee_context");
        tableList.add("filter");
        tableList.add("folder");
//        tableList.add("hibernate_sequence");
        tableList.add("hierarchy");
//        tableList.add("java_info");
        tableList.add("level");
        tableList.add("measure");
        tableList.add("measure_application");
        tableList.add("measure_grade");
        tableList.add("mesa_grade_connect");
//        tableList.add("operate_grant_config");
        tableList.add("operator");
        tableList.add("operator_data_list");
        tableList.add("order_data_list");
        tableList.add("order_value");
        tableList.add("organization");
//        tableList.add("query_plan");
//        tableList.add("request");
        tableList.add("space_admin");
        tableList.add("space_blacklist");
        tableList.add("space_employee");
        tableList.add("space_owner");
        tableList.add("space_role");
        tableList.add("t_auth");
        tableList.add("t_department");
        tableList.add("t_order");
        tableList.add("t_ratio");
        tableList.add("t_space");
        tableList.add("t_super_admin");
//        tableList.add("user");
        tableList.add("value_format");
*/
//        blackTableList.add("audit_log");
//        blackTableList.add("cache_reload_task");
//        blackTableList.add("dim_all_values");
//        blackTableList.add("employee");
//        blackTableList.add("dim_all_values_bak");
//        blackTableList.add("measure_relate_recode");

    }

    public  void remakeList() throws SQLException {
        remakeList.clear();

        for (String table : tableList) {
            String dropSql = "DROP TABLE IF EXISTS " + table + ";";
            remakeList.add(dropSql);
            remakeList.add(createTableSql(table));
        }
    }

    public static void initTableList() throws SQLException {
//        tableList.clear();
        String sql = "show tables";
        ResultSet rs = null;
        try {
            rs = sm.executeQuery(sql);
            while (rs.next()){
                String s = rs.getString(1);
                if (! blackTableList.contains(s)){
//                    tableList.add(s);
                }
            }
        } finally {
            if(rs != null){
                rs.close();
            }
        }
    }

    private static String createTableSql(String tableName) throws SQLException {
        String sql = "show create table " + tableName;
        ResultSet rs = null;
        try {
            rs = sm.executeQuery(sql);
            while (rs.next()){
                String s = rs.getString("Create Table");
                return s + ";";
            }
            return ";";
        } finally {
            if(rs != null){
                rs.close();
            }
        }
    }

    public String executeSelectSQLFile() throws Exception {
        connectSQL(driverClassName, url, userName, password);//连接数据库
//        initTableList();
        List<String> listSQL = new ArrayList<String>();
        remakeList(); // 初始化表
        listSQL = createSQL();//创建查询语句
        executeSQL(conn, sm, listSQL, tableList);//执行sql并拼装
//        createFile();//创建文件
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private  List<String> createSQL() throws Exception {

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
                Log.info(e.getMessage());
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
            Log.info(e.getMessage());
        } finally {
            try {
                bw.close();
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.info(e.getMessage());
            }
        }
    }

    /**
     * 连接数据库 创建statement对象
     *
     * @param driver
     * @param url
     * @param userName
     * @param password
     */
    public static void connectSQL(String driver, String url, String userName, String password) {
        try {
            Class.forName(driver).newInstance();
            conn = DriverManager.getConnection(url, userName, password);
            sm = conn.createStatement();
        } catch (Exception e) {
            e.printStackTrace();
            Log.info(e.getMessage());
        }
    }

    /**
     * 执行sql并返回插入sql
     *
     * @param conn
     * @param sm
     * @param listSQL
     * @throws SQLException
     */
    public static void executeSQL(Connection conn, Statement sm, List listSQL, List listTable) throws SQLException {
        List<String> insertSQL = new ArrayList<String>();
        ResultSet rs = null;
        try {
            rs = getColumnNameAndColumeValue(sm, listSQL, listTable, rs);
        } catch (Exception e) {
            e.printStackTrace();
            Log.info(e.getMessage());
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
     * @throws SQLException
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

    public void exportDB(HttpServletResponse response) {
        try {
            Log.info("导出脚本开始....");
            insertList.clear();
            executeSelectSQLFile();
            Log.info("导出数据完毕....");
            writeSheet(response);

        } catch (Exception ex) {
           Log.info(ex.getMessage());
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
        java.util.Date dt = new Date();
        String year = String.format("%tY", dt);
        String mon = String.format("%tm", dt);
        String day = String.format("%td", dt);

        String dateStr = year + "-" + mon + "-" + day;
        String dbName = "db" + dateStr + ".sql";
        response.setHeader("Content-Disposition", "attachment;filename=" + dbName);
        OutputStream os = new BufferedOutputStream(response.getOutputStream());
        response.setContentType("application/vnd.ms-excel;charset=gb2312;filename=" + dbName);
        // 将excel写入到输出流中
        BufferedOutputStream buff = null;
        StringBuffer write = new StringBuffer();
        String enter = "\r\n";
        ServletOutputStream outSTr = null;
        try {
            outSTr = response.getOutputStream(); // 建立
            buff = new BufferedOutputStream(outSTr);
            //把内容写入文件
            if (remakeList.size() > 0) {
                for (int i = 0; i < remakeList.size(); i++) {
                    write.append(remakeList.get(i));
                    write.append(enter);
                }
            }
            if (insertList.size() > 0) {
                for (int i = 0; i < insertList.size(); i++) {
                    write.append(insertList.get(i));
                    write.append(enter);
                }
            }

            buff.write(write.toString().getBytes("UTF-8"));


        } catch (Exception e) {
            e.printStackTrace();
            Log.info(e.getMessage());
        } finally {
            try {
                buff.close();
                outSTr.close();
                os.flush();
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                Log.info(e.getMessage());
            }
        }

    }

    public static void main(String[] args) {

    }

}

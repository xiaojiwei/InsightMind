package com.graphinsight.indicator;


import com.graphinsight.indicator.model.ColumnTypeInfo;
import com.graphinsight.indicator.model.MemorySchema;
import com.graphinsight.indicator.model.QueryResultColumnInfo;
import com.graphinsight.indicator.model.TempTable;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

public class TempCubeTest {



    public static void main(String[] cube) throws Exception {

        MemorySchema memorySchema = new MemorySchema();
        Statement statement = null;
        ResultSet resultSet = null;
        Connection connection = null;
        String sql = "select * from TEMP.TEMP_TABLE , '_d_alis_城市ID', sum('_m_alis_60s响应量')";
        sql = "select  _d_alis_日期, _d_alis_城市, sum(_m_alis_60s响应量), sum(_m_alis_响应时长) from TEMP.TEMP_TABLE group by rollup(_d_alis_日期, _d_alis_城市)";
        TempTable cubeTable = null;
        try {

            Class.forName("org.apache.calcite.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:calcite:");
            CalciteConnection calciteConnection =  connection.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = calciteConnection.getRootSchema();

            TempTable tempTable = exec();

            memorySchema.addTable("TEMP_TABLE", tempTable);

            memorySchema.build();
            rootSchema.add("TEMP", memorySchema);
            statement = connection.createStatement();

            resultSet = statement.executeQuery(sql);
            int k = resultSet.getMetaData().getColumnCount();
            for (int i = 1; i <= k; i++) {
                String name = resultSet.getMetaData().getColumnLabel(i);
                QueryResultColumnInfo info = new QueryResultColumnInfo();
                info.setName(name);
            }

            cubeTable = new TempTable();
            List<Object[]> dataList = cubeTable.getDataList();
            int s = 0;
            while (resultSet.next()) {
                s++;
                int n = resultSet.getMetaData().getColumnCount();
                Object[] rows = new Object[n + 1];
                rows[0] = UUID.randomUUID().toString();
                for (int i = 1; i <= n; i++) {

                    String value = resultSet.getString(i);

                    if (value == null) {
                        value = "全部";
                    }

                    rows[i] = value;
                    System.out.print(value + "  ");

                }

                System.out.println();
                dataList.add(rows);

            }

            //虚拟主键
            List<ColumnTypeInfo> columnTypeInfoList = cubeTable.getColumnTypeInfoList();
            ColumnTypeInfo columnTypeInfo = ColumnTypeInfo.build("TEMP_ID", "12");

            columnTypeInfoList.add(columnTypeInfo);

            String[] columnNames = new String[4];
            String[] columnTypes = new String[4];

            columnNames[0] = "_d_alis_日期";
            columnTypes[0] = "12";

            columnNames[1] = "_d_alis_城市";
            columnTypes[1] = "12";

            columnNames[2] = "_m_alis_60s响应量";
            columnTypes[2] = "-6";

            columnNames[3] = "_m_alis_响应时长";
            columnTypes[3] = "-6";

            for (int idx = 0; idx < columnNames.length; idx++) {

                String name = columnNames[idx];
                String type = String.valueOf(columnTypes[idx]);

                columnTypeInfo = ColumnTypeInfo.build(name.toUpperCase(), type);
                columnTypeInfoList.add(columnTypeInfo);

            }

            System.out.println(cubeTable);


        } catch (Exception ex) {
            ex.printStackTrace();
//            throw new RuntimeException(ex.getMessage());
        } finally {

            resultSet.close();
            statement.close();
            connection.close();

        }

        buildCubeTable(cubeTable);

    }

    private static void query(Connection connection, String sql) throws Exception {

        Statement statement = null;
        ResultSet resultSet = null;

        statement = connection.createStatement();
        resultSet = statement.executeQuery(sql);

        List<Object[]> dataList = new LinkedList<>();

        while (resultSet.next()) {
            int n = resultSet.getMetaData().getColumnCount();
            Object[] rows = new Object[n + 1];
            for (int i = 1; i <= n; i++) {

                String value = resultSet.getString(i);
                rows[i] = value;
                System.out.print(value);

            }

            System.out.println();
            dataList.add(rows);

        }

    }

    private static void buildCubeTable(TempTable cubeTable) throws Exception {

        MemorySchema memorySchema = new MemorySchema();
        Statement statement = null;
        ResultSet resultSet = null;
        Connection connection = null;
        String sql = "select * from  TEMP.CUBE_TABLE";
        sql = "select _m_alis_响应时长 from TEMP.CUBE_TABLE  where _d_alis_日期='2022年01月13日' and _d_alis_城市='商洛市'";
        try {

            Class.forName("org.apache.calcite.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:calcite:");
            CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = calciteConnection.getRootSchema();

            TempTable tempTable = cubeTable;

            memorySchema.addTable("CUBE_TABLE", tempTable);

            memorySchema.build();
            rootSchema.add("TEMP", memorySchema);

//            ResultSet result = connection.getMetaData().getTables( null, null, null, null);
//            while( result.next()) {
//                System. out.println( "Catalog : " + result.getString(1) + ",Database : " + result.getString(2) + ",Table : " + result .getString(3));
//            }
//            result.close();
            query(connection, sql);
            sql = "select _m_alis_响应时长 from TEMP.CUBE_TABLE  where _d_alis_日期='2022年01月14日' and _d_alis_城市='全部'";
            query(connection, sql);
            /*
            statement = connection.createStatement();
            resultSet = statement.executeQuery(sql);
//            int k = resultSet.getMetaData().getColumnCount();
//            for (int i = 1; i <= k; i++) {
//                String name = resultSet.getMetaData().getColumnLabel(i);
//                QueryResultColumnInfo info = new QueryResultColumnInfo();
//                info.setName(name);
//            }

            List<Object[]> dataList = new LinkedList<>();

            while (resultSet.next()) {
                int n = resultSet.getMetaData().getColumnCount();
                Object[] rows = new Object[n + 1];
                for (int i = 1; i <= n; i++) {

                    String value = resultSet.getString(i);
                    rows[i] = value;
                    System.out.print(value + "  ");

                }

                System.out.println();
                dataList.add(rows);

            }

             */

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static TempTable exec() {

        List<Map<String, Object>> orgDataList = DorisJdbcTest.getDataList();

        TempTable tempTable = new TempTable();
        //虚拟主键
        List<ColumnTypeInfo> columnTypeInfoList = tempTable.getColumnTypeInfoList();
        ColumnTypeInfo columnTypeInfo = ColumnTypeInfo.build("TEMP_ID", "12");

        columnTypeInfoList.add(columnTypeInfo);
        List<Object[]> dataList = tempTable.getDataList();

        String[] columnNames = new String[6];
        String[] columnTypes = new String[6];

        columnNames[0] = "_d_alis_日期ID";
        columnTypes[0] = "12";

        columnNames[1] = "_d_alis_日期";
        columnTypes[1] = "12";

        columnNames[2] = "_d_alis_城市ID";
        columnTypes[2] = "12";

        columnNames[3] = "_d_alis_城市";
        columnTypes[3] = "12";

        columnNames[4] = "_m_alis_60s响应量";
        columnTypes[4] = "-6";

        columnNames[5] = "_m_alis_响应时长";
        columnTypes[5] = "-6";

        for (int idx = 0; idx < columnNames.length; idx++) {

            String name = columnNames[idx];
            String type = String.valueOf(columnTypes[idx]);

            columnTypeInfo = ColumnTypeInfo.build(name.toUpperCase(), type);
            columnTypeInfoList.add(columnTypeInfo);

        }

        Integer columSize = columnNames.length;

        for (Map<String, Object> stringObjectMap : orgDataList) {

            Object[] objs = new Object[columSize + 1];
            objs[0] = UUID.randomUUID().toString();

            Collection vs = stringObjectMap.values();
            Object[] datas = vs.toArray();
            for (int i = 0; i < datas.length; i++) {
                objs[i + 1] = datas[i];
            }
            dataList.add(objs);
        }

        return tempTable;

    }
}

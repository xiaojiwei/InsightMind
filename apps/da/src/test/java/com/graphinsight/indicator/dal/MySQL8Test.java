package com.graphinsight.indicator.dal;

import com.mysql.cj.jdbc.MysqlDataSource;

import java.sql.Connection;

/**
 * Author: lixiaolong
 * Date: 2023/9/19
 * Desc:
 */
public class MySQL8Test {

    public static void main(String[] args) throws Exception{
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL("jdbc:mysql://192.168.58.132:3306/indicator");
        // dataSource.setURL("jdbc:mysql://10.24.105.31:12012/information_schema");
        // dataSource.setURL("jdbc:mysql://localhost:12012/information_schema");
        dataSource.setUser("root");
        dataSource.setPassword("123456");

        Connection connection = dataSource.getConnection();
        // PreparedStatement statement = connection.prepareStatement("select 1");
        // ResultSet resultSet = statement.executeQuery();
        // while (resultSet.next()){
        //     int i = resultSet.getInt(1);
        //     System.out.println(i);
        // }
    }
}

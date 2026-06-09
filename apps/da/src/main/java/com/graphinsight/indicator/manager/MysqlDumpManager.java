package com.graphinsight.indicator.manager;

import lombok.extern.slf4j.Slf4j;
import org.mortbay.log.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;


/**
 * Author: lixiaolong
 * Date: 2022/8/23
 * Desc:
 * 重要提示：
 * 这个类的操作会把整个库表清掉，重新写入数据
 * 因此此类的配置信息任何人不能随便修改
 */
@Slf4j
@Component
public class MysqlDumpManager {

    private static final String url = "jdbc:mysql://192.168.58.132:3306/indicator?allowMultiQueries=true&useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&autoReconnect=true&failOverReadOnly=false&maxReconnects=30&initialTimeout=2&connectTimeout=3000";

    private static final String driverClassName = "com.mysql.cj.jdbc.Driver";

    private static final String userName = "platform";

    private static final String password = "=LBN6abKC2*t";

    @Value("${mysqlDumpSwitch:off}")
    private String mysqlDumpSwitch;

    @Autowired
    RestTemplate restTemplate;

    private static final String ADDR = "https://da-indicator.prod.k8s.chehejia.com/bi/v1/dbsql";
    // private static final String ADDR = "https://da-indicator.ontest.k8s.chehejia.com/bi/v1/dbsql";
    // private static final String URL = "http://localhost:8080/bi/v1/dbsql";

    private static Connection conn = null;
    private static Statement sm = null;

    public void dump() {
        if ("on".equalsIgnoreCase(mysqlDumpSwitch)){
            try {
                ResponseEntity<Resource> entity = restTemplate.getForEntity(ADDR, Resource.class);
                if (entity.getStatusCode().equals(HttpStatus.OK)){
                    InputStream is = entity.getBody().getInputStream();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
                    String sql = bufferedReader.lines().collect(Collectors.joining(""));
                    log.info("sql读取完毕，开始执行 {}",sql);
                    executeSql(sql);
                }
            } catch (Exception e) {
                log.error("备份库异常:",e);
            }
        }
    }

    private void executeSql(String sql) throws SQLException {
        try {
            connectSQL(driverClassName, url, userName, password);//连接数据库
            sm.execute(sql);
        } finally {
            sm.close();
            conn.close();
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

}

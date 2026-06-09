package com.graphinsight.indicator.model;

import lombok.Data;

/**
 * 数据库连接信息，从知识图谱中读取，用于动态创建 JDBC 连接。
 */
@Data
public class DataConnection {

    /** 数据库类型，如 mysql / doris / starrocks */
    private String dbType;

    private String host;

    private int port;

    private String dbUser;

    private String dbPassword;

    /** JDBC URL 中的 database 名称，通常与 Table.schemaName 相同 */
    private String dbName;

    /**
     * 生成 MySQL 兼容的 JDBC URL。
     */
    public String buildJdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?allowMultiQueries=true&useUnicode=true&characterEncoding=utf-8"
                + "&serverTimezone=Asia/Shanghai&autoReconnect=true"
                + "&failOverReadOnly=false&maxReconnects=30&connectTimeout=3000";
    }

    /** 缓存键，同一连接复用同一 JdbcTemplate。 */
    public String cacheKey() {
        return dbType + "://" + dbUser + "@" + host + ":" + port + "/" + dbName;
    }

    public String driverClassName() {
        return "com.mysql.cj.jdbc.Driver";
    }
}

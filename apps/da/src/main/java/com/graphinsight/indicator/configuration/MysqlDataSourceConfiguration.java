package com.graphinsight.indicator.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class MysqlDataSourceConfiguration {

    @Value(value = "${spring.datasource.dynamic.datasource.mysql.url}")
    private String url;

    @Value(value = "${spring.datasource.dynamic.datasource.mysql.driver-class-name}")
    private String driverClassName;

    @Value(value = "${spring.datasource.dynamic.datasource.mysql.username}")
    private String userName;

    @Value(value = "${spring.datasource.dynamic.datasource.mysql.password}")
    private String password;


    @Bean(name = "mysqlJdbcTemplate")
    public JdbcTemplate mysqlJdbcTemplate() {

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        dataSource.setUsername(userName);
        dataSource.setPassword(password);

        return new JdbcTemplate(dataSource);

    }

}
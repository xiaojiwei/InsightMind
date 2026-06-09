package com.graphinsight.indicator;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class DorisJdbcTest {

    private static  final String sql = "select leads_code from eps_dw.dwm_sale_retail_leads_net_leads_df limit 1";
    public static void main(String[] args) {

        DataSource ds = builDataSource();
        JdbcTemplate temp = new JdbcTemplate(ds);

        List<Map<String, Object>> list = temp.queryForList(sql);

        System.out.println(list.size());

    }

    public static List<Map<String, Object>> query(String sql) {
        DataSource ds = builDataSource();
        JdbcTemplate temp = new JdbcTemplate(ds);

        List<Map<String, Object>> list = temp.queryForList(sql);
        return list;
    }

    public static List<Map<String, Object>> getDataList() {
        DataSource ds = builDataSource();
        JdbcTemplate temp = new JdbcTemplate(ds);

        List<Map<String, Object>> list = temp.queryForList(sql);
        return list;
    }

    public static DataSource builDataSource() {
        DruidDataSource datasource = new DruidDataSource();
        datasource.setUrl("jdbc:mysql://dip-dmp-starrocks-eps.chj.cloud:9030/information_schema?allowMultiQueries=true&useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai");
        datasource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        datasource.setUsername("da_indicator_rw");
        datasource.setPassword("p8WmlCCY");
        datasource.setInitialSize(Integer.valueOf(10));
        datasource.setMinIdle(Integer.valueOf(10));
        datasource.setMaxWait(Long.valueOf(50));
        datasource.setMaxActive(Integer.valueOf(10));
        datasource.setMinEvictableIdleTimeMillis(Long.valueOf(10));
        try {
            datasource.setFilters("stat,wall");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return datasource;
    }



}

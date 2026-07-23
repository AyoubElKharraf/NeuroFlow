package com.neuroflow.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Dual DataSource: PostgreSQL (JPA primary) + ClickHouse (analytics JDBC).
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource postgresDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name}") String driverClassName) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("postgres-pool");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setMaximumPoolSize(10);
        return dataSource;
    }

    @Bean
    public DataSource clickHouseDataSource(
            @Value("${neuroflow.clickhouse.url}") String url,
            @Value("${neuroflow.clickhouse.username}") String username,
            @Value("${neuroflow.clickhouse.password}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("clickhouse-pool");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        dataSource.setMaximumPoolSize(8);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTestQuery("SELECT 1");
        return dataSource;
    }

    @Bean
    public JdbcTemplate clickHouseJdbcTemplate(
            @Qualifier("clickHouseDataSource") DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }
}

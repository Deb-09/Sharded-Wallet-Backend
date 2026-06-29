package com.example.rubun.sharded_wallet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;



@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.shard0.jdbc-url}")
    private String shard0Url;

    @Value("${spring.datasource.shard0.username}")
    private String shard0Username;

    @Value("${spring.datasource.shard0.password}")
    private String shard0Password;

    @Value("${spring.datasource.shard1.jdbc-url}")
    private String shard1Url;

    @Value("${spring.datasource.shard1.username}")
    private String shard1Username;

    @Value("${spring.datasource.shard1.password}")
    private String shard1Password;

    @Value("${spring.datasource.shard2.jdbc-url}")
    private String shard2Url;

    @Value("${spring.datasource.shard2.username}")
    private String shard2Username;

    @Value("${spring.datasource.shard2.password}")
    private String shard2Password;

    @Bean
    public DataSource shard0DataSource() {
        return buildDataSource(shard0Url, shard0Username, shard0Password);
    }

    @Bean
    public DataSource shard1DataSource() {
        return buildDataSource(shard1Url, shard1Username, shard1Password);
    }

    @Bean
    public DataSource shard2DataSource() {
        return buildDataSource(shard2Url, shard2Username, shard2Password);
    }

    @Bean
    @Primary
    public DataSource routingDataSource() {
        RoutingDataSource routingDataSource = new RoutingDataSource();

        Map<Object, Object> dataSources = new HashMap<>();
        dataSources.put("shard0", shard0DataSource());
        dataSources.put("shard1", shard1DataSource());
        dataSources.put("shard2", shard2DataSource());

        routingDataSource.setTargetDataSources(dataSources);
        routingDataSource.setDefaultTargetDataSource(shard0DataSource());
        return routingDataSource;
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway() {
        return Flyway.configure()
                .dataSource(shard0DataSource())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    private DataSource buildDataSource(String url, String username, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(5);
        return ds;
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
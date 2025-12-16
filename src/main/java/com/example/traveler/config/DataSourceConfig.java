package com.example.traveler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    private final String CONFIG_PATH = "/config/mapping.json";

    @Bean
    public ShardingRoutingDataSource shardingDataSource() throws IOException {
        ShardingRoutingDataSource routingDataSource = new ShardingRoutingDataSource(CONFIG_PATH);
        routingDataSource.refreshDataSources();
        // Встановлюємо дефолтний, якщо список не пустий
        if (!routingDataSource.getResolvedDataSources().isEmpty()) {
            routingDataSource.setDefaultTargetDataSource(routingDataSource.getResolvedDataSources().values().iterator().next());
        }
        return routingDataSource;
    }

    @Bean
    @Primary
    public DataSource dataSource(ShardingRoutingDataSource shardingDataSource) {
        return shardingDataSource;
    }
}
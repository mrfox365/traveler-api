package com.example.traveler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    public ShardingRoutingDataSource shardingDataSource() {
        ShardingRoutingDataSource routingDataSource = new ShardingRoutingDataSource();
        routingDataSource.refreshDataSources();
        return routingDataSource;
    }

    @Bean
    @Primary
    public DataSource dataSource(ShardingRoutingDataSource shardingDataSource) {
        return shardingDataSource;
    }
}
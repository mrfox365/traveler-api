package com.example.traveler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() throws IOException {
        ShardingRoutingDataSource routingDataSource = new ShardingRoutingDataSource();

        // 1. Читаємо JSON файл
        ObjectMapper mapper = new ObjectMapper();
        // В Docker ми змонтували папку в /config
        Map<String, String> mapping = mapper.readValue(new File("/config/mapping.json"), Map.class);

        Map<Object, Object> targetDataSources = new HashMap<>();

        // 2. Створюємо 16 Connection Pools
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(entry.getValue());
            ds.setUsername("postgres");
            ds.setPassword("09125689");
            ds.setDriverClassName("org.postgresql.Driver");

            // Зменшуємо розмір пулу для кожного шарду.
            ds.setMaximumPoolSize(5);
            ds.setMinimumIdle(2);

            targetDataSources.put(entry.getKey(), ds);
        }

        routingDataSource.setTargetDataSources(targetDataSources);
        // Встановлюємо дефолтний, щоб Spring міг запуститись (наприклад, db_0)
        routingDataSource.setDefaultTargetDataSource(targetDataSources.get("0"));

        return routingDataSource;
    }
}
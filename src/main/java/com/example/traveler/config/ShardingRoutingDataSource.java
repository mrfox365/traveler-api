package com.example.traveler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ShardingRoutingDataSource extends AbstractRoutingDataSource {

    private final String configPath;

    public ShardingRoutingDataSource(String configPath) {
        this.configPath = configPath;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return ShardContext.getShard();
    }

    // Метод для оновлення пулів з'єднань на льоту
    public void refreshDataSources() throws IOException {
        System.out.println("🔄 Refreshing DataSources from " + configPath);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> mapping = mapper.readValue(new File(configPath), Map.class);

        Map<Object, Object> targetDataSources = new HashMap<>();

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(entry.getValue());
            ds.setUsername("postgres");
            ds.setPassword("09125689");
            ds.setDriverClassName("org.postgresql.Driver");
            ds.setMaximumPoolSize(5);
            ds.setMinimumIdle(1);

            targetDataSources.put(entry.getKey(), ds);
        }

        this.setTargetDataSources(targetDataSources);
        this.afterPropertiesSet(); // Важливо! Це змушує Spring застосувати зміни
        System.out.println("✅ DataSources reloaded successfully.");
    }
}
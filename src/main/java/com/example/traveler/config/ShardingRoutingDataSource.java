package com.example.traveler.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class ShardingRoutingDataSource extends AbstractRoutingDataSource {

    // URL до каталогу (завжди на postgres_00)
    private final String CATALOG_URL = "jdbc:postgresql://postgres_00:5432/shard_catalog";
    private final String DB_USER = "postgres";
    private final String DB_PASS = "09125689";

    @Override
    protected Object determineCurrentLookupKey() {
        return ShardContext.getShard();
    }

    public void refreshDataSources() {
        System.out.println("Connecting to Shard Catalog DB...");

        Map<Object, Object> targetDataSources = new HashMap<>();

        // Використовуємо чистий JDBC для отримання конфігурації
        try (Connection conn = DriverManager.getConnection(CATALOG_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT shard_key, jdbc_url FROM shard_mapping")) {

            while (rs.next()) {
                String key = rs.getString("shard_key");
                String url = rs.getString("jdbc_url");

                HikariDataSource ds = new HikariDataSource();
                ds.setJdbcUrl(url);
                ds.setUsername(DB_USER);
                ds.setPassword(DB_PASS);
                ds.setDriverClassName("org.postgresql.Driver");
                ds.setMaximumPoolSize(5);
                ds.setMinimumIdle(1);

                targetDataSources.put(key, ds);
            }
            System.out.println("Loaded " + targetDataSources.size() + " shards from DB.");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load shard mapping from DB", e);
        }

        this.setTargetDataSources(targetDataSources);

        // Встановлюємо дефолтний
        if (!targetDataSources.isEmpty()) {
            this.setDefaultTargetDataSource(targetDataSources.values().iterator().next());
        }

        this.afterPropertiesSet();
    }
}
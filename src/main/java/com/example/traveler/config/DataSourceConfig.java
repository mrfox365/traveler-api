package com.example.traveler.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    // 1. Налаштування для Writer (Майстер)
    @Bean
    @ConfigurationProperties("spring.datasource.writer")
    public DataSourceProperties writerDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource writerDataSource() {
        return writerDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    // 2. Налаштування для Reader (Репліки)
    @Bean
    @ConfigurationProperties("spring.datasource.reader")
    public DataSourceProperties readerDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource readerDataSource() {
        return readerDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    // 3. Маршрутизатор (Router)
    @Bean
    @Primary
    public DataSource dataSource(
            @Qualifier("writerDataSource") DataSource writerDataSource,
            @Qualifier("readerDataSource") DataSource readerDataSource
    ) {
        AbstractRoutingDataSource routingDataSource = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                // Якщо транзакція Read-Only -> йдемо на репліку, інакше -> на майстер
                return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? "read" : "write";
            }
        };

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("write", writerDataSource);
        targetDataSources.put("read", readerDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(writerDataSource);
        return routingDataSource;
    }
}
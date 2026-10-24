package com.tspoon.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    public OutboxEventPublisher outboxEventPublisher(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
        return new OutboxEventPublisher(jdbcTemplate, jsonMapper);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "tspoon.outbox", name = "relay-enabled", matchIfMissing = true)
    static class RelayConfiguration {

        @Bean
        public OutboxRelay outboxRelay(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager,
                                       KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper,
                                       OutboxProperties properties) {
            return new OutboxRelay(jdbcTemplate, new TransactionTemplate(transactionManager), kafkaTemplate, jsonMapper, properties);
        }
    }
}

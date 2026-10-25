package com.tspoon.searchservice.config;

import com.tspoon.event.TspoonTopics;
import com.tspoon.searchservice.index.SearchIndex;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.TopicBuilder;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class SearchConfig {

    // 구독할 토픽을 기동할 때 먼저 만든다 (발행 서비스보다 먼저 떠도 바로 읽도록, #41)
    @Bean
    public NewTopic communityEventsTopic() {
        return TopicBuilder.name(TspoonTopics.COMMUNITY_EVENTS).partitions(TspoonTopics.PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic qnaEventsTopic() {
        return TopicBuilder.name(TspoonTopics.QNA_EVENTS).partitions(TspoonTopics.PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic educationEventsTopic() {
        return TopicBuilder.name(TspoonTopics.EDUCATION_EVENTS).partitions(TspoonTopics.PARTITIONS).replicas(1).build();
    }

    @Bean
    public HealthIndicator opensearchHealthIndicator(SearchIndex index) {
        return () -> {
            String status = index.clusterStatus();
            return "red".equals(status) ? Health.down().withDetail("cluster", status).build()
                    : Health.up().withDetail("cluster", status).build();
        };
    }

    // 기동할 때 색인을 만들어 둔다. OpenSearch 가 아직이면 첫 이벤트·검색 때 다시 시도한다
    @Bean
    public SearchIndexInitializer searchIndexInitializer(SearchIndex index) {
        return new SearchIndexInitializer(index);
    }

    public record SearchIndexInitializer(SearchIndex index) {

        @EventListener(ApplicationReadyEvent.class)
        public void createIndex() {
            try {
                index.createIfMissing();
            } catch (RuntimeException e) {
                log.warn("검색 색인을 아직 만들지 못했습니다 (첫 요청 때 다시 시도): {}", e.getMessage());
            }
        }
    }
}

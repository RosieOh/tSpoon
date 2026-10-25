package com.tspoon.educationservice.event;

import com.tspoon.educationservice.entity.EducationContent;
import com.tspoon.event.EducationEventTypes;
import com.tspoon.event.TspoonTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * education-service 가 발행하는 이벤트. 키는 콘텐츠 ID 라서 같은 콘텐츠의 이벤트는 같은 파티션에 순서대로 들어간다.
 */
@Configuration(proxyBeanMethods = false)
public class EducationEvents {

    public static final String TOPIC = TspoonTopics.EDUCATION_EVENTS;
    public static final String AGGREGATE_CONTENT = "CONTENT";
    public static final String CONTENT_PUBLISHED = EducationEventTypes.CONTENT_PUBLISHED;
    public static final String CONTENT_REMOVED = EducationEventTypes.CONTENT_REMOVED;

    // 로컬·CI 는 브로커가 하나라 복제본 1
    @Bean
    public NewTopic educationEventsTopic() {
        return TopicBuilder.name(TOPIC).partitions(TspoonTopics.PARTITIONS).replicas(1).build();
    }

    /** @param content 검색용 텍스트 — 소개와 본문을 합친다 */
    public record ContentPublished(Long contentId, String title, String content, String category, Long authorId,
                                   String authorName, Instant createdAt) {

        public static ContentPublished of(EducationContent content) {
            String text = Stream.of(content.getDescription(), content.getContent())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n\n"));
            return new ContentPublished(content.getId(), content.getTitle(), text, content.getCategory().name(),
                    content.getAuthorId(), content.getAuthorName(), content.getCreatedAt());
        }
    }

    public record ContentRemoved(Long contentId) {
    }
}

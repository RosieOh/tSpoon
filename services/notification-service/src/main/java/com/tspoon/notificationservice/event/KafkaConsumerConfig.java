package com.tspoon.notificationservice.event;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfig {

    /**
     * 처리에 실패한 메시지는 1초 간격으로 3번 다시 시도하고, 그래도 안 되면 DLT 로 보내 뒤 메시지를 막지 않는다.
     * JSON 이 깨졌거나 필드가 없는 메시지는 다시 해도 소용없으므로 바로 DLT 로 보낸다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        // DLT 파티션은 브로커가 정하게 한다 (원래 토픽과 파티션 수가 달라도 되도록)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3));
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
        return handler;
    }
}

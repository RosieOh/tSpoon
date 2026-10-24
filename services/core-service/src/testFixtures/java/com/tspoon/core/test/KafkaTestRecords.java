package com.tspoon.core.test;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 테스트에서 토픽에 발행된 메시지를 처음부터 읽어 조건에 맞는 것을 찾는다.
 */
public final class KafkaTestRecords {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private KafkaTestRecords() {
    }

    public static ConsumerRecord<String, String> consumeUntil(String bootstrapServers, String topic, Predicate<String> matches) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (matches.test(record.value())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError(TIMEOUT.toSeconds() + "초 안에 " + topic + " 에서 조건에 맞는 메시지를 받지 못했습니다.");
    }
}

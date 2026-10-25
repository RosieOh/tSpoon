package com.tspoon.core.test;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 테스트에서 토픽에 발행된 메시지를 처음부터 읽어 조건에 맞는 것을 찾거나, 다른 서비스가 보낸 것처럼 메시지를 보낸다.
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

    public static void send(String bootstrapServers, String topic, String key, String value) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException(topic + " 으로 보내지 못했습니다.", e);
        }
    }

    /** outbox 릴레이가 보내는 것과 같은 봉투 */
    public static String envelope(String eventType, String aggregateType, Object aggregateId, String payloadJson) {
        return """
                {"eventId": "%s", "eventType": "%s", "occurredAt": "%s", "aggregateType": "%s", "aggregateId": "%s", "payload": %s}
                """.formatted(UUID.randomUUID(), eventType, Instant.now(), aggregateType, aggregateId, payloadJson);
    }
}

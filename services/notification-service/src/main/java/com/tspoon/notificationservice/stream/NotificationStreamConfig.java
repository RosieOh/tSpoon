package com.tspoon.notificationservice.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(NotificationStreamProperties.class)
public class NotificationStreamConfig {

    // 모든 인스턴스가 같은 채널을 구독해, 자기에게 연결된 회원의 알림만 골라 보낸다
    @Bean
    public RedisMessageListenerContainer notificationPushListener(RedisConnectionFactory connectionFactory,
                                                                  NotificationStreams streams, JsonMapper jsonMapper,
                                                                  NotificationStreamProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            try {
                streams.deliver(jsonMapper.readValue(message.getBody(), NotificationPush.class));
            } catch (RuntimeException e) {
                log.warn("실시간 알림 메시지를 처리하지 못했습니다: {}", e.getMessage());
            }
        }, new ChannelTopic(properties.channel()));
        return container;
    }
}

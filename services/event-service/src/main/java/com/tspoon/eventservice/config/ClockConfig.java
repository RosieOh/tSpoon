package com.tspoon.eventservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 행사 일정은 한국 시간(현지 시각)으로 저장하므로, 신청 기간 판단도 서울 시간대 기준으로 한다.
 * (컨테이너는 UTC 로 돈다)
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    public static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(EVENT_ZONE);
    }
}

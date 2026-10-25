package com.tspoon.eventservice;

import com.tspoon.core.test.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 빈 DB 에 Flyway 마이그레이션을 적용한 뒤 Hibernate 스키마 검증(ddl-auto: validate)까지 통과하는지 확인한다.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.kafka.listener.auto-startup=false",
        "tspoon.outbox.relay-enabled=false", "tspoon.event.reminder.enabled=false"})
@Import(TestcontainersConfiguration.class)
class EventServiceApplicationTests {

    @Test
    void 마이그레이션한_스키마로_기동된다() {
    }
}

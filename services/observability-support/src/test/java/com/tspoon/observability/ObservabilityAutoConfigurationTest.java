package com.tspoon.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenTelemetrySdkAutoConfiguration.class, ObservabilityAutoConfiguration.class));

    @AfterEach
    void detachAppender() {
        root().detachAppender(ObservabilityAutoConfiguration.APPENDER_NAME);
    }

    @Test
    void 로그_내보내기_주소가_있으면_루트_로거에_OpenTelemetry_어펜더를_붙인다() {
        runner.withPropertyValues("management.opentelemetry.logging.export.otlp.endpoint=http://localhost:4318/v1/logs")
                .run(context -> assertThat(root().getAppender(ObservabilityAutoConfiguration.APPENDER_NAME))
                        .isInstanceOf(OpenTelemetryAppender.class));
    }

    @Test
    void 주소가_없으면_어펜더를_붙이지_않는다() {
        runner.run(context -> assertThat(root().getAppender(ObservabilityAutoConfiguration.APPENDER_NAME)).isNull());
    }

    private static Logger root() {
        return ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
    }
}

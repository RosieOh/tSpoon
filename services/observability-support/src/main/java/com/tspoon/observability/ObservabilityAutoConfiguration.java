package com.tspoon.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * OTLP 로그 내보내기를 켰을 때(management.opentelemetry.logging.export.otlp.endpoint) Logback 루트 로거에
 * OpenTelemetry 어펜더를 붙인다. 서비스마다 logback-spring.xml 을 두지 않으려고 코드로 연결한다.
 */
@AutoConfiguration(after = OpenTelemetrySdkAutoConfiguration.class)
@ConditionalOnClass({OpenTelemetryAppender.class, LoggerContext.class})
public class ObservabilityAutoConfiguration {

    static final String APPENDER_NAME = "OTEL";

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnProperty("management.opentelemetry.logging.export.otlp.endpoint")
    public InitializingBean openTelemetryLogbackAppender(OpenTelemetry openTelemetry) {
        return () -> {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            if (root.getAppender(APPENDER_NAME) == null) {
                OpenTelemetryAppender appender = new OpenTelemetryAppender();
                appender.setName(APPENDER_NAME);
                appender.setContext(context);
                appender.start();
                root.addAppender(appender);
            }
            OpenTelemetryAppender.install(openTelemetry);
        };
    }
}

package com.tspoon.core.autoconfigure;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * 도메인 서비스 공통 기본 설정(tspoon-defaults.yml)을 가장 낮은 우선순위로 추가한다.
 * 각 서비스의 application.yml 이나 환경변수에 같은 키가 있으면 그 값이 우선한다.
 */
public class TspoonDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String DEFAULTS_LOCATION = "tspoon-defaults.yml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ClassPathResource resource = new ClassPathResource(DEFAULTS_LOCATION);
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("tspoonDefaults", resource);
            sources.forEach(source -> environment.getPropertySources().addLast(source));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + DEFAULTS_LOCATION, e);
        }
    }
}

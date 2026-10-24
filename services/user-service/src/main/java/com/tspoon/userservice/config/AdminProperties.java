package com.tspoon.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * 기동 시 만들 초기 관리자 계정. 환경변수 ADMIN_EMAIL, ADMIN_PASSWORD 가 모두 있을 때만 쓴다.
 */
@ConfigurationProperties("tspoon.admin")
public record AdminProperties(String email, String password, @DefaultValue("관리자") String name) {

    public boolean isConfigured() {
        return StringUtils.hasText(email) && StringUtils.hasText(password);
    }
}

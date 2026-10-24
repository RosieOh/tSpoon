package com.tspoon.core.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 도메인 서비스 공통 OpenAPI 설정. 모든 API 에 JWT Bearer 인증을 기본으로 걸고,
 * 공개 API 는 컨트롤러에서 {@code @SecurityRequirements} 로 비운다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OpenAPI.class)
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI tspoonOpenApi(@Value("${spring.application.name}") String applicationName) {
        return new OpenAPI()
                .info(new Info()
                        .title("티스푼 " + applicationName + " API")
                        .description("API Gateway 를 거쳐 호출합니다. 로그인 응답의 accessToken 을 Authorize 에 넣으세요."))
                // Swagger UI 가 Gateway 에서 열리므로 상대 경로로 두어 'Try it out' 요청이 Gateway 를 거치게 한다
                .servers(List.of(new Server().url("/").description("API Gateway")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}

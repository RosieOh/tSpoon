package com.tspoon.apigateway.support;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gateway 가 직접 거절할 때도 도메인 서비스와 같은 RFC 9457 ProblemDetail 형식으로 응답한다.
 */
public final class ProblemResponses {

    private ProblemResponses() {
    }

    /**
     * @param code 클라이언트가 원인을 구분할 때 쓰는 값. 없으면 null
     */
    public static Mono<Void> write(ServerWebExchange exchange, JsonMapper jsonMapper, HttpStatus status, String detail,
                                   String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", exchange.getRequest().getPath().value());
        if (code != null) {
            body.put("code", code);
        }

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(jsonMapper.writeValueAsBytes(body))));
    }
}

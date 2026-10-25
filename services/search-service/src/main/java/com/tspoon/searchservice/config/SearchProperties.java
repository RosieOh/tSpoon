package com.tspoon.searchservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param opensearchUrl OpenSearch 주소
 * @param index         색인 이름
 * @param analyzer      본문 분석기 — nori(한국어 형태소 분석, 플러그인 필요) 또는 standard
 * @param timeout       OpenSearch 요청 제한 시간
 */
@ConfigurationProperties("tspoon.search")
public record SearchProperties(
        @DefaultValue("http://localhost:9200") String opensearchUrl,
        @DefaultValue("tspoon-search") String index,
        @DefaultValue("nori") String analyzer,
        @DefaultValue("5s") Duration timeout
) {
}

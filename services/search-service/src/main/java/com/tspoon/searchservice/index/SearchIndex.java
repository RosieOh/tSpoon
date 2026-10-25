package com.tspoon.searchservice.index;

import com.tspoon.searchservice.config.SearchProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenSearch 색인. 쓰는 API 가 몇 개뿐이라 공식 클라이언트 대신 REST API 를 RestClient 로 직접 부른다.
 *
 * 색인·삭제는 이벤트 발생 시각을 외부 버전(external_gte)으로 넘겨, 늦게 도착한 옛 이벤트(DLT 재처리 등)가
 * 새 내용을 덮어쓰거나 지운 글을 되살리지 못하게 한다.
 */
@Slf4j
@Component
public class SearchIndex {

    private final RestClient client;
    private final SearchProperties properties;
    private final JsonMapper jsonMapper;
    private volatile boolean ready;

    public SearchIndex(SearchProperties properties, JsonMapper jsonMapper) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.client = RestClient.builder().baseUrl(properties.opensearchUrl()).requestFactory(requestFactory).build();
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    /** 색인이 없으면 만든다. 인스턴스 여러 대가 동시에 만들어도 한쪽만 성공하고 나머지는 이미 있다는 응답을 받는다 */
    public void createIfMissing() {
        if (ready) {
            return;
        }
        try {
            client.head().uri("/{index}", properties.index()).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            try {
                client.put().uri("/{index}", properties.index()).contentType(MediaType.APPLICATION_JSON)
                        .body(indexDefinition()).retrieve().toBodilessEntity();
                log.info("검색 색인 {} 을(를) 만들었습니다 (분석기 {}).", properties.index(), properties.analyzer());
            } catch (HttpClientErrorException.BadRequest alreadyExists) {
                log.debug("다른 인스턴스가 먼저 색인을 만들었습니다: {}", alreadyExists.getMessage());
            }
        }
        ready = true;
    }

    public void put(SearchDocument document, Instant version) {
        createIfMissing();
        try {
            client.put().uri("/{index}/_doc/{id}?version={version}&version_type=external_gte",
                            properties.index(), document.documentId(), version.toEpochMilli())
                    .contentType(MediaType.APPLICATION_JSON).body(document).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            log.debug("{} 에 더 새로운 내용이 이미 있어 건너뜁니다.", document.documentId());
        }
    }

    public void delete(SearchType type, long targetId, Instant version) {
        createIfMissing();
        try {
            client.delete().uri("/{index}/_doc/{id}?version={version}&version_type=external_gte",
                    properties.index(), SearchDocument.documentId(type, targetId), version.toEpochMilli())
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Conflict e) {
            log.debug("{}-{} 은(는) 이미 없거나 더 새로운 내용이 있어 건너뜁니다.", type, targetId);
        }
    }

    /** 회원 이름이 바뀌었거나 탈퇴하면 그 회원이 쓴 모든 문서의 작성자 이름을 바꾼다 */
    public void renameAuthor(long authorId, String authorName) {
        createIfMissing();
        Map<String, Object> body = Map.of(
                "query", Map.of("term", Map.of("authorId", authorId)),
                "script", Map.of("lang", "painless", "source", "ctx._source.authorName = params.name",
                        "params", Map.of("name", authorName)));
        client.post().uri("/{index}/_update_by_query?conflicts=proceed&refresh=true", properties.index())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
    }

    /**
     * 제목(가중치 2)과 본문에서 찾는다. 강조 표시는 내용을 HTML 이스케이프한 뒤 &lt;em&gt; 으로 감싸므로 그대로 화면에 넣어도 안전하다.
     */
    public Page<SearchHit> search(String query, SearchType type, int page, int size) {
        createIfMissing();
        List<Object> filters = type == null ? List.of() : List.of(Map.of("term", Map.of("type", type.name())));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", page * size);
        body.put("size", size);
        body.put("track_total_hits", true);
        body.put("query", Map.of("bool", Map.of(
                "must", List.of(Map.of("multi_match", Map.of("query", query, "fields", List.of("title^2", "content")))),
                "filter", filters)));
        body.put("highlight", Map.of(
                "encoder", "html",
                "pre_tags", List.of("<em>"),
                "post_tags", List.of("</em>"),
                "fields", Map.of(
                        "title", Map.of("number_of_fragments", 0),
                        "content", Map.of("fragment_size", 120, "number_of_fragments", 1))));

        JsonNode response = client.post().uri("/{index}/_search", properties.index())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        List<SearchHit> hits = new ArrayList<>();
        for (JsonNode hit : response.path("hits").path("hits")) {
            hits.add(SearchHit.of(jsonMapper.treeToValue(hit.required("_source"), SearchDocument.class), hit.path("highlight")));
        }
        long total = response.path("hits").path("total").path("value").asLong();
        return new PageImpl<>(hits, PageRequest.of(page, size), total);
    }

    /** 가볍게 살아 있는지 — health 표시용 */
    public String clusterStatus() {
        return client.get().uri("/_cluster/health").retrieve().body(JsonNode.class).path("status").asString();
    }

    private Map<String, Object> indexDefinition() {
        Map<String, Object> text = Map.of("type", "text", "analyzer", "korean");
        Map<String, Object> analyzer = "nori".equals(properties.analyzer())
                ? Map.of("type", "custom", "tokenizer", "korean_tokenizer", "filter", List.of("nori_part_of_speech", "lowercase"))
                : Map.of("type", "standard");
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("analyzer", Map.of("korean", analyzer));
        if ("nori".equals(properties.analyzer())) {
            // 복합어는 원형과 분리형을 함께 남겨(mixed) "공부법" 으로도 "공부" 로도 찾을 수 있게 한다
            analysis.put("tokenizer", Map.of("korean_tokenizer", Map.of("type", "nori_tokenizer", "decompound_mode", "mixed")));
        }
        return Map.of(
                "settings", Map.of("analysis", analysis),
                "mappings", Map.of("properties", Map.of(
                        "type", Map.of("type", "keyword"),
                        "targetId", Map.of("type", "long"),
                        "title", text,
                        "content", text,
                        "category", Map.of("type", "keyword"),
                        "authorId", Map.of("type", "long"),
                        "authorName", Map.of("type", "keyword"),
                        "createdAt", Map.of("type", "date"),
                        "url", Map.of("type", "keyword", "index", false))));
    }
}

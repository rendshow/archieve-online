package com.danganguan.archive.search.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.page.entity.ArchiveDocumentPage;
import com.danganguan.archive.document.page.mapper.ArchiveDocumentPageMapper;
import com.danganguan.archive.search.config.OpenSearchProperties;
import com.danganguan.archive.search.dto.ArchivePageSearchHit;
import com.danganguan.archive.search.service.ArchivePageSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "archive.search.opensearch", name = "enabled", havingValue = "true")
public class OpenSearchArchivePageSearchService implements ArchivePageSearchService {
    private static final String MAPPING = """
            {"settings":{"analysis":{"analyzer":{"archive_cjk":{"type":"cjk"}}}},"mappings":{"properties":{
            "documentId":{"type":"long"},"hallId":{"type":"long"},"title":{"type":"text","analyzer":"archive_cjk","fields":{"keyword":{"type":"keyword"}}},
            "folderPath":{"type":"keyword"},"pageNo":{"type":"integer"},"ocrText":{"type":"text","analyzer":"archive_cjk"},"updatedAt":{"type":"date"}
            }}}""";

    private final OpenSearchProperties properties;
    private final ArchiveDocumentPageMapper pageMapper;
    private final ObjectMapper objectMapper;
    private volatile boolean initialized;

    @Override
    public synchronized void syncDocument(ArchiveDocument document) {
        if (document == null || document.getId() == null) {
            return;
        }
        ensureIndex();
        List<ArchiveDocumentPage> pages = pageMapper.selectList(new LambdaQueryWrapper<ArchiveDocumentPage>()
                .eq(ArchiveDocumentPage::getArchiveDocumentId, document.getId())
                .orderByAsc(ArchiveDocumentPage::getPageNo));
        for (ArchiveDocumentPage page : pages) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("documentId", document.getId());
            source.put("hallId", document.getHallId());
            source.put("title", document.getTitle());
            source.put("folderPath", document.getFolderPath());
            source.put("pageNo", page.getPageNo());
            source.put("ocrText", page.getOcrText());
            source.put("updatedAt", document.getUpdatedAt() == null ? null : document.getUpdatedAt().toString());
            put("/" + writeIndex() + "/_doc/" + document.getId() + "-" + page.getPageNo(), json(source));
        }
    }

    @Override
    public void deleteDocument(Long documentId) {
        if (documentId == null) {
            return;
        }
        ensureIndex();
        post("/" + properties.getIndexAlias() + "/_delete_by_query", json(Map.of("query", Map.of("term", Map.of("documentId", documentId)))));
    }

    @Override
    public List<ArchivePageSearchHit> search(AgentResolvedScope scope, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        ensureIndex();
        List<Map<String, Object>> filters = new ArrayList<>();
        if (scope != null && scope.hallId() != null) {
            filters.add(Map.of("term", Map.of("hallId", scope.hallId())));
        }
        if (scope != null && scope.scopeType() == AgentScopeType.DOCUMENT && scope.documentId() != null) {
            filters.add(Map.of("term", Map.of("documentId", scope.documentId())));
        } else if (scope != null && scope.scopeType() == AgentScopeType.FOLDER && scope.folderPath() != null && !scope.folderPath().isBlank()) {
            filters.add(Map.of("prefix", Map.of("folderPath", scope.folderPath())));
        }
        Map<String, Object> body = Map.of(
                "size", Math.max(1, Math.min(limit, 100)),
                "query", Map.of("bool", Map.of(
                        "must", List.of(Map.of("multi_match", Map.of("query", query, "fields", List.of("ocrText^3", "title^5"), "operator", "and"))),
                        "filter", filters
                )),
                "highlight", Map.of("fields", Map.of("ocrText", Map.of("fragment_size", 160, "number_of_fragments", 1)))
        );
        JsonNode hits = post("/" + properties.getIndexAlias() + "/_search", json(body)).path("hits").path("hits");
        List<ArchivePageSearchHit> result = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            String text = source.path("ocrText").asText("");
            JsonNode fragments = hit.path("highlight").path("ocrText");
            if (fragments.isArray() && !fragments.isEmpty()) {
                text = fragments.get(0).asText(text);
            }
            result.add(new ArchivePageSearchHit(source.path("documentId").asLong(), source.path("hallId").asLong(),
                    source.path("title").asText(), source.path("folderPath").asText(), source.path("pageNo").asInt(),
                    text, hit.path("_score").asDouble()));
        }
        return result;
    }

    private void ensureIndex() {
        if (initialized) {
            return;
        }
        String index = writeIndex();
        HttpResponse<String> response = request("PUT", "/" + index, MAPPING, false);
        if (response.statusCode() != 200 && response.statusCode() != 201 && response.statusCode() != 400) {
            throw failure("创建 OpenSearch 索引失败", response);
        }
        post("/_aliases", json(Map.of("actions", List.of(Map.of("add", Map.of("index", index, "alias", properties.getIndexAlias()))))));
        initialized = true;
    }

    private String writeIndex() {
        String alias = properties.getIndexAlias();
        return alias.endsWith("-read") ? alias.substring(0, alias.length() - 5) + "-v1" : alias + "-v1";
    }

    private JsonNode put(String path, String body) {
        return parse(request("PUT", path, body, true));
    }

    private JsonNode post(String path, String body) {
        return parse(request("POST", path, body, true));
    }

    private HttpResponse<String> request(String method, String path, String body, boolean requireSuccess) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint(path))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                    .build().send(request, HttpResponse.BodyHandlers.ofString());
            if (requireSuccess && (response.statusCode() < 200 || response.statusCode() >= 300)) {
                throw failure("OpenSearch 请求失败", response);
            }
            return response;
        } catch (IOException ex) {
            throw new BizException("连接 OpenSearch 失败：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException("OpenSearch 请求被中断");
        }
    }

    private URI endpoint(String path) {
        String base = properties.getEndpoint().replaceAll("/+$", "");
        return URI.create(base + path);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException("生成 OpenSearch 请求失败", ex);
        }
    }

    private JsonNode parse(HttpResponse<String> response) {
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw new BizException("解析 OpenSearch 响应失败");
        }
    }

    private BizException failure(String message, HttpResponse<String> response) {
        return new BizException(message + "，HTTP " + response.statusCode());
    }
}

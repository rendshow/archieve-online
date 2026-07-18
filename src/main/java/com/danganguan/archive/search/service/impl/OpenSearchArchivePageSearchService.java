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
            {"mappings":{"properties":{
            "documentId":{"type":"long"},"hallId":{"type":"long"},"title":{"type":"text","analyzer":"cjk","fields":{"keyword":{"type":"keyword"}}},
            "folderPath":{"type":"keyword"},"pageNo":{"type":"integer"},"ocrText":{"type":"text","analyzer":"cjk"},"updatedAt":{"type":"date"}
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
        deleteByDocumentId(document.getId());
        List<ArchiveDocumentPage> pages = pageMapper.selectList(new LambdaQueryWrapper<ArchiveDocumentPage>()
                .eq(ArchiveDocumentPage::getArchiveDocumentId, document.getId())
                .orderByAsc(ArchiveDocumentPage::getPageNo));
        StringBuilder bulk = new StringBuilder();
        for (ArchiveDocumentPage page : pages) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("documentId", document.getId());
            source.put("hallId", document.getHallId());
            source.put("title", document.getTitle());
            source.put("folderPath", document.getFolderPath());
            source.put("pageNo", page.getPageNo());
            source.put("ocrText", page.getOcrText());
            source.put("updatedAt", document.getUpdatedAt() == null ? null : document.getUpdatedAt().toString());
            bulk.append(json(Map.of("index", Map.of("_index", writeIndex(), "_id", document.getId() + "-" + page.getPageNo())))).append('\n');
            bulk.append(json(source)).append('\n');
        }
        if (!bulk.isEmpty()) {
            JsonNode response = requestJson("POST", "/_bulk", bulk.toString(), "application/x-ndjson");
            if (response.path("errors").asBoolean(false)) {
                throw new BizException("OpenSearch 批量写入存在失败页");
            }
        }
    }

    @Override
    public void deleteDocument(Long documentId) {
        if (documentId == null) {
            return;
        }
        ensureIndex();
        deleteByDocumentId(documentId);
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
                        "must", List.of(Map.of("multi_match", Map.of("query", query, "fields", List.of("ocrText^3", "title^5"), "minimum_should_match", "60%"))),
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
        HttpResponse<String> exists = request("HEAD", "/" + index, "", false, "application/json");
        if (exists.statusCode() == 404) {
            request("PUT", "/" + index, MAPPING, true, "application/json");
        } else if (exists.statusCode() != 200) {
            throw failure("检查 OpenSearch 索引失败", exists);
        }
        post("/_aliases", json(Map.of("actions", List.of(Map.of("add", Map.of("index", index, "alias", properties.getIndexAlias()))))));
        initialized = true;
    }

    private String writeIndex() {
        String alias = properties.getIndexAlias();
        return alias.endsWith("-read") ? alias.substring(0, alias.length() - 5) + "-v1" : alias + "-v1";
    }

    private JsonNode post(String path, String body) {
        return requestJson("POST", path, body, "application/json");
    }

    private void deleteByDocumentId(Long documentId) {
        post("/" + writeIndex() + "/_delete_by_query", json(Map.of("query", Map.of("term", Map.of("documentId", documentId)))));
    }

    private JsonNode requestJson(String method, String path, String body, String contentType) {
        return parse(request(method, path, body, true, contentType));
    }

    private HttpResponse<String> request(String method, String path, String body, boolean requireSuccess, String contentType) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint(path))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                    .header("Content-Type", contentType)
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

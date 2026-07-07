package com.danganguan.archive.ai.naming.impl;

import com.danganguan.archive.ai.dto.AiNamingRequest;
import com.danganguan.archive.ai.dto.AiNamingResult;
import com.danganguan.archive.ai.naming.DocumentNamingCandidate;
import com.danganguan.archive.ai.naming.DocumentNamingCandidateExtractor;
import com.danganguan.archive.ai.service.AiNamingService;
import com.danganguan.archive.common.config.AiProviderProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "archive.naming", name = "provider", havingValue = "openai-compatible")
public class OpenAiCompatibleAiNamingService implements AiNamingService {
    private static final int REASON_LIMIT = 1000;

    private final AiProviderProperties properties;
    private final DocumentNamingCandidateExtractor candidateExtractor;
    private final RuleBasedNamingEngine fallbackNamingEngine;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Override
    public AiNamingResult name(AiNamingRequest request) {
        AiNamingResult fallback = fallbackNamingEngine.name(request);
        AiProviderProperties.OpenAiCompatible config = properties.getOpenaiCompatible();
        if (!validConfig(config)) {
            return fallbackWithReason(fallback, "AI 命名未配置完整，已使用规则兜底。");
        }
        DocumentNamingCandidate candidate = candidateExtractor.extract(request);
        try {
            String content = callModel(config, request, candidate, fallback);
            AiNamingResult result = parseResult(content, fallback);
            return new AiNamingResult(
                    safeName(firstNonBlank(result.suggestedName(), fallback.suggestedName())),
                    firstNonBlank(result.folderName(), fallback.folderName()),
                    firstNonBlank(result.summary(), fallback.summary()),
                    firstNonBlank(result.reason(), "AI 根据候选字段和命名示例生成最终名称。")
            );
        } catch (Exception ex) {
            return fallbackWithReason(fallback, "AI 命名失败，已使用规则兜底：" + limit(ex.getMessage(), REASON_LIMIT));
        }
    }

    private String callModel(AiProviderProperties.OpenAiCompatible config,
                             AiNamingRequest request,
                             DocumentNamingCandidate candidate,
                             AiNamingResult fallback) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(config.getTimeoutSeconds() * 1000);
        requestFactory.setReadTimeout(config.getTimeoutSeconds() * 1000);
        RestClient restClient = restClientBuilder.requestFactory(requestFactory).build();
        Map<String, Object> payload = Map.of(
                "model", config.getModel(),
                "temperature", 0.1,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", userPrompt(request, candidate, fallback))
                )
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(chatCompletionsUrl(config.getBaseUrl()))
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                .body(payload)
                .retrieve()
                .body(Map.class);
        return extractMessageContent(response);
    }

    private String systemPrompt() {
        return """
                你是档案馆文件命名助手。你的任务不是重新分析全文，而是根据候选字段、用户命名示例和兜底名称决定最终文件名。
                必须只返回 JSON 对象，不要 Markdown，不要解释。
                JSON 字段：
                suggestedName: string，最终文件名，不带扩展名；
                folderName: string，建议文件夹名；
                summary: string，80字以内摘要；
                reason: string，说明采用哪些候选字段和示例。
                规则：
                1. 用户示例里的“学号”“姓名”是占位符，应替换为候选学号和候选姓名。
                2. 如果候选字段缺失，不要编造；可使用“待识别学号”或“待识别姓名”。
                3. 优先贴合用户给出的文件命名示例，其次参考兜底名称。
                4. 文件名不得包含 / \\ : * ? " < > | 或空白字符。
                """;
    }

    private String userPrompt(AiNamingRequest request, DocumentNamingCandidate candidate, AiNamingResult fallback) {
        return """
                任务名：%s
                文件命名示例：%s
                文件夹命名示例：%s
                命名序号：%s
                原始文件名：%s
                候选姓名：%s
                候选学号：%s
                候选材料类型：%s
                候选关键词：%s
                少量OCR文本：%s
                规则兜底文件名：%s
                规则兜底文件夹名：%s
                """.formatted(
                blankToNone(request.task().getTaskName()),
                blankToNone(request.task().getFileNameExample()),
                blankToNone(request.task().getFolderNameExample()),
                request.sequenceNo(),
                blankToNone(candidate.originalName()),
                blankToNone(candidate.personName()),
                blankToNone(candidate.studentNo()),
                blankToNone(candidate.materialType()),
                candidate.keywords().isEmpty() ? "无" : String.join("、", candidate.keywords()),
                blankToNone(candidate.textSnippet()),
                fallback.suggestedName(),
                fallback.folderName()
        );
    }

    private AiNamingResult parseResult(String content, AiNamingResult fallback) throws JsonProcessingException {
        Map<String, Object> json = objectMapper.readValue(cleanJson(content), new TypeReference<>() {});
        return new AiNamingResult(
                firstNonBlank(stringValue(json.get("suggestedName")), fallback.suggestedName()),
                firstNonBlank(stringValue(json.get("folderName")), fallback.folderName()),
                firstNonBlank(stringValue(json.get("summary")), fallback.summary()),
                firstNonBlank(stringValue(json.get("reason")), fallback.reason())
        );
    }

    private String extractMessageContent(Map<String, Object> response) {
        Object choicesValue = response == null ? null : response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("外部 AI 响应缺少 choices");
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new IllegalStateException("外部 AI 响应格式异常");
        }
        Object messageValue = choiceMap.get("message");
        if (!(messageValue instanceof Map<?, ?> messageMap)) {
            throw new IllegalStateException("外部 AI 响应缺少 message");
        }
        Object contentValue = messageMap.get("content");
        if (contentValue == null) {
            throw new IllegalStateException("外部 AI 响应缺少 content");
        }
        return contentValue.toString();
    }

    private String chatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private boolean validConfig(AiProviderProperties.OpenAiCompatible config) {
        return config != null
                && config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                && config.getApiKey() != null && !config.getApiKey().isBlank()
                && config.getModel() != null && !config.getModel().isBlank();
    }

    private AiNamingResult fallbackWithReason(AiNamingResult fallback, String reason) {
        return new AiNamingResult(
                fallback.suggestedName(),
                fallback.folderName(),
                fallback.summary(),
                fallback.reason() + " " + reason
        );
    }

    private String cleanJson(String content) {
        String cleaned = content == null ? "" : content.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            return cleaned.substring(start, end + 1);
        }
        return "{}";
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }

    private String safeName(String name) {
        String safe = name == null || name.isBlank() ? "档案" : name;
        return safe.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }
}

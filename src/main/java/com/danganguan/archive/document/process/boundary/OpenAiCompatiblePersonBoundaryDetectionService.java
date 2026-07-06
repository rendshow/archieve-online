package com.danganguan.archive.document.process.boundary;

import com.danganguan.archive.ai.ocr.dto.OcrResult;
import com.danganguan.archive.ai.ocr.service.OcrService;
import com.danganguan.archive.common.config.BoundaryDetectionProperties;
import com.danganguan.archive.common.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OpenAiCompatiblePersonBoundaryDetectionService {
    private static final int TEXT_LIMIT_PER_PAGE = 1200;

    private final BoundaryDetectionProperties properties;
    private final OcrService ocrService;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public List<BoundaryGroup> detectByModel(List<BoundaryImage> images) {
        if (images == null || images.isEmpty()) {
            throw new BizException("AI 边界拆分时没有可分析的图片");
        }
        BoundaryDetectionProperties.OpenAiCompatible config = properties.getOpenaiCompatible();
        validateConfig(config);
        String content = callModel(config, images, collectPageTexts(images));
        List<BoundaryGroup> groups = parseGroups(content, images.size());
        validateGroups(groups, images.size());
        return groups;
    }

    private List<PageText> collectPageTexts(List<BoundaryImage> images) {
        List<PageText> pageTexts = new ArrayList<>();
        for (BoundaryImage image : images) {
            OcrResult result = ocrService.recognize(image.imagePath());
            pageTexts.add(new PageText(image.index(), image.order(), image.entryName(), limit(result.text(), TEXT_LIMIT_PER_PAGE)));
        }
        return pageTexts;
    }

    private String callModel(BoundaryDetectionProperties.OpenAiCompatible config, List<BoundaryImage> images, List<PageText> pageTexts) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(config.getTimeoutSeconds() * 1000);
        requestFactory.setReadTimeout(config.getTimeoutSeconds() * 1000);
        RestClient restClient = restClientBuilder.requestFactory(requestFactory).build();
        Map<String, Object> payload = Map.of(
                "model", config.getModel(),
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt(images.size())),
                        Map.of("role", "user", "content", buildUserPrompt(pageTexts))
                )
        );
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(chatCompletionsUrl(config.getBaseUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            return extractMessageContent(response);
        } catch (Exception ex) {
            throw new BizException("外部 AI 边界识别失败：" + ex.getMessage());
        }
    }

    private String systemPrompt(int pageCount) {
        return """
                你是档案馆图片序列的人员边界识别助手。输入是同一个压缩包内按顺序排列的 OCR 文本。
                目标：判断哪些连续页面属于同一个人的完整材料。
                只返回 JSON，不要 Markdown，不要解释。
                必须满足：
                1. 图片序号从 0 到 %d，每张图片必须且只能出现一次；
                2. 每组必须连续且按顺序递增；
                3. 不允许空组；
                4. 不确定时宁愿少切分，不要把同一个人的续页切开。
                返回格式：
                {"groups":[{"indexes":[0,1],"reason":"识别依据"}]}
                """.formatted(pageCount - 1);
    }

    private String buildUserPrompt(List<PageText> pageTexts) {
        List<Map<String, Object>> pages = new ArrayList<>();
        for (PageText pageText : pageTexts) {
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("index", pageText.index());
            page.put("order", pageText.order());
            page.put("entryName", pageText.entryName());
            page.put("ocrText", pageText.text());
            pages.add(page);
        }
        try {
            return "请根据以下页面 OCR 文本识别人员边界：\n" + objectMapper.writeValueAsString(pages);
        } catch (JsonProcessingException ex) {
            throw new BizException("构造 AI 边界识别请求失败：" + ex.getMessage());
        }
    }

    private List<BoundaryGroup> parseGroups(String content, int imageCount) {
        if (content == null || content.isBlank()) {
            throw new BizException("外部 AI 边界识别返回空内容");
        }
        try {
            Map<String, Object> root = objectMapper.readValue(cleanJson(content), new TypeReference<>() {});
            Object groupsValue = root.get("groups");
            if (!(groupsValue instanceof List<?> rawGroups)) {
                throw new BizException("外部 AI 边界识别返回 JSON 缺少 groups");
            }
            List<BoundaryGroup> groups = new ArrayList<>();
            for (Object rawGroup : rawGroups) {
                if (!(rawGroup instanceof Map<?, ?> groupMap)) {
                    continue;
                }
                List<Integer> indexes = intList(groupMap.get("indexes"));
                String reason = groupMap.get("reason") == null ? "外部 AI 边界识别" : groupMap.get("reason").toString();
                if (!indexes.isEmpty()) {
                    groups.add(new BoundaryGroup(indexes, reason));
                }
            }
            return groups;
        } catch (JsonProcessingException ex) {
            throw new BizException("外部 AI 边界识别返回内容不是合法 JSON：" + ex.getMessage());
        }
    }

    private void validateGroups(List<BoundaryGroup> groups, int imageCount) {
        if (groups == null || groups.isEmpty()) {
            throw new BizException("外部 AI 边界识别未返回有效分组");
        }
        Set<Integer> seen = new HashSet<>();
        int expected = 0;
        for (BoundaryGroup group : groups) {
            if (group.imageIndexes() == null || group.imageIndexes().isEmpty()) {
                throw new BizException("外部 AI 边界识别返回了空分组");
            }
            for (Integer index : group.imageIndexes()) {
                if (index == null || index < 0 || index >= imageCount) {
                    throw new BizException("外部 AI 边界识别返回非法图片序号：" + index);
                }
                if (index != expected) {
                    throw new BizException("外部 AI 边界识别返回的图片序号不连续，期望 " + expected + "，实际 " + index);
                }
                if (!seen.add(index)) {
                    throw new BizException("外部 AI 边界识别重复使用图片序号：" + index);
                }
                expected++;
            }
        }
        if (seen.size() != imageCount) {
            throw new BizException("外部 AI 边界识别未覆盖全部图片");
        }
    }

    private List<Integer> intList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof Number number) {
                result.add(number.intValue());
            } else if (item != null) {
                try {
                    result.add(Integer.parseInt(item.toString()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    private String extractMessageContent(Map<String, Object> response) {
        Object choicesValue = response == null ? null : response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            throw new BizException("外部 AI 响应缺少 choices");
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new BizException("外部 AI 响应格式异常");
        }
        Object messageValue = choiceMap.get("message");
        if (!(messageValue instanceof Map<?, ?> messageMap)) {
            throw new BizException("外部 AI 响应缺少 message");
        }
        Object contentValue = messageMap.get("content");
        if (contentValue == null) {
            throw new BizException("外部 AI 响应缺少 content");
        }
        return contentValue.toString();
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
        return "";
    }

    private String chatCompletionsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BizException("未配置外部 AI 边界识别接口地址，请设置 ARCHIVE_BOUNDARY_AI_BASE_URL");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private void validateConfig(BoundaryDetectionProperties.OpenAiCompatible config) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new BizException("未配置外部 AI 边界识别 API Key，请设置 ARCHIVE_BOUNDARY_AI_API_KEY");
        }
        if (config.getModel() == null || config.getModel().isBlank()) {
            throw new BizException("未配置外部 AI 边界识别模型，请设置 ARCHIVE_BOUNDARY_AI_MODEL");
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }

    private record PageText(int index, int order, String entryName, String text) {
    }
}

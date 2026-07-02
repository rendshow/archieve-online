package com.danganguan.archive.ai.analysis.service.impl;

import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeRequest;
import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.danganguan.archive.ai.analysis.service.DocumentAnalyzeService;
import com.danganguan.archive.common.config.AiProviderProperties;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.file.storage.FileStorageService;
import com.danganguan.archive.task.enums.OutputFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "archive.ai", name = "provider", havingValue = "openai-compatible")
public class OpenAiCompatibleDocumentAnalyzeServiceImpl implements DocumentAnalyzeService {
    private static final int TEXT_LIMIT = 8000;
    private static final int PDF_VISUAL_PAGE_LIMIT = 3;

    private final AiProviderProperties properties;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public OpenAiCompatibleDocumentAnalyzeServiceImpl(AiProviderProperties properties,
                                                      FileStorageService fileStorageService,
                                                      ObjectMapper objectMapper,
                                                      RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public DocumentAnalyzeResult analyze(DocumentAnalyzeRequest request) {
        AiProviderProperties.OpenAiCompatible config = properties.getOpenaiCompatible();
        validateConfig(config);
        String extractedText = extractPdfTextIfPossible(request);
        String content = callModel(config, request, extractedText);
        return parseResult(content, extractedText);
    }

    private void validateConfig(AiProviderProperties.OpenAiCompatible config) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new BizException("未配置外部 AI API Key，请设置 ARCHIVE_AI_API_KEY");
        }
        if (config.getModel() == null || config.getModel().isBlank()) {
            throw new BizException("未配置外部 AI 模型，请设置 ARCHIVE_AI_MODEL");
        }
    }

    private String callModel(AiProviderProperties.OpenAiCompatible config,
                             DocumentAnalyzeRequest request,
                             String extractedText) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(config.getTimeoutSeconds() * 1000);
        requestFactory.setReadTimeout(config.getTimeoutSeconds() * 1000);
        RestClient restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
        Map<String, Object> payload = Map.of(
                "model", config.getModel(),
                "temperature", 0.1,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", buildUserContent(request, extractedText))
                )
        );
        try {
            String requestUrl = chatCompletionsUrl(config.getBaseUrl());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(requestUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            return extractMessageContent(response);
        } catch (Exception ex) {
            throw new BizException("外部 AI 文档分析失败：" + ex.getMessage() + "。请检查 base-url 是否为兼容 Chat Completions 的地址。");
        }
    }

    private String chatCompletionsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BizException("未配置外部 AI 接口地址，请设置 ARCHIVE_AI_BASE_URL");
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

    private List<Map<String, Object>> buildUserContent(DocumentAnalyzeRequest request, String extractedText) {
        List<Map<String, Object>> content = new ArrayList<>();
        String text = """
                请分析这份档案材料，并只返回 JSON。
                原始文件名：%s
                输出文件路径：%s
                任务文件命名示例：%s
                任务文件夹命名示例：%s
                已提取 PDF 文本：%s
                """.formatted(
                request.sourceFile().getOriginalName(),
                request.processedFile().storagePath(),
                blankToNone(request.task().getFileNameExample()),
                blankToNone(request.task().getFolderNameExample()),
                limit(extractedText, TEXT_LIMIT)
        );
        content.add(Map.of("type", "text", "text", text));
        if (request.processedFile().outputFormat() == OutputFormat.PNG) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", toImageDataUrl(request.processedFile().storagePath()))
            ));
        } else if (request.processedFile().outputFormat() == OutputFormat.PDF && (extractedText == null || extractedText.isBlank())) {
            for (String imageDataUrl : renderPdfPagesAsImageDataUrls(request.processedFile().storagePath())) {
                content.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", imageDataUrl)
                ));
            }
        }
        return content;
    }

    private String systemPrompt() {
        return """
                你是档案馆文档分析助手。请从档案图片或文本中提取用于命名和检索的信息。
                只返回一个 JSON 对象，不要 Markdown，不要解释。
                JSON 字段：
                extractedText: string，尽量还原可读正文；
                summary: string，80 字以内摘要；
                detectedPersonName: string|null，识别到的人名；
                keywords: string[]，适合做标签的关键词；
                confidence: number，0 到 1；
                reason: string，简短说明依据。
                """;
    }

    private String extractPdfTextIfPossible(DocumentAnalyzeRequest request) {
        if (request.processedFile().outputFormat() != OutputFormat.PDF) {
            return "";
        }
        Path pdfPath = fileStorageService.resolve(request.processedFile().storagePath());
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            String text = new PDFTextStripper().getText(document);
            return text == null ? "" : text.trim();
        } catch (IOException ex) {
            return "";
        }
    }

    private String toImageDataUrl(String storagePath) {
        Path imagePath = fileStorageService.resolve(storagePath);
        try {
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
            return "data:image/png;base64," + base64;
        } catch (IOException ex) {
            throw new BizException("读取待识别图片失败：" + ex.getMessage());
        }
    }

    private List<String> renderPdfPagesAsImageDataUrls(String storagePath) {
        Path pdfPath = fileStorageService.resolve(storagePath);
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), PDF_VISUAL_PAGE_LIMIT);
            List<String> imageDataUrls = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 150, ImageType.RGB);
                imageDataUrls.add(toPngDataUrl(image));
            }
            return imageDataUrls;
        } catch (IOException ex) {
            throw new BizException("渲染待识别 PDF 页面失败：" + ex.getMessage());
        }
    }

    private String toPngDataUrl(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException ex) {
            throw new BizException("生成待识别图片失败：" + ex.getMessage());
        }
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

    private DocumentAnalyzeResult parseResult(String content, String fallbackText) {
        try {
            Map<String, Object> result = objectMapper.readValue(cleanJson(content), new TypeReference<>() {});
            String extractedText = stringValue(result.get("extractedText"));
            String summary = stringValue(result.get("summary"));
            String detectedPersonName = stringValue(result.get("detectedPersonName"));
            List<String> keywords = stringList(result.get("keywords"));
            BigDecimal confidence = decimalValue(result.get("confidence"));
            String reason = stringValue(result.get("reason"));
            return new DocumentAnalyzeResult(
                    firstNonBlank(extractedText, fallbackText),
                    firstNonBlank(summary, "外部 AI 已完成文档分析。"),
                    detectedPersonName,
                    keywords,
                    confidence == null ? new BigDecimal("0.70") : confidence,
                    firstNonBlank(reason, "外部 AI 根据文档内容生成分析结果。")
            );
        } catch (JsonProcessingException ex) {
            throw new BizException("外部 AI 返回内容不是合法 JSON：" + ex.getMessage());
        }
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
        return cleaned;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            String text = stringValue(item);
            if (text != null && !result.contains(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }
}

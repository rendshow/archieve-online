package com.danganguan.archive.document.process.impl;

import com.danganguan.archive.common.config.ImageEnhanceProperties;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.process.ImageEnhanceService;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "archive.image-enhance", name = "provider", havingValue = "quark-api")
public class QuarkApiImageEnhanceServiceImpl implements ImageEnhanceService {
    private static final List<String> BASE64_FIELD_NAMES = List.of(
            "imageBase64", "image_base64", "base64", "resultImage", "result_image"
    );
    private static final List<String> URL_FIELD_NAMES = List.of(
            "imageUrl", "image_url", "url", "resultUrl", "result_url"
    );

    private final ImageEnhanceProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Path enhance(ArchiveTask task, Path imagePath) {
        ImageEnhanceProperties.QuarkApi config = properties.getQuarkApi();
        if (config.getEndpoint() == null || config.getEndpoint().isBlank()) {
            throw new BizException("夸克图像增强 API 未配置 endpoint");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new BizException("夸克图像增强 API 未配置 api-key");
        }

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory(config.getTimeoutSeconds()))
                .build();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("scene", config.getScene());
        body.add("file", new FileSystemResource(imagePath));
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        body.set("file", new HttpEntity<>(new FileSystemResource(imagePath), fileHeaders));

        byte[] response = restClient.post()
                .uri(URI.create(config.getEndpoint()))
                .header(config.getApiKeyHeader(), config.getApiKeyPrefix() + config.getApiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(byte[].class);
        if (response == null || response.length == 0) {
            throw new BizException("夸克图像增强 API 返回空内容");
        }
        return parseResponse(imagePath, response);
    }

    private SimpleClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(timeoutSeconds, 1));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    private Path parseResponse(Path source, byte[] response) {
        if (looksLikeImage(response)) {
            return writeBytes(source, response, imageExt(response));
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            String base64 = findText(root, BASE64_FIELD_NAMES);
            if (base64 != null && !base64.isBlank()) {
                return writeBytes(source, Base64.getDecoder().decode(stripDataUrlPrefix(base64)), "jpg");
            }
            String url = findText(root, URL_FIELD_NAMES);
            if (url != null && !url.isBlank()) {
                throw new BizException("夸克图像增强 API 返回了图片 URL，当前后端暂未启用 URL 下载：" + url);
            }
            throw new BizException("夸克图像增强 API 返回 JSON 中未找到图片字段");
        } catch (IOException ex) {
            throw new BizException("夸克图像增强 API 返回内容无法解析：" + ex.getMessage());
        }
    }

    private Path writeBytes(Path source, byte[] bytes, String ext) {
        Path target = source.resolveSibling(source.getFileName() + ".quark." + ext);
        try {
            Files.write(target, bytes);
            return target;
        } catch (IOException ex) {
            throw new BizException("保存夸克图像增强结果失败：" + ex.getMessage());
        }
    }

    private boolean looksLikeImage(byte[] bytes) {
        return bytes.length > 4 && ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                || bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47);
    }

    private String imageExt(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 ? "png" : "jpg";
    }

    private String findText(JsonNode node, List<String> names) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            for (String name : names) {
                JsonNode value = node.get(name);
                if (value != null && value.isTextual()) {
                    return value.asText();
                }
            }
            Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                String found = findText(values.next(), names);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findText(child, names);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String stripDataUrlPrefix(String value) {
        int comma = value.indexOf(',');
        return value.startsWith("data:") && comma >= 0 ? value.substring(comma + 1) : value;
    }
}

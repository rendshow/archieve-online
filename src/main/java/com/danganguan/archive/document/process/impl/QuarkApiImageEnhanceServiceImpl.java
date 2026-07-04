package com.danganguan.archive.document.process.impl;

import com.danganguan.archive.common.config.ImageEnhanceProperties;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.process.ImageEnhanceService;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "archive.image-enhance", name = "provider", havingValue = "quark-api")
public class QuarkApiImageEnhanceServiceImpl implements ImageEnhanceService {
    private static final String BUSINESS = "vision";
    private static final String SIGN_METHOD = "SHA3-256";
    private static final String SUCCESS_CODE = "00000";

    private final ImageEnhanceProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Path enhance(ArchiveTask task, Path imagePath) {
        ImageEnhanceProperties.QuarkApi config = properties.getQuarkApi();
        if (config.getEndpoint() == null || config.getEndpoint().isBlank()) {
            throw new BizException("夸克图像增强 API 未配置 endpoint");
        }
        if (config.getClientId() == null || config.getClientId().isBlank()) {
            throw new BizException("夸克图像增强 API 未配置 client-id");
        }
        if (config.getClientSecret() == null || config.getClientSecret().isBlank()) {
            throw new BizException("夸克图像增强 API 未配置 client-secret");
        }

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory(config.getTimeoutSeconds()))
                .build();

        String response = restClient.post()
                .uri(URI.create(config.getEndpoint()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildRequestBody(config, imagePath))
                .retrieve()
                .body(String.class);
        if (response == null || response.isBlank()) {
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

    private Map<String, Object> buildRequestBody(ImageEnhanceProperties.QuarkApi config, Path imagePath) {
        try {
            String nonce = UUID.randomUUID().toString().replace("-", "");
            long timestamp = System.currentTimeMillis();
            Map<String, String> inputConfigs = new LinkedHashMap<>();
            inputConfigs.put("function_option", config.getFunctionOption());
            inputConfigs.put("auto_crop", config.getAutoCrop());
            inputConfigs.put("auto_rotate", config.getAutoRotate());

            Map<String, String> outputConfigs = new LinkedHashMap<>();
            outputConfigs.put("need_return_image", config.isNeedReturnImage() ? "True" : "False");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("dataBase64", Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath)));
            body.put("dataType", "image");
            body.put("serviceOption", "scan");
            body.put("inputConfigs", objectMapper.writeValueAsString(inputConfigs));
            body.put("outputConfigs", objectMapper.writeValueAsString(outputConfigs));
            body.put("reqId", UUID.randomUUID().toString().replace("-", ""));
            body.put("clientId", config.getClientId());
            body.put("signMethod", SIGN_METHOD);
            body.put("signNonce", nonce);
            body.put("timestamp", String.valueOf(timestamp));
            body.put("signature", signature(config.getClientId(), config.getClientSecret(), nonce, timestamp));
            return body;
        } catch (IOException ex) {
            throw new BizException("读取待增强图片失败：" + ex.getMessage());
        }
    }

    private Path parseResponse(Path source, String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String code = root.path("code").asText("");
            JsonNode imageInfo = root.path("data").path("ImageInfo");
            if (imageInfo.isMissingNode() || imageInfo.isNull()) {
                imageInfo = root.path("ImageInfo");
            }
            if (!code.isBlank() && !SUCCESS_CODE.equals(code)) {
                String message = firstText(root, "message", "msg", "errorMsg", "errorMessage");
                throw new BizException("夸克图像增强 API 调用失败：" + code + (message == null ? "" : " " + message));
            }
            if (!imageInfo.isArray() || imageInfo.isEmpty()) {
                throw new BizException("夸克图像增强 API 返回 JSON 中未找到 ImageInfo");
            }
            String base64 = imageInfo.get(0).path("ImageBase64").asText("");
            if (base64.isBlank()) {
                base64 = imageInfo.get(0).path("ImageBese64").asText("");
            }
            if (base64.isBlank()) {
                throw new BizException("夸克图像增强 API 返回 JSON 中未找到 ImageBase64");
            }
            byte[] imageBytes = Base64.getDecoder().decode(stripDataUrlPrefix(base64));
            return writeBytes(source, imageBytes, imageExt(imageBytes));
        } catch (JsonProcessingException ex) {
            throw new BizException("夸克图像增强 API 返回内容无法解析：" + ex.getMessage());
        } catch (IOException ex) {
            throw new BizException("保存夸克图像增强结果失败：" + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new BizException("夸克图像增强 API 返回图片不是合法 Base64：" + ex.getMessage());
        }
    }

    private Path writeBytes(Path source, byte[] bytes, String ext) throws IOException {
        Path target = source.resolveSibling(source.getFileName() + ".quark." + ext);
        Files.write(target, bytes);
        return target;
    }

    private String imageExt(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 ? "png" : "jpg";
    }

    private String firstText(JsonNode node, String... names) {
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
                String found = firstText(values.next(), names);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = firstText(child, names);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String signature(String clientId, String clientSecret, String nonce, long timestamp) {
        String raw = clientId + "_" + BUSINESS + "_" + SIGN_METHOD + "_" + nonce + "_" + timestamp + "_" + clientSecret;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA3-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException("当前 JDK 不支持 SHA3-256 签名算法");
        }
    }

    private String stripDataUrlPrefix(String value) {
        int comma = value.indexOf(',');
        return value.startsWith("data:") && comma >= 0 ? value.substring(comma + 1) : value;
    }
}

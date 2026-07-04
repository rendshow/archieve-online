package com.danganguan.archive.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "archive.image-enhance")
public class ImageEnhanceProperties {
    private String provider = "noop";
    private QuarkApi quarkApi = new QuarkApi();

    @Getter
    @Setter
    public static class QuarkApi {
        private String endpoint = "";
        private String apiKey = "";
        private String apiKeyHeader = "Authorization";
        private String apiKeyPrefix = "Bearer ";
        private String scene = "scan";
        private int timeoutSeconds = 60;
    }
}

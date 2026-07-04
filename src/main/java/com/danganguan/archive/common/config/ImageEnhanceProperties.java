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
        private String endpoint = "https://scan-business.quark.cn/vision";
        private String clientId = "";
        private String clientSecret = "";
        private String functionOption = "auto_select";
        private String autoCrop = "true";
        private String autoRotate = "true";
        private boolean needReturnImage = true;
        private int timeoutSeconds = 60;
    }
}

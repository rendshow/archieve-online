package com.danganguan.archive.common.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.ocr")
@Getter
@Setter
public class OcrProperties {
    private String provider = "none";
    private int timeoutSeconds = 30;
    private Rapidocr rapidocr = new Rapidocr();




    @Getter
    @Setter
    public static class Rapidocr {
        private String command = "python";
        private String script = "scripts/ocr/rapidocr_ocr.py";


    }
}

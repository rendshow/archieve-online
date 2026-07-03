package com.danganguan.archive.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.ocr")
public class OcrProperties {
    private String provider = "none";
    private int timeoutSeconds = 30;
    private Rapidocr rapidocr = new Rapidocr();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Rapidocr getRapidocr() {
        return rapidocr;
    }

    public void setRapidocr(Rapidocr rapidocr) {
        this.rapidocr = rapidocr;
    }

    public static class Rapidocr {
        private String command = "python";
        private String script = "scripts/ocr/rapidocr_ocr.py";

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public String getScript() {
            return script;
        }

        public void setScript(String script) {
            this.script = script;
        }
    }
}

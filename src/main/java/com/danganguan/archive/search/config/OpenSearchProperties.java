package com.danganguan.archive.search.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.search.opensearch")
@Getter
@Setter
public class OpenSearchProperties {
    private boolean enabled;
    private String endpoint = "http://127.0.0.1:9200";
    private String indexAlias = "archive-page-read";
    private int connectTimeoutSeconds = 5;
    private int requestTimeoutSeconds = 15;

    public boolean isEnabled() { return enabled; }





}

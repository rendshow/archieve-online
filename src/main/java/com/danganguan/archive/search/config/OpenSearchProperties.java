package com.danganguan.archive.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.search.opensearch")
public class OpenSearchProperties {
    private boolean enabled;
    private String endpoint = "http://127.0.0.1:9200";
    private String indexAlias = "archive-page-read";
    private int connectTimeoutSeconds = 5;
    private int requestTimeoutSeconds = 15;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getIndexAlias() { return indexAlias; }
    public void setIndexAlias(String indexAlias) { this.indexAlias = indexAlias; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }
}

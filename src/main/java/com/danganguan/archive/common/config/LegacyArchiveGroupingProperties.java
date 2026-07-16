package com.danganguan.archive.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.legacy-grouping")
public class LegacyArchiveGroupingProperties {
    private String root = "";

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }
}

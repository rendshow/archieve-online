package com.danganguan.archive.common.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.legacy-grouping")
@Getter
@Setter
public class LegacyArchiveGroupingProperties {
    private String root = "";

}

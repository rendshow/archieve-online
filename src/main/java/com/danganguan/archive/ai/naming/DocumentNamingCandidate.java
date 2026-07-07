package com.danganguan.archive.ai.naming;

import java.util.List;

public record DocumentNamingCandidate(
        String personName,
        String studentNo,
        String materialType,
        List<String> keywords,
        String originalName,
        String textSnippet
) {
    public List<String> keywords() {
        return keywords == null ? List.of() : keywords;
    }
}

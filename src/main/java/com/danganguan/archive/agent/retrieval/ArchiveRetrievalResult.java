package com.danganguan.archive.agent.retrieval;

import java.util.List;

public record ArchiveRetrievalResult(
        List<String> keywords,
        List<String> materialKeywords,
        List<ArchiveRetrievalHit> hits
) {
    public boolean requiresMaterialEvidence() {
        return !materialKeywords.isEmpty();
    }

    public boolean hasMaterialContentEvidence() {
        if (!requiresMaterialEvidence()) {
            return false;
        }
        return hits.stream()
                .anyMatch(hit -> hit.hasContentEvidence()
                        && hit.matchedKeywords().stream().anyMatch(materialKeywords::contains));
    }
}

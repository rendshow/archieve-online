package com.danganguan.archive.agent.retrieval;

import com.danganguan.archive.document.entity.ArchiveDocument;

import java.util.List;

public record ArchiveRetrievalHit(
        ArchiveDocument document,
        int score,
        EvidenceLevel evidenceLevel,
        List<String> matchedKeywords,
        List<String> matchedFields,
        String snippet
) {
    public enum EvidenceLevel {
        CONTENT,
        METADATA
    }

    public boolean hasContentEvidence() {
        return evidenceLevel == EvidenceLevel.CONTENT;
    }
}

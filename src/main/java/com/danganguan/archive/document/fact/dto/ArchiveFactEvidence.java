package com.danganguan.archive.document.fact.dto;

import com.danganguan.archive.document.fact.enums.ArchiveFactType;

import java.math.BigDecimal;

public record ArchiveFactEvidence(
        Long archiveDocumentId,
        String archiveTitle,
        String folderPath,
        Integer pageNo,
        ArchiveFactType factType,
        String factKey,
        String factValue,
        String normalizedValue,
        BigDecimal confidence,
        String evidenceText
) {
}

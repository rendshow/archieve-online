package com.danganguan.archive.document.indexing.dto;

public record ArchiveDocumentTextIndexResult(
        int scannedCount,
        int indexedCount,
        int skippedCount,
        int failedCount
) {
}

package com.danganguan.archive.document.importing.dto;

import com.danganguan.archive.document.entity.ArchiveDocument;

import java.util.List;

public record FinishedArchiveImportResult(
        Long hallId,
        int importedCount,
        int skippedCount,
        List<String> skippedFiles,
        List<ArchiveDocument> documents
) {
}

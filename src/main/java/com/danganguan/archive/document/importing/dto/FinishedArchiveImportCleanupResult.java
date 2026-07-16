package com.danganguan.archive.document.importing.dto;

public record FinishedArchiveImportCleanupResult(
        int deletedDocumentCount,
        int deletedObjectCount,
        int deletedTagCount,
        int deletedImportJobCount
) {
}

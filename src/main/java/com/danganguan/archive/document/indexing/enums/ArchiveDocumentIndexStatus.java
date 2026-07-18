package com.danganguan.archive.document.indexing.enums;

public enum ArchiveDocumentIndexStatus {
    QUEUED,
    OCR_RUNNING,
    EXTRACTING,
    SEARCH_SYNCING,
    READY,
    PARTIAL,
    FAILED,
    STALE
}

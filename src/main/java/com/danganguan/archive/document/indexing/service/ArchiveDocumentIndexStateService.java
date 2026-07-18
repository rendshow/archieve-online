package com.danganguan.archive.document.indexing.service;

import com.danganguan.archive.document.indexing.enums.ArchiveDocumentIndexStatus;

public interface ArchiveDocumentIndexStateService {
    void mark(Long documentId, ArchiveDocumentIndexStatus status, String errorMessage);
}

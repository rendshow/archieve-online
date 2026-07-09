package com.danganguan.archive.document.indexing.service;

import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.indexing.dto.ArchiveDocumentTextIndexResult;

public interface ArchiveDocumentTextIndexService {
    ArchiveDocument indexOne(Long documentId);

    ArchiveDocumentTextIndexResult indexMissing(Long hallId, int limit);
}

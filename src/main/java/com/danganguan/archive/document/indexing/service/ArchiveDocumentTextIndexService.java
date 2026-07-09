package com.danganguan.archive.document.indexing.service;

import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.indexing.dto.CreateArchiveTextIndexJobRequest;
import com.danganguan.archive.document.indexing.dto.ArchiveDocumentTextIndexResult;
import com.danganguan.archive.document.indexing.entity.ArchiveTextIndexJob;

public interface ArchiveDocumentTextIndexService {
    ArchiveDocument indexOne(Long documentId);

    ArchiveDocumentTextIndexResult indexMissing(Long hallId, int limit);

    ArchiveTextIndexJob createJob(CreateArchiveTextIndexJobRequest request);

    ArchiveTextIndexJob getJob(Long jobId);

    void processJobBatch(Long jobId);
}

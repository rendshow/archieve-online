package com.danganguan.archive.document.page.service;

import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.page.dto.ArchiveDocumentPageIndexResult;

public interface ArchiveDocumentPageIndexService {
    ArchiveDocumentPageIndexResult rebuild(ArchiveDocument document);
}

package com.danganguan.archive.document.fact.service;

import com.danganguan.archive.document.fact.dto.ArchiveFactEvidence;
import com.danganguan.archive.document.fact.dto.ArchiveFactSearchRequest;

import java.util.List;

public interface ArchiveDocumentFactQueryService {
    List<ArchiveFactEvidence> listByDocumentId(Long archiveDocumentId);

    List<ArchiveFactEvidence> search(ArchiveFactSearchRequest request);
}

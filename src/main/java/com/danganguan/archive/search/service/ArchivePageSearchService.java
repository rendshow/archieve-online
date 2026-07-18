package com.danganguan.archive.search.service;

import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.search.dto.ArchivePageSearchHit;

import java.util.List;

public interface ArchivePageSearchService {
    void syncDocument(ArchiveDocument document);

    void deleteDocument(Long documentId);

    List<ArchivePageSearchHit> search(AgentResolvedScope scope, String query, int limit);
}

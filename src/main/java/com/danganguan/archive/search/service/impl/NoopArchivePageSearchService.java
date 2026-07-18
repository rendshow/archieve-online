package com.danganguan.archive.search.service.impl;

import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.search.dto.ArchivePageSearchHit;
import com.danganguan.archive.search.service.ArchivePageSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "archive.search.opensearch", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopArchivePageSearchService implements ArchivePageSearchService {
    @Override
    public void syncDocument(ArchiveDocument document) {
        // OpenSearch is optional during local development.
    }

    @Override
    public void deleteDocument(Long documentId) {
        // OpenSearch is optional during local development.
    }

    @Override
    public List<ArchivePageSearchHit> search(AgentResolvedScope scope, String query, int limit) {
        return List.of();
    }
}

package com.danganguan.archive.document.fact.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.fact.dto.ArchiveFactEvidence;
import com.danganguan.archive.document.fact.dto.ArchiveFactSearchRequest;
import com.danganguan.archive.document.fact.entity.ArchiveExtractedFact;
import com.danganguan.archive.document.fact.mapper.ArchiveExtractedFactMapper;
import com.danganguan.archive.document.fact.service.ArchiveDocumentFactQueryService;
import com.danganguan.archive.document.page.entity.ArchiveDocumentPage;
import com.danganguan.archive.document.page.mapper.ArchiveDocumentPageMapper;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArchiveDocumentFactQueryServiceImpl implements ArchiveDocumentFactQueryService {
    private static final int MAX_SCOPE_DOCUMENTS = 500;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ArchiveDocumentService archiveDocumentService;
    private final ArchiveExtractedFactMapper archiveExtractedFactMapper;
    private final ArchiveDocumentPageMapper archiveDocumentPageMapper;

    @Override
    public List<ArchiveFactEvidence> listByDocumentId(Long archiveDocumentId) {
        ArchiveDocument document = archiveDocumentService.getById(archiveDocumentId);
        if (document == null || document.getStatus() != ArchiveDocumentStatus.ACTIVE) {
            return List.of();
        }
        List<ArchiveExtractedFact> facts = archiveExtractedFactMapper.selectList(
                new LambdaQueryWrapper<ArchiveExtractedFact>()
                        .eq(ArchiveExtractedFact::getArchiveDocumentId, archiveDocumentId)
                        .orderByAsc(ArchiveExtractedFact::getArchiveDocumentPageId)
                        .orderByDesc(ArchiveExtractedFact::getConfidence)
        );
        return toEvidence(facts, Map.of(document.getId(), document));
    }

    @Override
    public List<ArchiveFactEvidence> search(ArchiveFactSearchRequest request) {
        ArchiveFactSearchRequest effectiveRequest = request == null ? new ArchiveFactSearchRequest() : request;
        List<ArchiveDocument> documents = scopedDocuments(effectiveRequest);
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<Long, ArchiveDocument> documentsById = documents.stream()
                .collect(Collectors.toMap(ArchiveDocument::getId, Function.identity()));
        LambdaQueryWrapper<ArchiveExtractedFact> wrapper = new LambdaQueryWrapper<ArchiveExtractedFact>()
                .in(ArchiveExtractedFact::getArchiveDocumentId, documentsById.keySet())
                .orderByDesc(ArchiveExtractedFact::getConfidence)
                .orderByDesc(ArchiveExtractedFact::getUpdatedAt);
        if (effectiveRequest.getFactType() != null) {
            wrapper.eq(ArchiveExtractedFact::getFactType, effectiveRequest.getFactType());
        }
        if (hasText(effectiveRequest.getValue())) {
            String value = effectiveRequest.getValue().trim();
            wrapper.and(inner -> inner.like(ArchiveExtractedFact::getNormalizedValue, value)
                    .or().like(ArchiveExtractedFact::getFactValue, value)
                    .or().like(ArchiveExtractedFact::getEvidenceText, value));
        }
        return toEvidence(archiveExtractedFactMapper.selectList(wrapper), documentsById).stream()
                .limit(normalizeLimit(effectiveRequest.getLimit()))
                .toList();
    }

    private List<ArchiveDocument> scopedDocuments(ArchiveFactSearchRequest request) {
        LambdaQueryWrapper<ArchiveDocument> wrapper = new LambdaQueryWrapper<ArchiveDocument>()
                .eq(ArchiveDocument::getStatus, ArchiveDocumentStatus.ACTIVE)
                .orderByDesc(ArchiveDocument::getArchivedAt)
                .last("LIMIT " + MAX_SCOPE_DOCUMENTS);
        if (request.getHallId() != null) {
            wrapper.eq(ArchiveDocument::getHallId, request.getHallId());
        }
        if (hasText(request.getFolderPath())) {
            String folderPath = request.getFolderPath().trim();
            wrapper.and(inner -> inner.eq(ArchiveDocument::getFolderPath, folderPath)
                    .or().likeRight(ArchiveDocument::getFolderPath, folderPath + "/"));
        }
        return archiveDocumentService.list(wrapper);
    }

    private List<ArchiveFactEvidence> toEvidence(Collection<ArchiveExtractedFact> facts,
                                                  Map<Long, ArchiveDocument> documentsById) {
        if (facts.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> pageNoById = archiveDocumentPageMapper.selectBatchIds(
                        facts.stream().map(ArchiveExtractedFact::getArchiveDocumentPageId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ArchiveDocumentPage::getId, ArchiveDocumentPage::getPageNo));
        return facts.stream()
                .filter(fact -> documentsById.containsKey(fact.getArchiveDocumentId()))
                .map(fact -> toEvidence(fact, documentsById.get(fact.getArchiveDocumentId()), pageNoById))
                .sorted(Comparator.comparing(ArchiveFactEvidence::archiveTitle)
                        .thenComparing(ArchiveFactEvidence::pageNo)
                        .thenComparing(ArchiveFactEvidence::factType))
                .toList();
    }

    private ArchiveFactEvidence toEvidence(ArchiveExtractedFact fact, ArchiveDocument document,
                                           Map<Long, Integer> pageNoById) {
        return new ArchiveFactEvidence(document.getId(), document.getTitle(), document.getFolderPath(),
                pageNoById.get(fact.getArchiveDocumentPageId()), fact.getFactType(), fact.getFactKey(),
                fact.getFactValue(), fact.getNormalizedValue(), fact.getConfidence(), fact.getEvidenceText());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

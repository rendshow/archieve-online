package com.danganguan.archive.agent.v2.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.agent.v2.tool.ArchiveLocateTool;
import com.danganguan.archive.agent.v2.search.ArchiveQueryTerms;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.fact.dto.ArchiveFactEvidence;
import com.danganguan.archive.document.fact.dto.ArchiveFactSearchRequest;
import com.danganguan.archive.document.fact.enums.ArchiveFactType;
import com.danganguan.archive.document.fact.service.ArchiveDocumentFactQueryService;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import com.danganguan.archive.task.enums.OutputFormat;
import com.danganguan.archive.search.dto.ArchivePageSearchHit;
import com.danganguan.archive.search.service.ArchivePageSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ArchiveLocateToolImpl implements ArchiveLocateTool {
    private static final int LIMIT = 20;
    private static final int RRF_K = 60;

    private final ArchiveDocumentFactQueryService archiveDocumentFactQueryService;
    private final ArchiveDocumentService archiveDocumentService;
    private final ArchivePageSearchService archivePageSearchService;

    @Override
    public LocateResult locate(String message, AgentResolvedScope scope) {
        ArchiveQueryTerms terms = ArchiveQueryTerms.parse(message);
        if (!terms.hasLocateClue() && !(scope.scopeType() == AgentScopeType.DOCUMENT && scope.documentId() != null)) {
            return new LocateResult("没有识别到可用于定位的姓名、学号、档号或材料类型。请补充具体线索；“这份档案”需要在档案详情页中使用，或在同一会话中保留上轮定位结果。", List.of(), List.of());
        }
        List<ArchiveFactEvidence> evidence = locateByFacts(terms, scope);
        List<AgentDocumentReference> factDocuments = documentsFromEvidence(evidence);
        List<AgentDocumentReference> metadataDocuments = locateByMetadata(terms, scope);
        List<AgentDocumentReference> pageDocuments = locateByPageText(terms, scope);
        List<AgentDocumentReference> documents = fuseRankedDocuments(factDocuments, metadataDocuments, pageDocuments);
        if (documents.isEmpty()) {
            return new LocateResult("当前范围内没有找到匹配的正式档案。已使用姓名、学号、材料类型、档号和题名线索检索。", List.of(), evidence);
        }
        String answer = "找到 %d 份匹配档案：%s".formatted(documents.size(), documents.stream()
                .limit(8)
                .map(AgentDocumentReference::title)
                .reduce((left, right) -> left + "；" + right)
                .orElse(""));
        return new LocateResult(answer, documents, evidence);
    }

    private List<AgentDocumentReference> locateByPageText(ArchiveQueryTerms terms, AgentResolvedScope scope) {
        Map<Long, AgentDocumentReference> documents = new LinkedHashMap<>();
        try {
            for (String query : terms.pageQueries()) {
                for (ArchivePageSearchHit hit : archivePageSearchService.search(scope, query, LIMIT)) {
                    documents.putIfAbsent(hit.documentId(), new AgentDocumentReference(hit.documentId(), hit.hallId(), hit.title(),
                            hit.folderPath(), null));
                }
            }
        } catch (RuntimeException ignored) {
            // Metadata and fact retrieval remain usable when the optional page index is unavailable.
        }
        return List.copyOf(documents.values());
    }

    private List<ArchiveFactEvidence> locateByFacts(ArchiveQueryTerms terms, AgentResolvedScope scope) {
        String studentId = terms.studentId();
        if (studentId != null) {
            return factSearch(scope, ArchiveFactType.STUDENT_ID, studentId);
        }
        String personName = terms.personName();
        if (personName != null) {
            List<ArchiveFactEvidence> personEvidence = factSearch(scope, ArchiveFactType.PERSON_NAME, personName);
            String material = terms.materialType();
            if (material == null) {
                return personEvidence;
            }
            return personEvidence.stream()
                    .map(ArchiveFactEvidence::archiveDocumentId)
                    .distinct()
                    .filter(documentId -> documentHasMaterial(documentId, material))
                    .flatMap(documentId -> archiveDocumentFactQueryService.listByDocumentId(documentId).stream())
                    .filter(fact -> (fact.factType() == ArchiveFactType.PERSON_NAME && personName.equals(fact.normalizedValue()))
                            || (fact.factType() == ArchiveFactType.MATERIAL_TYPE && material.equals(fact.normalizedValue())))
                    .toList();
        }
        String material = terms.materialType();
        return material == null ? List.of() : factSearch(scope, ArchiveFactType.MATERIAL_TYPE, material);
    }

    private boolean documentHasMaterial(Long documentId, String materialValue) {
        return archiveDocumentFactQueryService.listByDocumentId(documentId).stream()
                .anyMatch(fact -> fact.factType() == ArchiveFactType.MATERIAL_TYPE
                        && materialValue.equals(fact.normalizedValue()));
    }

    private List<ArchiveFactEvidence> factSearch(AgentResolvedScope scope, ArchiveFactType type, String value) {
        if (scope.scopeType() == AgentScopeType.DOCUMENT && scope.documentId() != null) {
            return archiveDocumentFactQueryService.listByDocumentId(scope.documentId()).stream()
                    .filter(fact -> fact.factType() == type && (value == null || fact.normalizedValue().contains(value)))
                    .toList();
        }
        ArchiveFactSearchRequest request = new ArchiveFactSearchRequest();
        request.setHallId(scope.hallId());
        request.setFolderPath(scope.scopeType() == AgentScopeType.FOLDER ? scope.folderPath() : null);
        request.setFactType(type);
        request.setValue(value);
        request.setLimit(LIMIT);
        return archiveDocumentFactQueryService.search(request);
    }

    private List<AgentDocumentReference> locateByMetadata(ArchiveQueryTerms terms, AgentResolvedScope scope) {
        String archiveNo = terms.archiveNo();
        String name = terms.personName();
        if (archiveNo == null && name == null) {
            return List.of();
        }
        LambdaQueryWrapper<ArchiveDocument> wrapper = scopedDocuments(scope);
        wrapper.and(inner -> {
            if (archiveNo != null) {
                inner.like(ArchiveDocument::getArchiveNo, archiveNo).or().like(ArchiveDocument::getTitle, archiveNo);
            }
            if (name != null) {
                if (archiveNo != null) {
                    inner.or();
                }
                inner.like(ArchiveDocument::getTitle, name);
            }
        });
        return archiveDocumentService.list(wrapper.last("LIMIT " + LIMIT)).stream()
                .map(this::toReference)
                .toList();
    }

    private LambdaQueryWrapper<ArchiveDocument> scopedDocuments(AgentResolvedScope scope) {
        LambdaQueryWrapper<ArchiveDocument> wrapper = new LambdaQueryWrapper<ArchiveDocument>()
                .eq(ArchiveDocument::getStatus, ArchiveDocumentStatus.ACTIVE)
                .orderByAsc(ArchiveDocument::getTitle);
        if (scope.hallId() != null) {
            wrapper.eq(ArchiveDocument::getHallId, scope.hallId());
        }
        if (scope.scopeType() == AgentScopeType.DOCUMENT && scope.documentId() != null) {
            wrapper.eq(ArchiveDocument::getId, scope.documentId());
        } else if (scope.scopeType() == AgentScopeType.FOLDER && scope.folderPath() != null && !scope.folderPath().isBlank()) {
            wrapper.and(inner -> inner.eq(ArchiveDocument::getFolderPath, scope.folderPath())
                    .or().likeRight(ArchiveDocument::getFolderPath, scope.folderPath() + "/"));
        }
        return wrapper;
    }

    private List<AgentDocumentReference> documentsFromEvidence(List<ArchiveFactEvidence> evidence) {
        Map<Long, ArchiveFactEvidence> unique = new LinkedHashMap<>();
        evidence.forEach(item -> unique.putIfAbsent(item.archiveDocumentId(), item));
        if (unique.isEmpty()) {
            return List.of();
        }
        Map<Long, ArchiveDocument> documentsById = archiveDocumentService.listByIds(unique.keySet()).stream()
                .collect(java.util.stream.Collectors.toMap(ArchiveDocument::getId, document -> document));
        return unique.keySet().stream()
                .map(documentsById::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ArchiveDocument::getTitle))
                .map(this::toReference)
                .toList();
    }

    private AgentDocumentReference toReference(ArchiveDocument document) {
        OutputFormat format = document.getFileFormat();
        return new AgentDocumentReference(document.getId(), document.getHallId(), document.getTitle(),
                document.getFolderPath(), format == null ? null : format.name());
    }

    @SafeVarargs
    private final List<AgentDocumentReference> fuseRankedDocuments(List<AgentDocumentReference>... sources) {
        Map<Long, RankedDocument> scores = new LinkedHashMap<>();
        for (List<AgentDocumentReference> source : sources) {
            for (int rank = 0; rank < source.size(); rank++) {
                AgentDocumentReference document = source.get(rank);
                double reciprocalRankScore = 1.0 / (RRF_K + rank + 1);
                scores.compute(document.documentId(), (ignored, current) -> current == null
                        ? new RankedDocument(document, reciprocalRankScore)
                        : current.add(reciprocalRankScore));
            }
        }
        return scores.values().stream()
                .sorted(Comparator.comparingDouble(RankedDocument::score).reversed()
                        .thenComparing(item -> item.document().title()))
                .limit(LIMIT)
                .map(RankedDocument::document)
                .toList();
    }

    private record RankedDocument(AgentDocumentReference document, double score) {
        private RankedDocument add(double value) { return new RankedDocument(document, score + value); }
    }
}

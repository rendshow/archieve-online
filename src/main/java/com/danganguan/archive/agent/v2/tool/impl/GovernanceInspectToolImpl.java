package com.danganguan.archive.agent.v2.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.agent.v2.dto.AgentGovernanceFinding;
import com.danganguan.archive.agent.v2.tool.GovernanceInspectTool;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.fact.dto.ArchiveFactEvidence;
import com.danganguan.archive.document.fact.entity.ArchiveExtractedFact;
import com.danganguan.archive.document.fact.enums.ArchiveFactType;
import com.danganguan.archive.document.fact.mapper.ArchiveExtractedFactMapper;
import com.danganguan.archive.document.page.entity.ArchiveDocumentPage;
import com.danganguan.archive.document.page.mapper.ArchiveDocumentPageMapper;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import com.danganguan.archive.task.enums.OutputFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GovernanceInspectToolImpl implements GovernanceInspectTool {
    private static final int MAX_DOCUMENTS = 5_000;
    private static final Pattern TITLE_PERSON_NAME = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})$");

    private final ArchiveDocumentService archiveDocumentService;
    private final ArchiveExtractedFactMapper archiveExtractedFactMapper;
    private final ArchiveDocumentPageMapper archiveDocumentPageMapper;

    @Override
    public InspectResult inspect(AgentResolvedScope scope) {
        long total = archiveDocumentService.count(scopeWrapper(scope));
        List<ArchiveDocument> documents = archiveDocumentService.list(scopeWrapper(scope)
                .orderByAsc(ArchiveDocument::getFolderPath)
                .orderByAsc(ArchiveDocument::getTitle)
                .last("LIMIT " + MAX_DOCUMENTS));
        if (documents.isEmpty()) {
            return new InspectResult("当前范围内没有正式档案可供核验。", List.of(), List.of(), List.of());
        }
        Map<Long, ArchiveDocument> documentsById = documents.stream()
                .collect(Collectors.toMap(ArchiveDocument::getId, document -> document));
        List<ArchiveExtractedFact> facts = archiveExtractedFactMapper.selectList(new LambdaQueryWrapper<ArchiveExtractedFact>()
                .in(ArchiveExtractedFact::getArchiveDocumentId, documentsById.keySet()));
        List<ArchiveFactEvidence> evidence = toEvidence(facts, documentsById);
        Map<Long, List<ArchiveFactEvidence>> evidenceByDocument = evidence.stream()
                .collect(Collectors.groupingBy(ArchiveFactEvidence::archiveDocumentId));
        List<AgentGovernanceFinding> findings = new ArrayList<>();
        for (ArchiveDocument document : documents) {
            findings.addAll(inspectDocument(document, evidenceByDocument.getOrDefault(document.getId(), List.of())));
        }
        findings.addAll(findDuplicateIdentityCandidates(documentsById, evidenceByDocument));
        boolean truncated = total > MAX_DOCUMENTS;
        long indexedCount = evidenceByDocument.keySet().size();
        String answer = "当前范围共有 %d 份正式档案，本次核验 %d 份；其中 %d 份具有可用页级事实。"
                .formatted(total, documents.size(), indexedCount);
        if (truncated) {
            answer += "范围超过单次核验上限，结论仅覆盖前 %d 份。".formatted(MAX_DOCUMENTS);
        }
        if (indexedCount == 0) {
            answer += "没有可用页级事实，无法对姓名、学号或材料一致性作出判断。";
        } else if (findings.isEmpty()) {
            answer += "在已覆盖的事实中没有发现明确冲突或满足规则的疑似重复候选；这不等同于全部档案均无问题。";
        } else {
            answer += "发现 %d 项需要人工复核的事项，其中明确冲突 %d 项、疑似重复候选 %d 项。"
                    .formatted(findings.size(), findings.stream().filter(item -> "HIGH".equals(item.level())).count(),
                            findings.stream().filter(item -> "MEDIUM".equals(item.level())).count());
        }
        return new InspectResult(answer, documents.stream().limit(10).map(this::toReference).toList(),
                evidence.stream().limit(100).toList(), findings.stream().limit(100).toList());
    }

    private List<AgentGovernanceFinding> inspectDocument(ArchiveDocument document, List<ArchiveFactEvidence> facts) {
        if (facts.isEmpty()) {
            return List.of();
        }
        List<AgentGovernanceFinding> findings = new ArrayList<>();
        List<String> names = values(facts, ArchiveFactType.PERSON_NAME);
        List<String> studentIds = values(facts, ArchiveFactType.STUDENT_ID);
        AgentDocumentReference reference = toReference(document);
        if (names.size() > 1) {
            findings.add(finding("MULTIPLE_PERSON_NAMES", "HIGH", "同一档案页级事实中出现多个学生姓名：" + String.join("、", names),
                    List.of(reference), selectFacts(facts, ArchiveFactType.PERSON_NAME)));
        }
        if (studentIds.size() > 1) {
            findings.add(finding("MULTIPLE_STUDENT_IDS", "HIGH", "同一档案页级事实中出现多个学号：" + String.join("、", studentIds),
                    List.of(reference), selectFacts(facts, ArchiveFactType.STUDENT_ID)));
        }
        String titleName = titlePersonName(document.getTitle());
        if (titleName != null && !names.isEmpty() && !names.contains(titleName)) {
            findings.add(finding("TITLE_PERSON_NAME_MISMATCH", "HIGH",
                    "文件名末尾姓名“%s”与页级识别姓名“%s”不一致。".formatted(titleName, String.join("、", names)),
                    List.of(reference), selectFacts(facts, ArchiveFactType.PERSON_NAME)));
        }
        return findings;
    }

    private List<AgentGovernanceFinding> findDuplicateIdentityCandidates(Map<Long, ArchiveDocument> documentsById,
                                                                           Map<Long, List<ArchiveFactEvidence>> factsByDocument) {
        Map<String, List<Long>> documentIdsByIdentity = new LinkedHashMap<>();
        for (Map.Entry<Long, List<ArchiveFactEvidence>> entry : factsByDocument.entrySet()) {
            List<String> names = values(entry.getValue(), ArchiveFactType.PERSON_NAME);
            List<String> studentIds = values(entry.getValue(), ArchiveFactType.STUDENT_ID);
            if (names.size() == 1 && studentIds.size() == 1) {
                documentIdsByIdentity.computeIfAbsent(names.getFirst() + "|" + studentIds.getFirst(), ignored -> new ArrayList<>())
                        .add(entry.getKey());
            }
        }
        List<AgentGovernanceFinding> findings = new ArrayList<>();
        for (Map.Entry<String, List<Long>> entry : documentIdsByIdentity.entrySet()) {
            if (entry.getValue().size() < 2 || !hasOverlappingMaterials(entry.getValue(), factsByDocument)) {
                continue;
            }
            List<AgentDocumentReference> references = entry.getValue().stream().map(documentsById::get)
                    .map(this::toReference).toList();
            List<ArchiveFactEvidence> evidence = entry.getValue().stream()
                    .flatMap(documentId -> factsByDocument.get(documentId).stream())
                    .filter(fact -> fact.factType() == ArchiveFactType.PERSON_NAME || fact.factType() == ArchiveFactType.STUDENT_ID
                            || fact.factType() == ArchiveFactType.MATERIAL_TYPE)
                    .toList();
            findings.add(finding("DUPLICATE_IDENTITY_CANDIDATE", "MEDIUM",
                    "多个档案具有相同姓名和学号，且材料类型存在重叠：" + entry.getKey().replace('|', '，') + "。需人工确认是重复录入还是同一人的合理多份材料。",
                    references, evidence));
        }
        return findings;
    }

    private boolean hasOverlappingMaterials(List<Long> documentIds, Map<Long, List<ArchiveFactEvidence>> factsByDocument) {
        Set<String> seen = new java.util.HashSet<>();
        for (Long documentId : documentIds) {
            Set<String> materials = factsByDocument.get(documentId).stream()
                    .filter(fact -> fact.factType() == ArchiveFactType.MATERIAL_TYPE)
                    .map(ArchiveFactEvidence::normalizedValue)
                    .collect(Collectors.toSet());
            for (String material : materials) {
                if (!seen.add(material)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> values(List<ArchiveFactEvidence> facts, ArchiveFactType type) {
        return facts.stream().filter(fact -> fact.factType() == type).map(ArchiveFactEvidence::normalizedValue)
                .filter(value -> value != null && !value.isBlank()).distinct().sorted().toList();
    }

    private List<ArchiveFactEvidence> selectFacts(List<ArchiveFactEvidence> facts, ArchiveFactType type) {
        return facts.stream().filter(fact -> fact.factType() == type).toList();
    }

    private AgentGovernanceFinding finding(String type, String level, String message,
                                           List<AgentDocumentReference> documents, List<ArchiveFactEvidence> evidence) {
        return new AgentGovernanceFinding(type, level, message, documents, evidence);
    }

    private String titlePersonName(String title) {
        if (title == null) return null;
        Matcher matcher = TITLE_PERSON_NAME.matcher(title.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    private List<ArchiveFactEvidence> toEvidence(List<ArchiveExtractedFact> facts,
                                                  Map<Long, ArchiveDocument> documentsById) {
        if (facts.isEmpty()) return List.of();
        Map<Long, Integer> pageNos = archiveDocumentPageMapper.selectBatchIds(facts.stream()
                        .map(ArchiveExtractedFact::getArchiveDocumentPageId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ArchiveDocumentPage::getId, ArchiveDocumentPage::getPageNo));
        return facts.stream().map(fact -> new ArchiveFactEvidence(fact.getArchiveDocumentId(),
                        documentsById.get(fact.getArchiveDocumentId()).getTitle(),
                        documentsById.get(fact.getArchiveDocumentId()).getFolderPath(), pageNos.get(fact.getArchiveDocumentPageId()),
                        fact.getFactType(), fact.getFactKey(), fact.getFactValue(), fact.getNormalizedValue(),
                        fact.getConfidence(), fact.getEvidenceText()))
                .sorted(Comparator.comparing(ArchiveFactEvidence::archiveTitle).thenComparing(ArchiveFactEvidence::pageNo))
                .toList();
    }

    private LambdaQueryWrapper<ArchiveDocument> scopeWrapper(AgentResolvedScope scope) {
        LambdaQueryWrapper<ArchiveDocument> wrapper = new LambdaQueryWrapper<ArchiveDocument>()
                .eq(ArchiveDocument::getStatus, ArchiveDocumentStatus.ACTIVE);
        if (scope.hallId() != null) wrapper.eq(ArchiveDocument::getHallId, scope.hallId());
        if (scope.scopeType() == AgentScopeType.DOCUMENT && scope.documentId() != null) {
            wrapper.eq(ArchiveDocument::getId, scope.documentId());
        } else if (scope.scopeType() == AgentScopeType.FOLDER && scope.folderPath() != null && !scope.folderPath().isBlank()) {
            wrapper.and(inner -> inner.eq(ArchiveDocument::getFolderPath, scope.folderPath())
                    .or().likeRight(ArchiveDocument::getFolderPath, scope.folderPath() + "/"));
        } else if (scope.scopeType() == AgentScopeType.TASK && scope.taskId() != null) {
            wrapper.eq(ArchiveDocument::getTaskId, scope.taskId());
        }
        return wrapper;
    }

    private AgentDocumentReference toReference(ArchiveDocument document) {
        OutputFormat format = document.getFileFormat();
        return new AgentDocumentReference(document.getId(), document.getHallId(), document.getTitle(),
                document.getFolderPath(), format == null ? null : format.name());
    }
}

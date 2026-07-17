package com.danganguan.archive.agent.v2.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.agent.v2.tool.ScopeAggregateTool;
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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScopeAggregateToolImpl implements ScopeAggregateTool {
    private static final int MAX_DOCUMENTS = 5_000;
    private static final int RESPONSE_LIMIT = 50;

    private final ArchiveDocumentService archiveDocumentService;
    private final ArchiveDocumentPageMapper archiveDocumentPageMapper;
    private final ArchiveExtractedFactMapper archiveExtractedFactMapper;

    @Override
    public AggregateResult aggregate(String message, AgentResolvedScope scope) {
        long totalDocuments = archiveDocumentService.count(scopeWrapper(scope));
        List<ArchiveDocument> documents = archiveDocumentService.list(scopeWrapper(scope)
                .orderByAsc(ArchiveDocument::getFolderPath)
                .orderByAsc(ArchiveDocument::getTitle)
                .last("LIMIT " + MAX_DOCUMENTS));
        boolean truncated = totalDocuments > MAX_DOCUMENTS;
        if (documents.isEmpty()) {
            return new AggregateResult("当前范围内没有正式档案。", List.of(), List.of());
        }

        List<Long> documentIds = documents.stream().map(ArchiveDocument::getId).toList();
        Map<Long, ArchiveDocument> documentsById = documents.stream()
                .collect(Collectors.toMap(ArchiveDocument::getId, Function.identity()));
        Set<Long> indexedDocumentIds = archiveDocumentPageMapper.selectList(new LambdaQueryWrapper<ArchiveDocumentPage>()
                        .in(ArchiveDocumentPage::getArchiveDocumentId, documentIds))
                .stream()
                .map(ArchiveDocumentPage::getArchiveDocumentId)
                .collect(Collectors.toSet());
        List<ArchiveExtractedFact> facts = archiveExtractedFactMapper.selectList(new LambdaQueryWrapper<ArchiveExtractedFact>()
                .in(ArchiveExtractedFact::getArchiveDocumentId, documentIds));
        List<ArchiveFactEvidence> evidence = toEvidence(facts, documentsById);
        String answer = buildAnswer(message, totalDocuments, documents.size(), indexedDocumentIds.size(), truncated, evidence);
        return new AggregateResult(answer, documents.stream().limit(10).map(this::toReference).toList(),
                selectEvidence(message, evidence));
    }

    private String buildAnswer(String message, long totalDocuments, int inspectedDocuments, int indexedDocuments,
                               boolean truncated, List<ArchiveFactEvidence> evidence) {
        String coverage = "当前范围共有 %d 份正式档案；本次统计读取 %d 份，其中 %d 份已完成页级文本索引。"
                .formatted(totalDocuments, inspectedDocuments, indexedDocuments);
        if (truncated) {
            coverage += "范围超过单次统计上限，以下内容统计仅覆盖前 %d 份，不能视为全量结论。".formatted(MAX_DOCUMENTS);
        }
        if (containsAny(message, "多少文件", "多少个文件", "文件数", "文件数量", "多少档案", "多少份档案", "档案数", "档案数量")
                || (message != null && message.contains("统计") && containsAny(message, "文件", "档案"))) {
            return coverage;
        }
        if (containsAny(message, "哪些学生", "学生名单", "名单")) {
            List<String> names = evidence.stream()
                    .filter(fact -> fact.factType() == ArchiveFactType.PERSON_NAME)
                    .map(ArchiveFactEvidence::normalizedValue)
                    .distinct()
                    .sorted()
                    .toList();
            if (names.isEmpty()) {
                return coverage + "已索引档案中尚未提取到可用学生姓名，因此不能生成可靠名单。";
            }
            String displayed = names.stream().limit(RESPONSE_LIMIT).collect(Collectors.joining("、"));
            String suffix = names.size() > RESPONSE_LIMIT ? "等" : "";
            return coverage + "已从页级事实中识别到 %d 名学生：%s%s。名单只覆盖已完成索引的档案。"
                    .formatted(names.size(), displayed, suffix);
        }
        Map<String, Long> materialCounts = evidence.stream()
                .filter(fact -> fact.factType() == ArchiveFactType.MATERIAL_TYPE)
                .collect(Collectors.groupingBy(fact -> materialLabel(fact.normalizedValue()), LinkedHashMap::new,
                        Collectors.mapping(ArchiveFactEvidence::archiveDocumentId, Collectors.toSet())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) entry.getValue().size(),
                        (left, right) -> left, LinkedHashMap::new));
        if (materialCounts.isEmpty()) {
            return coverage + "已索引档案中尚未提取到材料类型。";
        }
        String materials = materialCounts.entrySet().stream()
                .map(entry -> "%s %d 份".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("；"));
        return coverage + "已抽取到材料类型事实的档案覆盖：" + materials + "。材料数量按档案去重，不按页面重复计数。";
    }

    private List<ArchiveFactEvidence> selectEvidence(String message, List<ArchiveFactEvidence> evidence) {
        if (containsAny(message, "哪些学生", "学生名单", "名单")) {
            return evidence.stream().filter(fact -> fact.factType() == ArchiveFactType.PERSON_NAME)
                    .limit(RESPONSE_LIMIT).toList();
        }
        return evidence.stream().filter(fact -> fact.factType() == ArchiveFactType.MATERIAL_TYPE)
                .limit(RESPONSE_LIMIT).toList();
    }

    private List<ArchiveFactEvidence> toEvidence(List<ArchiveExtractedFact> facts,
                                                  Map<Long, ArchiveDocument> documentsById) {
        if (facts.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> pageNos = archiveDocumentPageMapper.selectBatchIds(facts.stream()
                        .map(ArchiveExtractedFact::getArchiveDocumentPageId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ArchiveDocumentPage::getId, ArchiveDocumentPage::getPageNo));
        return facts.stream()
                .map(fact -> new ArchiveFactEvidence(fact.getArchiveDocumentId(),
                        documentsById.get(fact.getArchiveDocumentId()).getTitle(),
                        documentsById.get(fact.getArchiveDocumentId()).getFolderPath(),
                        pageNos.get(fact.getArchiveDocumentPageId()), fact.getFactType(), fact.getFactKey(),
                        fact.getFactValue(), fact.getNormalizedValue(), fact.getConfidence(), fact.getEvidenceText()))
                .sorted(Comparator.comparing(ArchiveFactEvidence::archiveTitle).thenComparing(ArchiveFactEvidence::pageNo))
                .toList();
    }

    private LambdaQueryWrapper<ArchiveDocument> scopeWrapper(AgentResolvedScope scope) {
        LambdaQueryWrapper<ArchiveDocument> wrapper = new LambdaQueryWrapper<ArchiveDocument>()
                .eq(ArchiveDocument::getStatus, ArchiveDocumentStatus.ACTIVE);
        if (scope.hallId() != null) {
            wrapper.eq(ArchiveDocument::getHallId, scope.hallId());
        }
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

    private String materialLabel(String value) {
        return switch (value) {
            case "TRANSCRIPT" -> "成绩单";
            case "STUDENT_STATUS_FORM" -> "学籍材料";
            case "GRADUATION_APPRAISAL" -> "毕业鉴定";
            case "REVIEW_FORM" -> "评阅材料";
            case "DEGREE_AWARD_DECISION" -> "学位授予材料";
            default -> value;
        };
    }

    private boolean containsAny(String message, String... terms) {
        if (message == null) return false;
        for (String term : terms) {
            if (message.contains(term)) return true;
        }
        return false;
    }
}

package com.danganguan.archive.agent.v2.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.agent.v2.tool.ArchiveLocateTool;
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ArchiveLocateToolImpl implements ArchiveLocateTool {
    private static final int LIMIT = 20;
    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("(?<!\\d)(\\d{6,})(?!\\d)");
    private static final Pattern ARCHIVE_NO_PATTERN = Pattern.compile("(\\d{4}-[A-Za-z]{1,6}\\d{1,4}[•.．·\\-]\\d{1,4}[•.．·\\-]\\d{1,6}(?:-\\d+)?[\\u4e00-\\u9fa5]{0,4})");
    private static final Pattern NAME_BEFORE_POSSESSIVE = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})的");
    private static final Pattern NAME_AFTER_CALLED = Pattern.compile("(?:叫|姓名为|姓名是)([\\u4e00-\\u9fa5]{2,4})");
    private static final List<String> NON_NAME_TOKENS = List.of("当前文件", "这个文件", "这份档案", "学生姓名", "成绩单", "学籍材料");

    private final ArchiveDocumentFactQueryService archiveDocumentFactQueryService;
    private final ArchiveDocumentService archiveDocumentService;
    private final ArchivePageSearchService archivePageSearchService;

    @Override
    public LocateResult locate(String message, AgentResolvedScope scope) {
        if (!hasSearchClue(message) && !(scope.scopeType() == AgentScopeType.DOCUMENT && scope.documentId() != null)) {
            return new LocateResult("没有识别到可用于定位的姓名、学号、档号或材料类型。请补充具体线索；“这份档案”需要在档案详情页中使用，或在同一会话中保留上轮定位结果。", List.of(), List.of());
        }
        List<ArchiveFactEvidence> evidence = locateByFacts(message, scope);
        List<AgentDocumentReference> documents = documentsFromEvidence(evidence);
        if (documents.isEmpty()) {
            documents = locateByMetadata(message, scope);
        }
        if (documents.isEmpty()) {
            documents = locateByPageText(message, scope);
        }
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

    private List<AgentDocumentReference> locateByPageText(String message, AgentResolvedScope scope) {
        Map<Long, AgentDocumentReference> documents = new LinkedHashMap<>();
        for (ArchivePageSearchHit hit : archivePageSearchService.search(scope, message, LIMIT)) {
            documents.putIfAbsent(hit.documentId(), new AgentDocumentReference(hit.documentId(), hit.hallId(), hit.title(),
                    hit.folderPath(), null));
        }
        return List.copyOf(documents.values());
    }

    private List<ArchiveFactEvidence> locateByFacts(String message, AgentResolvedScope scope) {
        String studentId = firstMatch(STUDENT_ID_PATTERN, message);
        if (studentId != null) {
            return factSearch(scope, ArchiveFactType.STUDENT_ID, studentId);
        }
        String personName = extractName(message);
        if (personName != null) {
            List<ArchiveFactEvidence> personEvidence = factSearch(scope, ArchiveFactType.PERSON_NAME, personName);
            String material = materialValue(message);
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
        String material = materialValue(message);
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

    private List<AgentDocumentReference> locateByMetadata(String message, AgentResolvedScope scope) {
        String archiveNo = firstMatch(ARCHIVE_NO_PATTERN, message);
        String name = extractName(message);
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

    private String extractName(String message) {
        if (message == null) {
            return null;
        }
        for (Pattern pattern : List.of(NAME_BEFORE_POSSESSIVE, NAME_AFTER_CALLED)) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String candidate = matcher.group(1)
                        .replaceFirst("^(帮我|请|我)?(找|查|搜索)", "");
                if (!NON_NAME_TOKENS.contains(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private String materialValue(String message) {
        if (message == null) return null;
        if (message.contains("成绩单") || message.contains("成绩")) return "TRANSCRIPT";
        if (message.contains("学籍")) return "STUDENT_STATUS_FORM";
        if (message.contains("毕业鉴定")) return "GRADUATION_APPRAISAL";
        if (message.contains("评阅")) return "REVIEW_FORM";
        if (message.contains("学位")) return "DEGREE_AWARD_DECISION";
        return null;
    }

    private boolean hasSearchClue(String message) {
        return firstMatch(STUDENT_ID_PATTERN, message) != null
                || firstMatch(ARCHIVE_NO_PATTERN, message) != null
                || extractName(message) != null
                || materialValue(message) != null;
    }

    private String firstMatch(Pattern pattern, String text) {
        if (text == null) return null;
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}

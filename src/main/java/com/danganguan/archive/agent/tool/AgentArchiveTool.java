package com.danganguan.archive.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AgentArchiveTool {
    private static final int SEARCH_LIMIT = 20;
    private static final int SCOPE_SCAN_LIMIT = 5000;
    private static final Pattern PERSON_NAME_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})$");

    private final ArchiveDocumentService archiveDocumentService;

    public SearchResult search(String message, AgentResolvedScope scope) {
        List<String> keywords = extractKeywords(message);
        LambdaQueryWrapper<ArchiveDocument> wrapper = baseScopeWrapper(scope)
                .orderByDesc(ArchiveDocument::getArchivedAt)
                .last("LIMIT " + SEARCH_LIMIT);
        if (!keywords.isEmpty()) {
            wrapper.and(and -> {
                for (int i = 0; i < keywords.size(); i++) {
                    String keyword = keywords.get(i);
                    if (i > 0) {
                        and.or();
                    }
                    and.like(ArchiveDocument::getTitle, keyword)
                            .or()
                            .like(ArchiveDocument::getFolderPath, keyword)
                            .or()
                            .like(ArchiveDocument::getAiSummary, keyword)
                            .or()
                            .like(ArchiveDocument::getOcrText, keyword);
                }
            });
        }
        List<ArchiveDocument> documents = archiveDocumentService.list(wrapper);
        return new SearchResult(keywords, toReferences(documents), documents.size());
    }

    public ScopeSummary summarize(AgentResolvedScope scope) {
        List<ArchiveDocument> documents = loadScopeDocuments(scope);
        Map<String, Integer> materialCounts = new LinkedHashMap<>();
        int transcriptCount = 0;
        int degreeCount = 0;
        int studentStatusCount = 0;
        for (ArchiveDocument document : documents) {
            String material = inferMaterialType(document);
            materialCounts.merge(material, 1, Integer::sum);
            if ("成绩单".equals(material)) {
                transcriptCount++;
            } else if ("学位材料".equals(material)) {
                degreeCount++;
            } else if ("学籍材料".equals(material)) {
                studentStatusCount++;
            }
        }
        long personCount = documents.stream()
                .map(this::inferPersonName)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        return new ScopeSummary(documents.size(), (int) personCount, transcriptCount, degreeCount,
                studentStatusCount, materialCounts, toReferences(documents.stream().limit(10).toList()));
    }

    public MissingMaterialResult checkMissingMaterials(AgentResolvedScope scope) {
        List<ArchiveDocument> documents = loadScopeDocuments(scope);
        Map<String, PersonMaterials> people = new LinkedHashMap<>();
        for (ArchiveDocument document : documents) {
            String person = inferPersonName(document);
            if (person == null) {
                continue;
            }
            PersonMaterials materials = people.computeIfAbsent(person, PersonMaterials::new);
            materials.documents.add(document);
            String materialType = inferMaterialType(document);
            if ("成绩单".equals(materialType)) {
                materials.hasTranscript = true;
            }
            if ("学籍材料".equals(materialType)) {
                materials.hasStudentStatus = true;
            }
            if ("学位材料".equals(materialType)) {
                materials.hasDegree = true;
            }
        }

        List<MissingPerson> missing = people.values().stream()
                .filter(person -> !person.hasTranscript || !person.hasStudentStatus || !person.hasDegree)
                .map(person -> new MissingPerson(
                        person.name,
                        !person.hasTranscript,
                        !person.hasStudentStatus,
                        !person.hasDegree,
                        toReferences(person.documents.stream().limit(5).toList())
                ))
                .sorted(Comparator.comparing(MissingPerson::name))
                .limit(50)
                .toList();
        return new MissingMaterialResult(people.size(), missing.size(), missing);
    }

    private List<ArchiveDocument> loadScopeDocuments(AgentResolvedScope scope) {
        return archiveDocumentService.list(baseScopeWrapper(scope)
                .orderByAsc(ArchiveDocument::getFolderPath)
                .orderByAsc(ArchiveDocument::getTitle)
                .last("LIMIT " + SCOPE_SCAN_LIMIT));
    }

    private LambdaQueryWrapper<ArchiveDocument> baseScopeWrapper(AgentResolvedScope scope) {
        LambdaQueryWrapper<ArchiveDocument> wrapper = new LambdaQueryWrapper<ArchiveDocument>()
                .eq(ArchiveDocument::getStatus, ArchiveDocumentStatus.ACTIVE);
        if (scope.hallId() != null) {
            wrapper.eq(ArchiveDocument::getHallId, scope.hallId());
        }
        if (scope.scopeType() == AgentScopeType.DOCUMENT && scope.documentId() != null) {
            wrapper.eq(ArchiveDocument::getId, scope.documentId());
        } else if (scope.scopeType() == AgentScopeType.FOLDER && scope.folderPath() != null && !scope.folderPath().isBlank()) {
            String folderPath = scope.folderPath();
            wrapper.and(inner -> inner
                    .eq(ArchiveDocument::getFolderPath, folderPath)
                    .or()
                    .likeRight(ArchiveDocument::getFolderPath, folderPath + "/"));
        } else if (scope.scopeType() == AgentScopeType.TASK && scope.taskId() != null) {
            wrapper.eq(ArchiveDocument::getTaskId, scope.taskId());
        }
        return wrapper;
    }

    private List<String> extractKeywords(String message) {
        String text = message == null ? "" : message.trim();
        List<String> keywords = new ArrayList<>();
        for (String candidate : List.of("成绩单", "成绩", "学籍", "学位", "毕业", "档案", "材料")) {
            if (text.contains(candidate)) {
                keywords.add(candidate);
            }
        }
        String cleaned = text
                .replaceAll("(查一下|找一下|查询|查找|搜索|有没有|请问|帮我|一下|当前|目录|文件夹|全校|全馆|所有|全部)", " ")
                .replaceAll("(如果没有|如果没找到|没找到|告诉我|下一步|怎么查|该怎么|怎么办|可以怎么|如何|如果|没有|就)", " ")
                .replaceAll("(成绩单|成绩|学籍|学位|档案|材料|的|里|中|下|吗|呢)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        Matcher matcher = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._•\\-]{2,}|[\\u4e00-\\u9fa5]{2,4}").matcher(cleaned);
        while (matcher.find()) {
            String token = matcher.group();
            if (!isStopWord(token) && keywords.stream().noneMatch(token::equals)) {
                keywords.add(token);
            }
        }
        return keywords.stream().limit(6).toList();
    }

    private boolean isStopWord(String token) {
        return List.of("查一下", "找一下", "有没有", "当前", "目录", "文件夹", "全校", "全馆", "总结", "汇总",
                        "哪些", "这个", "如果", "没有", "告诉我", "下一步", "怎么查", "怎么办")
                .contains(token);
    }

    private String inferMaterialType(ArchiveDocument document) {
        String text = ((document.getTitle() == null ? "" : document.getTitle()) + " "
                + (document.getAiSummary() == null ? "" : document.getAiSummary()) + " "
                + (document.getOcrText() == null ? "" : document.getOcrText())).toLowerCase(Locale.ROOT);
        if (text.contains("成绩")) {
            return "成绩单";
        }
        if (text.contains("学位")) {
            return "学位材料";
        }
        if (text.contains("学籍")) {
            return "学籍材料";
        }
        return "其他学生材料";
    }

    private String inferPersonName(ArchiveDocument document) {
        String title = document.getTitle() == null ? "" : document.getTitle().trim();
        int dot = title.lastIndexOf('.');
        if (dot > 0) {
            title = title.substring(0, dot);
        }
        title = title.replaceAll("(成绩单|成绩|学籍|学位|档案|材料)$", "");
        Matcher matcher = PERSON_NAME_PATTERN.matcher(title);
        return matcher.find() ? matcher.group(1) : null;
    }

    private List<AgentDocumentReference> toReferences(List<ArchiveDocument> documents) {
        return documents.stream()
                .map(document -> new AgentDocumentReference(
                        document.getId(),
                        document.getHallId(),
                        document.getTitle(),
                        document.getFolderPath(),
                        document.getFileFormat() == null ? null : document.getFileFormat().name()
                ))
                .toList();
    }

    public record SearchResult(List<String> keywords, List<AgentDocumentReference> references, int total) {
    }

    public record ScopeSummary(int documentCount, int personCount, int transcriptCount, int degreeCount,
                               int studentStatusCount, Map<String, Integer> materialCounts,
                               List<AgentDocumentReference> sampleReferences) {
    }

    public record MissingMaterialResult(int personCount, int missingPersonCount, List<MissingPerson> missingPeople) {
    }

    public record MissingPerson(String name, boolean missingTranscript, boolean missingStudentStatus,
                                boolean missingDegree, List<AgentDocumentReference> references) {
    }

    private static class PersonMaterials {
        private final String name;
        private final List<ArchiveDocument> documents = new ArrayList<>();
        private boolean hasTranscript;
        private boolean hasStudentStatus;
        private boolean hasDegree;

        private PersonMaterials(String name) {
            this.name = name;
        }
    }
}

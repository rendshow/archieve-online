package com.danganguan.archive.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.agent.retrieval.AgentArchiveRetriever;
import com.danganguan.archive.agent.retrieval.ArchiveRetrievalHit;
import com.danganguan.archive.agent.retrieval.ArchiveRetrievalResult;
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
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AgentArchiveTool {
    private static final int SCOPE_SCAN_LIMIT = 5000;
    private static final Pattern PERSON_NAME_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})$");
    private static final Pattern YEAR_SEGMENT_PATTERN = Pattern.compile("^(19\\d{2}|20\\d{2})$");
    private static final Pattern YEAR_RANGE_PATTERN = Pattern.compile("(19\\d{2}|20\\d{2})\\s*[-~—–至到]\\s*(19\\d{2}|20\\d{2})");

    private final ArchiveDocumentService archiveDocumentService;
    private final AgentArchiveRetriever archiveRetriever;

    public SearchResult search(String message, AgentResolvedScope scope) {
        ArchiveRetrievalResult result = archiveRetriever.retrieve(message, scope);
        List<ArchiveRetrievalHit> hits = result.hits();
        return new SearchResult(result.keywords(), result.materialKeywords(), toReferencesFromHits(hits), hits.size(),
                result.requiresMaterialEvidence(), result.hasMaterialContentEvidence(), hits);
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

    public YearDistribution summarizeYears(AgentResolvedScope scope) {
        List<ArchiveDocument> documents = loadScopeDocuments(scope);
        Map<String, Integer> yearCounts = new TreeMap<>();
        YearRange expectedRange = extractExpectedYearRange(scope.folderPath());
        for (ArchiveDocument document : documents) {
            for (String year : extractTrustedDirectoryYears(scope, document)) {
                yearCounts.merge(year, 1, Integer::sum);
            }
        }
        List<String> missingYears = missingYears(expectedRange, yearCounts);
        return new YearDistribution(documents.size(), yearCounts, missingYears,
                expectedRange == null ? null : expectedRange.startYear(),
                expectedRange == null ? null : expectedRange.endYear(),
                "目录层级", toReferences(documents.stream().limit(10).toList()));
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

    private List<String> extractTrustedDirectoryYears(AgentResolvedScope scope, ArchiveDocument document) {
        String folderPath = normalizePath(document.getFolderPath());
        String scopePath = normalizePath(scope.folderPath());
        if (folderPath == null || folderPath.isBlank()) {
            return List.of();
        }
        String scopeYear = exactYear(lastSegment(scopePath));
        if (scopeYear != null && (folderPath.equals(scopePath) || folderPath.startsWith(scopePath + "/"))) {
            return List.of(scopeYear);
        }
        if (scope.scopeType() == AgentScopeType.FOLDER && scopePath != null && !scopePath.isBlank()) {
            String relativePath = relativePath(scopePath, folderPath);
            String directChild = firstSegment(relativePath);
            String directChildYear = exactYear(directChild);
            return directChildYear == null ? List.of() : List.of(directChildYear);
        }
        return exactYearSegments(folderPath);
    }

    private List<String> exactYearSegments(String folderPath) {
        List<String> years = new ArrayList<>();
        for (String segment : folderPath.split("/")) {
            String year = exactYear(segment);
            if (year != null && !years.contains(year)) {
                years.add(year);
            }
        }
        return years;
    }

    private String exactYear(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = YEAR_SEGMENT_PATTERN.matcher(text.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private YearRange extractExpectedYearRange(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = YEAR_RANGE_PATTERN.matcher(text);
        YearRange range = null;
        while (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1));
            int end = Integer.parseInt(matcher.group(2));
            if (start <= end && end - start <= 80) {
                range = new YearRange(start, end);
            }
        }
        return range;
    }

    private List<String> missingYears(YearRange range, Map<String, Integer> yearCounts) {
        if (range == null) {
            return List.of();
        }
        List<String> missingYears = new ArrayList<>();
        for (int year = range.startYear(); year <= range.endYear(); year++) {
            if (!yearCounts.containsKey(String.valueOf(year))) {
                missingYears.add(String.valueOf(year));
            }
        }
        return missingYears;
    }

    private String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String relativePath(String scopePath, String folderPath) {
        if (folderPath.equals(scopePath)) {
            return "";
        }
        return folderPath.startsWith(scopePath + "/") ? folderPath.substring(scopePath.length() + 1) : folderPath;
    }

    private String firstSegment(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }

    private String lastSegment(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
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

    private List<AgentDocumentReference> toReferencesFromHits(List<ArchiveRetrievalHit> hits) {
        return hits.stream()
                .map(ArchiveRetrievalHit::document)
                .map(document -> new AgentDocumentReference(
                        document.getId(),
                        document.getHallId(),
                        document.getTitle(),
                        document.getFolderPath(),
                        document.getFileFormat() == null ? null : document.getFileFormat().name()
                ))
                .toList();
    }

    public record SearchResult(List<String> keywords, List<String> materialKeywords,
                               List<AgentDocumentReference> references, int total,
                               boolean requiresMaterialEvidence, boolean hasMaterialContentEvidence,
                               List<ArchiveRetrievalHit> hits) {
    }

    public record ScopeSummary(int documentCount, int personCount, int transcriptCount, int degreeCount,
                               int studentStatusCount, Map<String, Integer> materialCounts,
                               List<AgentDocumentReference> sampleReferences) {
    }

    public record YearDistribution(int documentCount, Map<String, Integer> yearCounts, List<String> missingYears,
                                   Integer expectedStartYear, Integer expectedEndYear, String source,
                                   List<AgentDocumentReference> sampleReferences) {
    }

    private record YearRange(int startYear, int endYear) {
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

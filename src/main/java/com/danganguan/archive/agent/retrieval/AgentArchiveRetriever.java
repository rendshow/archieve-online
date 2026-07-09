package com.danganguan.archive.agent.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AgentArchiveRetriever {
    private static final int SQL_LIMIT = 80;
    private static final int DEFAULT_LIMIT = 10;
    private static final int SNIPPET_LIMIT = 900;
    private static final Pattern ARCHIVE_CODE_PATTERN = Pattern.compile(
            "\\d{4}-[A-Za-z]{1,6}\\d{1,4}[•.．·\\-]\\d{1,4}[•.．·\\-]\\d{1,6}(?:-\\d+)?[\\u4e00-\\u9fa5]{0,4}"
    );
    private static final Pattern PERSON_NAME_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,4}");

    private final ArchiveDocumentService archiveDocumentService;

    public ArchiveRetrievalResult retrieve(String message, AgentResolvedScope scope) {
        return retrieve(message, scope, DEFAULT_LIMIT);
    }

    public ArchiveRetrievalResult retrieve(String message, AgentResolvedScope scope, int limit) {
        List<String> keywords = extractKeywords(message);
        List<String> materialKeywords = materialKeywords(message);
        List<ArchiveDocument> candidates = loadCandidates(scope, keywords);
        List<ArchiveRetrievalHit> hits = candidates.stream()
                .map(document -> score(document, keywords))
                .filter(hit -> hit.score() > 0 || keywords.isEmpty())
                .sorted(Comparator
                        .comparingInt(ArchiveRetrievalHit::score).reversed()
                        .thenComparing(hit -> safeText(hit.document().getTitle())))
                .limit(Math.max(1, limit))
                .toList();
        return new ArchiveRetrievalResult(keywords, materialKeywords, hits);
    }

    private List<ArchiveDocument> loadCandidates(AgentResolvedScope scope, List<String> keywords) {
        LambdaQueryWrapper<ArchiveDocument> wrapper = baseScopeWrapper(scope)
                .orderByDesc(ArchiveDocument::getArchivedAt)
                .last("LIMIT " + SQL_LIMIT);
        if (!keywords.isEmpty()) {
            wrapper.and(inner -> {
                for (int i = 0; i < keywords.size(); i++) {
                    String keyword = keywords.get(i);
                    if (i > 0) {
                        inner.or();
                    }
                    inner.like(ArchiveDocument::getTitle, keyword)
                            .or()
                            .like(ArchiveDocument::getFolderName, keyword)
                            .or()
                            .like(ArchiveDocument::getFolderPath, keyword)
                            .or()
                            .like(ArchiveDocument::getAiSummary, keyword)
                            .or()
                            .like(ArchiveDocument::getOcrText, keyword);
                }
            });
        }
        return archiveDocumentService.list(wrapper);
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

    private ArchiveRetrievalHit score(ArchiveDocument document, List<String> keywords) {
        int score = 0;
        Set<String> matchedKeywords = new LinkedHashSet<>();
        Set<String> matchedFields = new LinkedHashSet<>();
        boolean contentEvidence = false;
        String title = safeText(document.getTitle());
        String folder = safeText(document.getFolderPath()) + " " + safeText(document.getFolderName());
        String summary = safeText(document.getAiSummary());
        String ocr = safeText(document.getOcrText());
        for (String keyword : keywords) {
            boolean matched = false;
            if (contains(title, keyword)) {
                score += titleScore(keyword);
                matched = true;
                matchedFields.add("题名");
            }
            if (contains(folder, keyword)) {
                score += 10;
                matched = true;
                matchedFields.add("目录");
            }
            if (contains(summary, keyword)) {
                score += 70;
                matched = true;
                contentEvidence = true;
                matchedFields.add("摘要");
            }
            if (contains(ocr, keyword)) {
                score += 90;
                matched = true;
                contentEvidence = true;
                matchedFields.add("OCR正文");
            }
            if (matched) {
                matchedKeywords.add(keyword);
            }
        }
        String snippet = bestSnippet(firstNonBlank(ocr, summary), new ArrayList<>(matchedKeywords));
        return new ArchiveRetrievalHit(document, score,
                contentEvidence ? ArchiveRetrievalHit.EvidenceLevel.CONTENT : ArchiveRetrievalHit.EvidenceLevel.METADATA,
                new ArrayList<>(matchedKeywords), new ArrayList<>(matchedFields), snippet);
    }

    private List<String> extractKeywords(String message) {
        String text = message == null ? "" : message.trim();
        Set<String> keywords = new LinkedHashSet<>();
        Matcher archiveCodeMatcher = ARCHIVE_CODE_PATTERN.matcher(text);
        while (archiveCodeMatcher.find()) {
            keywords.add(archiveCodeMatcher.group());
        }
        for (String material : List.of("成绩单", "成绩", "学籍", "学位", "导师", "指导教师", "毕业", "证明")) {
            if (text.contains(material)) {
                keywords.add(material);
            }
        }
        String cleaned = text
                .replaceAll("(帮我|找一下|查一下|查询|查找|搜索|分析一下|分析|这个文档|这份文档|这个档案|这份档案)", " ")
                .replaceAll("(有哪些|哪些|什么|信息|记录|里面|当前|文件夹|目录|及其子目录|的|里|中|下|吗|呢|一下)", " ")
                .replaceAll("(成绩单|成绩|学籍|学位|档案|材料|文档|文件)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        Matcher nameMatcher = PERSON_NAME_PATTERN.matcher(cleaned);
        while (nameMatcher.find()) {
            String token = nameMatcher.group();
            if (!isStopWord(token)) {
                keywords.add(token);
            }
        }
        return keywords.stream().limit(8).toList();
    }

    private List<String> materialKeywords(String message) {
        String text = message == null ? "" : message;
        List<String> keywords = new ArrayList<>();
        if (text.contains("成绩单") || text.contains("成绩")) {
            keywords.add("成绩");
        }
        if (text.contains("学籍")) {
            keywords.add("学籍");
        }
        if (text.contains("学位")) {
            keywords.add("学位");
        }
        if (text.contains("导师") || text.contains("指导教师")) {
            keywords.add("导师");
            keywords.add("指导教师");
        }
        return keywords;
    }

    private String bestSnippet(String text, List<String> keywords) {
        if (text == null || text.isBlank()) {
            return "";
        }
        for (String keyword : keywords) {
            int index = text.indexOf(keyword);
            if (index >= 0) {
                int start = Math.max(0, index - 300);
                int end = Math.min(text.length(), index + keyword.length() + 600);
                return text.substring(start, end);
            }
        }
        return text.length() <= SNIPPET_LIMIT ? text : text.substring(0, SNIPPET_LIMIT);
    }

    private boolean contains(String source, String keyword) {
        return source != null && keyword != null && !keyword.isBlank() && source.contains(keyword);
    }

    private boolean isArchiveCode(String keyword) {
        return keyword != null && ARCHIVE_CODE_PATTERN.matcher(keyword).matches();
    }

    private int titleScore(String keyword) {
        if (isArchiveCode(keyword)) {
            return 120;
        }
        if (isPersonLike(keyword)) {
            return 90;
        }
        return 45;
    }

    private boolean isPersonLike(String keyword) {
        return keyword != null
                && keyword.length() >= 2
                && keyword.length() <= 4
                && PERSON_NAME_PATTERN.matcher(keyword).matches()
                && !List.of("成绩", "学籍", "学位", "导师", "毕业", "证明").contains(keyword);
    }

    private boolean isStopWord(String token) {
        return List.of("当前", "文件", "文档", "档案", "材料", "这个", "这份", "哪些", "什么", "信息", "记录").contains(token);
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? safeText(second) : first;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}

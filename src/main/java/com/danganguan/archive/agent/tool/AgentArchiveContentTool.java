package com.danganguan.archive.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.agent.dto.AgentClientContext;
import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AgentArchiveContentTool {
    private static final int DOCUMENT_LIMIT = 10;
    private static final int SNIPPET_LIMIT = 1000;

    private final ArchiveDocumentService archiveDocumentService;

    public DiscussResult discuss(String message, AgentResolvedScope scope, AgentClientContext context) {
        List<String> keywords = extractKeywords(message);
        List<ArchiveDocument> documents = loadDocuments(scope, context, keywords);
        List<ContentSnippet> snippets = documents.stream()
                .map(document -> toSnippet(document, keywords))
                .toList();
        return new DiscussResult(keywords, snippets, toReferences(documents));
    }

    public boolean hasExplicitContentScope(AgentResolvedScope scope, AgentClientContext context) {
        return scope.scopeType() == AgentScopeType.DOCUMENT
                || scope.scopeType() == AgentScopeType.FOLDER
                || (context != null && context.selectedDocumentIds() != null && !context.selectedDocumentIds().isEmpty());
    }

    private List<ArchiveDocument> loadDocuments(AgentResolvedScope scope, AgentClientContext context, List<String> keywords) {
        LambdaQueryWrapper<ArchiveDocument> wrapper = baseScopeWrapper(scope);
        if (context != null && context.selectedDocumentIds() != null && !context.selectedDocumentIds().isEmpty()) {
            wrapper.in(ArchiveDocument::getId, context.selectedDocumentIds());
        } else if (!keywords.isEmpty()) {
            List<String> searchKeywords = contentSearchKeywords(keywords);
            wrapper.and(inner -> {
                for (int i = 0; i < searchKeywords.size(); i++) {
                    String keyword = searchKeywords.get(i);
                    if (i > 0) {
                        inner.or();
                    }
                    inner.like(ArchiveDocument::getAiSummary, keyword)
                            .or()
                            .like(ArchiveDocument::getOcrText, keyword);
                }
            });
        }
        return archiveDocumentService.list(wrapper
                .orderByDesc(ArchiveDocument::getArchivedAt)
                .last("LIMIT " + DOCUMENT_LIMIT));
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

    private ContentSnippet toSnippet(ArchiveDocument document, List<String> keywords) {
        String summary = blankToEmpty(document.getAiSummary());
        String ocrText = blankToEmpty(document.getOcrText()).replaceAll("\\s+", " ").trim();
        String snippet = bestSnippet(ocrText, keywords);
        if (snippet.isBlank()) {
            snippet = limit(summary, SNIPPET_LIMIT);
        }
        if (snippet.isBlank()) {
            snippet = "当前档案没有可用 OCR 正文或摘要。";
        }
        return new ContentSnippet(
                document.getId(),
                document.getTitle(),
                document.getFolderPath(),
                summary,
                snippet,
                keywords.isEmpty() ? "按当前页面范围选取" : "按问题关键词匹配摘要或 OCR"
        );
    }

    private String bestSnippet(String text, List<String> keywords) {
        if (text.isBlank()) {
            return "";
        }
        for (String keyword : keywords) {
            int index = text.indexOf(keyword);
            if (index >= 0) {
                int start = Math.max(0, index - 300);
                int end = Math.min(text.length(), index + keyword.length() + 700);
                return text.substring(start, end);
            }
        }
        return limit(text, SNIPPET_LIMIT);
    }

    private List<String> extractKeywords(String message) {
        String text = message == null ? "" : message.trim();
        Set<String> keywords = new LinkedHashSet<>();
        Matcher surnameMatcher = Pattern.compile("姓([\\u4e00-\\u9fa5]{1,4})").matcher(text);
        while (surnameMatcher.find()) {
            keywords.add(surnameMatcher.group(1));
        }
        if (text.contains("导师")) {
            keywords.add("导师");
        }
        String cleaned = text
                .replaceAll("(这份|这些|当前|档案|文件|材料|内容|讲了什么|主要是什么|有没有|是否|提到|帮我|看一下|概括一下|总结一下)", " ")
                .replaceAll("(有哪些|哪些|学生|导师姓|姓|导师)", " ")
                .replaceAll("(的|里|中|下|吗|呢|一下)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        Matcher matcher = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._•\\-]{2,}|[\\u4e00-\\u9fa5]{2,8}").matcher(cleaned);
        while (matcher.find()) {
            String token = matcher.group();
            if (!isStopWord(token)) {
                keywords.add(token);
            }
        }
        for (String keyword : List.of("成绩", "学籍", "学位", "导师", "休学", "转专业", "处分", "奖励", "毕业", "证明")) {
            if (text.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        return keywords.stream().limit(8).toList();
    }

    private boolean isStopWord(String token) {
        return List.of("这个", "当前", "这些", "这份", "是否", "有没有", "主要", "什么", "里面",
                "有哪些", "哪些", "学生", "档案", "文件", "材料").contains(token);
    }

    private List<String> contentSearchKeywords(List<String> keywords) {
        List<String> specificKeywords = keywords.stream()
                .filter(keyword -> !List.of("导师", "学生", "档案", "文件", "材料", "内容").contains(keyword))
                .toList();
        return specificKeywords.isEmpty() ? keywords : specificKeywords;
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

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }

    public record DiscussResult(List<String> keywords, List<ContentSnippet> snippets,
                                List<AgentDocumentReference> references) {
    }

    public record ContentSnippet(Long documentId, String title, String folderPath, String summary,
                                 String snippet, String matchedReason) {
    }
}

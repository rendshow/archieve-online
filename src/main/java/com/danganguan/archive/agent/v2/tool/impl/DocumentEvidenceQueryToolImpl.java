package com.danganguan.archive.agent.v2.tool.impl;

import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.agent.v2.tool.DocumentEvidenceQueryTool;
import com.danganguan.archive.document.fact.dto.ArchiveFactEvidence;
import com.danganguan.archive.document.fact.dto.ArchiveFactSearchRequest;
import com.danganguan.archive.document.fact.enums.ArchiveFactType;
import com.danganguan.archive.document.fact.service.ArchiveDocumentFactQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class DocumentEvidenceQueryToolImpl implements DocumentEvidenceQueryTool {
    private static final Pattern PERSON_BEFORE_POSSESSIVE = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})的");
    private static final Pattern PERSON_BEFORE_QUESTION = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})(?=(?:哪一年|哪年|是谁|什么人|何时|什么时候))");
    private static final Pattern COURSE_AFTER_POSSESSIVE = Pattern.compile("的([\\u4e00-\\u9fa5]{2,20})成绩");
    private static final Pattern COURSE_DIRECT = Pattern.compile("([\\u4e00-\\u9fa5]{2,20})成绩(?:是多少|多少|是|为)?");
    private static final List<String> NON_NAME_TOKENS = List.of("当前文件", "这个文件", "这份档案", "学生姓名", "高等数学", "档案信息");

    private final ArchiveDocumentFactQueryService archiveDocumentFactQueryService;

    @Override
    public QueryResult query(String message, AgentResolvedScope scope) {
        List<ArchiveFactEvidence> facts = loadFacts(message, scope);
        if (facts.isEmpty()) {
            return new QueryResult("当前范围内没有找到可核查的页级事实。该档案可能尚未完成文本索引，或给出的姓名/线索未命中。", List.of());
        }
        String courseName = extractCourseName(message);
        if (courseName != null) {
            return courseAnswer(courseName, facts);
        }
        if (containsAny(message, "学位", "授予日期", "什么时候", "几月份", "毕业", "哪一年", "哪年")) {
            QueryResult result = firstFactAnswer("学位授予日期", facts, ArchiveFactType.DEGREE_AWARD_DATE);
            if (containsAny(message, "毕业", "哪一年", "哪年") && !result.evidence().isEmpty()) {
                if (containsAny(message, "是谁", "什么人")) {
                    QueryResult identity = personOverviewAnswer(facts);
                    List<ArchiveFactEvidence> evidence = java.util.stream.Stream.concat(identity.evidence().stream(), result.evidence().stream())
                            .distinct().toList();
                    return new QueryResult(identity.answer() + "档案中可核验的学位授予日期为 "
                            + result.evidence().getFirst().factValue() + "。该日期不能自动等同于毕业日期。", evidence);
                }
                return new QueryResult("档案中可核验的是" + result.answer() + "该日期为学位授予日期，不能自动等同于毕业日期。", result.evidence());
            }
            return result;
        }
        if (message != null && message.contains("学号")) {
            return firstFactAnswer("学号", facts, ArchiveFactType.STUDENT_ID);
        }
        return overviewAnswer(message, facts);
    }

    private List<ArchiveFactEvidence> loadFacts(String message, AgentResolvedScope scope) {
        if (scope.scopeType() == AgentScopeType.DOCUMENT && scope.documentId() != null) {
            return archiveDocumentFactQueryService.listByDocumentId(scope.documentId());
        }
        String personName = extractPersonName(message);
        if (personName == null) {
            return List.of();
        }
        ArchiveFactSearchRequest request = new ArchiveFactSearchRequest();
        request.setHallId(scope.hallId());
        request.setFolderPath(scope.scopeType() == AgentScopeType.FOLDER ? scope.folderPath() : null);
        request.setFactType(ArchiveFactType.PERSON_NAME);
        request.setValue(personName);
        request.setLimit(20);
        List<Long> documentIds = archiveDocumentFactQueryService.search(request).stream()
                .map(ArchiveFactEvidence::archiveDocumentId)
                .distinct()
                .toList();
        return documentIds.stream()
                .flatMap(documentId -> archiveDocumentFactQueryService.listByDocumentId(documentId).stream())
                .toList();
    }

    private QueryResult courseAnswer(String courseName, List<ArchiveFactEvidence> facts) {
        List<ArchiveFactEvidence> matches = facts.stream()
                .filter(fact -> fact.factType() == ArchiveFactType.COURSE_GRADE)
                .filter(fact -> fact.factKey() != null && fact.factKey().contains(courseName))
                .sorted(Comparator.comparing(ArchiveFactEvidence::archiveTitle)
                        .thenComparing(ArchiveFactEvidence::pageNo))
                .toList();
        if (matches.isEmpty()) {
            return new QueryResult("已定位到候选档案，但在已索引页面中没有找到“%s”的课程成绩。不能据此推断为缺失或不及格，请人工核对原件。".formatted(courseName), List.of());
        }
        String answer = matches.stream()
                .map(fact -> "%s：%s（第 %d 页）".formatted(fact.archiveTitle(), fact.factValue(), fact.pageNo()))
                .reduce("已根据页级 OCR 证据找到“%s”成绩：".formatted(courseName), (left, right) -> left + right + "；");
        return new QueryResult(answer, matches);
    }

    private QueryResult firstFactAnswer(String label, List<ArchiveFactEvidence> facts, ArchiveFactType type) {
        List<ArchiveFactEvidence> matches = facts.stream()
                .filter(fact -> fact.factType() == type)
                .sorted(Comparator.comparing(ArchiveFactEvidence::archiveTitle)
                        .thenComparing(ArchiveFactEvidence::pageNo))
                .toList();
        if (matches.isEmpty()) {
            return new QueryResult("已定位到候选档案，但未提取到可核查的%s。", List.of());
        }
        ArchiveFactEvidence first = matches.getFirst();
        return new QueryResult("%s：%s（%s，第 %d 页）。".formatted(label, first.factValue(), first.archiveTitle(), first.pageNo()), matches);
    }

    private QueryResult overviewAnswer(String message, List<ArchiveFactEvidence> facts) {
        if (containsAny(message, "是谁", "什么人")) {
            return personOverviewAnswer(facts);
        }
        boolean includeGrades = containsAny(message, "成绩", "课程");
        List<ArchiveFactEvidence> selected = facts.stream()
                .filter(fact -> fact.factType() == ArchiveFactType.PERSON_NAME
                        || fact.factType() == ArchiveFactType.STUDENT_ID
                        || fact.factType() == ArchiveFactType.DEGREE_AWARD_DATE
                        || fact.factType() == ArchiveFactType.MATERIAL_TYPE
                        || (includeGrades && fact.factType() == ArchiveFactType.COURSE_GRADE))
                .limit(20)
                .toList();
        if (selected.isEmpty()) {
            return new QueryResult("已定位到档案，但当前只识别到材料类型，尚无可用于内容回答的字段。", facts.stream().limit(10).toList());
        }
        String answer = selected.stream()
                .map(fact -> "%s：%s（第 %d 页）".formatted(fact.factKey(), fact.factValue(), fact.pageNo()))
                .reduce("已从页级 OCR 中提取以下可核查信息：", (left, right) -> left + right + "；");
        return new QueryResult(answer, selected);
    }

    private QueryResult personOverviewAnswer(List<ArchiveFactEvidence> facts) {
        List<ArchiveFactEvidence> names = facts.stream().filter(fact -> fact.factType() == ArchiveFactType.PERSON_NAME).toList();
        List<ArchiveFactEvidence> studentIds = facts.stream().filter(fact -> fact.factType() == ArchiveFactType.STUDENT_ID).toList();
        List<ArchiveFactEvidence> degreeDates = facts.stream().filter(fact -> fact.factType() == ArchiveFactType.DEGREE_AWARD_DATE).toList();
        List<ArchiveFactEvidence> materials = facts.stream().filter(fact -> fact.factType() == ArchiveFactType.MATERIAL_TYPE).toList();
        List<ArchiveFactEvidence> evidence = new java.util.ArrayList<>();
        evidence.addAll(names.stream().limit(1).toList());
        evidence.addAll(studentIds.stream().limit(1).toList());
        evidence.addAll(degreeDates.stream().limit(1).toList());
        evidence.addAll(materials.stream().limit(5).toList());
        String name = names.isEmpty() ? "未识别" : names.getFirst().factValue();
        StringBuilder answer = new StringBuilder("当前档案中可确认该学生为“").append(name).append("”。");
        if (!studentIds.isEmpty()) {
            answer.append("学号：").append(studentIds.getFirst().factValue()).append("。");
        }
        List<String> materialNames = materials.stream().map(fact -> materialLabel(fact.factValue())).distinct().toList();
        if (!materialNames.isEmpty()) {
            answer.append("已识别材料：").append(String.join("、", materialNames)).append("。");
        }
        if (!degreeDates.isEmpty()) {
            answer.append("学位授予日期：").append(degreeDates.getFirst().factValue()).append("。");
        }
        return new QueryResult(answer.toString(), evidence);
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

    private String extractPersonName(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = PERSON_BEFORE_QUESTION.matcher(message);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!NON_NAME_TOKENS.contains(candidate)) {
                return candidate;
            }
        }
        matcher = PERSON_BEFORE_POSSESSIVE.matcher(message);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!NON_NAME_TOKENS.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String extractCourseName(String message) {
        if (message == null || !message.contains("成绩")) {
            return null;
        }
        Optional<String> afterPossessive = firstMatch(COURSE_AFTER_POSSESSIVE, message);
        if (afterPossessive.isPresent()) {
            return afterPossessive.get();
        }
        return firstMatch(COURSE_DIRECT, message)
                .map(value -> value.replaceFirst("^.*的", ""))
                .orElse(null);
    }

    private Optional<String> firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private boolean containsAny(String text, String... terms) {
        if (text == null) {
            return false;
        }
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}

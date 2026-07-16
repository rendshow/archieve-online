package com.danganguan.archive.document.fact.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.fact.entity.ArchiveExtractedFact;
import com.danganguan.archive.document.fact.enums.ArchiveFactType;
import com.danganguan.archive.document.fact.mapper.ArchiveExtractedFactMapper;
import com.danganguan.archive.document.fact.service.ArchiveDocumentFactExtractionService;
import com.danganguan.archive.document.page.entity.ArchiveDocumentPage;
import com.danganguan.archive.document.page.mapper.ArchiveDocumentPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ArchiveDocumentFactExtractionServiceImpl implements ArchiveDocumentFactExtractionService {
    private static final Pattern EXPLICIT_NAME_PATTERN = Pattern.compile(
            "(?:作者姓名|学生姓名|申请人姓名)\\s*[:：]\\s*([\\u4e00-\\u9fa5]{2,4})(?=\\s*(?:专业|性别|学号|所在|$))"
    );
    private static final Pattern STUDENT_NAME_PATTERN = Pattern.compile(
            "(?<!教师)姓名\\s*[:：]\\s*([\\u4e00-\\u9fa5]{2,4})(?=\\s*(?:专业|性别|学号|$))"
    );
    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("学号\\s*[:：]\\s*(\\d{6,})");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})年\\s*(\\d{1,2})月\\s*(\\d{1,2})日");
    private static final Pattern GRADE_PATTERN = Pattern.compile("^(100|[1-9]?\\d|合格|不合格)$");
    private static final Set<String> COURSE_HEADERS = Set.of("课程", "课程名称", "成绩", "学时", "学分", "备注", "类别", "学位", "选修", "合格", "不合格");

    private final ArchiveExtractedFactMapper factMapper;
    private final ArchiveDocumentPageMapper pageMapper;

    @Override
    @Transactional
    public int rebuild(ArchiveDocument document) {
        factMapper.delete(new LambdaQueryWrapper<ArchiveExtractedFact>()
                .eq(ArchiveExtractedFact::getArchiveDocumentId, document.getId()));
        List<ArchiveDocumentPage> pages = pageMapper.selectList(new LambdaQueryWrapper<ArchiveDocumentPage>()
                .eq(ArchiveDocumentPage::getArchiveDocumentId, document.getId())
                .orderByAsc(ArchiveDocumentPage::getPageNo));
        List<FactCandidate> candidates = new ArrayList<>();
        for (ArchiveDocumentPage page : pages) {
            candidates.addAll(extract(page));
        }
        LocalDateTime now = LocalDateTime.now();
        Set<String> seen = new HashSet<>();
        for (FactCandidate candidate : candidates) {
            String uniqueKey = candidate.pageId() + "|" + candidate.type() + "|" + candidate.key() + "|" + candidate.value();
            if (!seen.add(uniqueKey)) {
                continue;
            }
            ArchiveExtractedFact fact = new ArchiveExtractedFact();
            fact.setArchiveDocumentId(document.getId());
            fact.setArchiveDocumentPageId(candidate.pageId());
            fact.setFactType(candidate.type());
            fact.setFactKey(candidate.key());
            fact.setFactValue(candidate.value());
            fact.setNormalizedValue(candidate.normalizedValue());
            fact.setConfidence(candidate.confidence());
            fact.setEvidenceText(limit(candidate.evidence(), 1000));
            fact.setCreatedAt(now);
            fact.setUpdatedAt(now);
            factMapper.insert(fact);
        }
        return seen.size();
    }

    private List<FactCandidate> extract(ArchiveDocumentPage page) {
        String text = page.getOcrText() == null ? "" : page.getOcrText();
        if (text.isBlank()) {
            return List.of();
        }
        List<FactCandidate> facts = new ArrayList<>();
        addMaterialFacts(facts, page, text);
        addMatcherFacts(facts, page, text, EXPLICIT_NAME_PATTERN, ArchiveFactType.PERSON_NAME, "姓名", new BigDecimal("0.95"));
        if (text.contains("学号")) {
            addMatcherFacts(facts, page, text, STUDENT_NAME_PATTERN, ArchiveFactType.PERSON_NAME, "姓名", new BigDecimal("0.90"));
        }
        addMatcherFacts(facts, page, text, STUDENT_ID_PATTERN, ArchiveFactType.STUDENT_ID, "学号", new BigDecimal("0.95"));
        if (isDegreeAwardPage(text)) {
            addDegreeDates(facts, page, text);
        }
        if (text.contains("成绩单")) {
            addCourseGrades(facts, page, text);
        }
        return facts;
    }

    private void addMaterialFacts(List<FactCandidate> facts, ArchiveDocumentPage page, String text) {
        addMaterialFact(facts, page, text, "成绩单", "TRANSCRIPT");
        addMaterialFact(facts, page, text, "毕业鉴定", "GRADUATION_APPRAISAL");
        addMaterialFact(facts, page, text, "评阅", "REVIEW_FORM");
        addMaterialFact(facts, page, text, "学籍", "STUDENT_STATUS_FORM");
        if (isDegreeAwardPage(text)) {
            addMaterialFact(facts, page, text, "学位授予决定", "DEGREE_AWARD_DECISION");
            facts.add(new FactCandidate(page.getId(), ArchiveFactType.MATERIAL_TYPE, "材料类型", "DEGREE_AWARD_DECISION",
                    "DEGREE_AWARD_DECISION", new BigDecimal("0.90"), "学位评定委员会授予学位"));
        }
    }

    private void addMaterialFact(List<FactCandidate> facts, ArchiveDocumentPage page, String text, String keyword, String value) {
        if (text.contains(keyword)) {
            facts.add(new FactCandidate(page.getId(), ArchiveFactType.MATERIAL_TYPE, "材料类型", value, value,
                    new BigDecimal("0.95"), keyword));
        }
    }

    private void addMatcherFacts(List<FactCandidate> facts, ArchiveDocumentPage page, String text, Pattern pattern,
                                 ArchiveFactType type, String key, BigDecimal confidence) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1);
            facts.add(new FactCandidate(page.getId(), type, key, value, value, confidence, matcher.group()));
        }
    }

    private void addDegreeDates(List<FactCandidate> facts, ArchiveDocumentPage page, String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            String normalized = "%s-%02d-%02d".formatted(
                    matcher.group(1),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
            facts.add(new FactCandidate(page.getId(), ArchiveFactType.DEGREE_AWARD_DATE, "学位授予日期",
                    normalized, normalized, new BigDecimal("0.90"), matcher.group()));
        }
    }

    private void addCourseGrades(List<FactCandidate> facts, ArchiveDocumentPage page, String text) {
        String[] lines = text.split("\\R");
        for (int index = 0; index + 1 < lines.length; index++) {
            String course = normalizeLine(lines[index]);
            String grade = normalizeLine(lines[index + 1]);
            if (!isCourseName(course) || !GRADE_PATTERN.matcher(grade).matches()) {
                continue;
            }
            facts.add(new FactCandidate(page.getId(), ArchiveFactType.COURSE_GRADE, course, grade, grade,
                    new BigDecimal("0.75"), course + " " + grade));
        }
    }

    private boolean isDegreeAwardPage(String text) {
        return text.contains("授予") && text.contains("学位") || text.contains("学位评定委员会");
    }

    private boolean isCourseName(String value) {
        if (value.length() < 2 || value.length() > 40 || COURSE_HEADERS.contains(value)) {
            return false;
        }
        return value.matches("[A-Za-z\\u4e00-\\u9fa5][A-Za-z\\u4e00-\\u9fa5 ]+");
    }

    private String normalizeLine(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record FactCandidate(Long pageId, ArchiveFactType type, String key, String value, String normalizedValue,
                                 BigDecimal confidence, String evidence) {
    }
}

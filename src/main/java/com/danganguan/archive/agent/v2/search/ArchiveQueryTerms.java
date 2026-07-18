package com.danganguan.archive.agent.v2.search;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts stable retrieval clues from a natural-language archive request. */
public record ArchiveQueryTerms(
        String personName,
        String studentId,
        String archiveNo,
        String materialType,
        String courseName,
        List<String> pageQueries
) {
    private static final Pattern STUDENT_ID = Pattern.compile("(?<!\\d)(\\d{6,})(?!\\d)");
    private static final Pattern ARCHIVE_NO = Pattern.compile("(\\d{4}-[A-Za-z]{1,6}\\d{1,4}[•.．·\\-]\\d{1,4}[•.．·\\-]\\d{1,6}(?:-\\d+)?[\\u4e00-\\u9fa5]{0,4})");
    private static final Pattern POSSESSIVE_NAME = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})的");
    private static final Pattern CALLED_NAME = Pattern.compile("(?:叫|姓名为|姓名是)([\\u4e00-\\u9fa5]{2,4})");
    private static final Pattern COURSE = Pattern.compile("的([\\u4e00-\\u9fa5]{2,20})成绩");
    private static final List<String> NON_NAMES = List.of("当前文件", "当前目录", "这个文件", "这份档案", "学生姓名", "高等数学", "档案信息", "当前范围");
    private static final List<String> STOP_WORDS = List.of("帮我", "请", "一下", "找", "查", "搜索", "定位", "有没有", "是否", "相关", "档案", "文件", "学生", "信息", "内容", "当前文件夹", "当前目录");

    public static ArchiveQueryTerms parse(String message) {
        String text = message == null ? "" : message.trim();
        String studentId = first(STUDENT_ID, text);
        String archiveNo = first(ARCHIVE_NO, text);
        String personName = firstName(text);
        String materialType = materialType(text);
        String courseName = courseName(text);
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        add(queries, studentId);
        add(queries, archiveNo);
        String materialLabel = materialLabel(materialType);
        if (personName != null && materialLabel != null) {
            add(queries, personName + " " + materialLabel);
        } else {
            add(queries, personName);
            add(queries, materialLabel);
        }
        if (personName == null || materialLabel == null) add(queries, courseName);
        String residual = text;
        for (String value : new String[]{studentId, archiveNo, personName, materialLabel(materialType), courseName}) {
            if (value != null) residual = residual.replace(value, " ");
        }
        for (String stopWord : STOP_WORDS) residual = residual.replace(stopWord, " ");
        residual = residual.replaceAll("[？?，,。！!；;：:的了么吗在和与]", " ").trim();
        if (residual.length() >= 2 && residual.length() <= 24) add(queries, residual);
        return new ArchiveQueryTerms(personName, studentId, archiveNo, materialType, courseName, List.copyOf(queries));
    }

    public boolean hasLocateClue() {
        return personName != null || studentId != null || archiveNo != null || materialType != null;
    }

    private static String firstName(String text) {
        String normalized = text.replaceFirst("^(?:帮我)?(?:找一下|查一下|找|查|搜索|定位)", "");
        for (Pattern pattern : List.of(POSSESSIVE_NAME, CALLED_NAME)) {
            Matcher matcher = pattern.matcher(normalized);
            while (matcher.find()) {
                String candidate = matcher.group(1).replaceFirst("^(帮我|请|我)?(找|查|搜索|定位)", "");
                if (!NON_NAMES.contains(candidate)) return candidate;
            }
        }
        return null;
    }

    private static String materialType(String text) {
        if (text.contains("成绩单") || text.contains("成绩")) return "TRANSCRIPT";
        if (text.contains("学籍")) return "STUDENT_STATUS_FORM";
        if (text.contains("毕业鉴定")) return "GRADUATION_APPRAISAL";
        if (text.contains("评阅")) return "REVIEW_FORM";
        if (text.contains("学位")) return "DEGREE_AWARD_DECISION";
        return null;
    }

    private static String materialLabel(String materialType) {
        if (materialType == null) return null;
        return switch (materialType) {
            case "TRANSCRIPT" -> "成绩单";
            case "STUDENT_STATUS_FORM" -> "学籍";
            case "GRADUATION_APPRAISAL" -> "毕业鉴定";
            case "REVIEW_FORM" -> "评阅";
            case "DEGREE_AWARD_DECISION" -> "学位";
            default -> null;
        };
    }

    private static String courseName(String text) {
        Matcher matcher = COURSE.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void add(LinkedHashSet<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value.trim());
    }
}

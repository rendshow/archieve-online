package com.danganguan.archive.ai.naming.impl;

import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.danganguan.archive.ai.dto.AiNamingRequest;
import com.danganguan.archive.ai.dto.AiNamingResult;
import com.danganguan.archive.ai.service.AiNamingService;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadType;
import com.danganguan.archive.task.entity.ArchiveTask;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuleBasedAiNamingService implements AiNamingService {
    private static final Pattern NUMBER_NAME_TAIL = Pattern.compile("^(.*?)(\\d+)([\\u4e00-\\u9fa5]{2,4})$");
    private static final Pattern STUDENT_NO_NAME_TAIL = Pattern.compile("^(.*?)([A-Za-z]?\\d{6,20})([-_•·.．\\s]+)([\\u4e00-\\u9fa5]{2,4})$");
    private static final Pattern STUDENT_NO = Pattern.compile("(?:学号|考号|准考证号|学生证号)[:：\\s]*([A-Za-z]?\\d{6,20})");
    private static final Pattern CHINESE_NAME_TAIL = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})$");

    @Override
    public AiNamingResult name(AiNamingRequest request) {
        ArchiveTask task = request.task();
        UploadedFile file = request.file();
        DocumentAnalyzeResult analyzeResult = request.analyzeResult();
        String suggestedName = suggestName(task, file, analyzeResult, request.sequenceNo());
        String folderName = task.getFolderNameExample() == null || task.getFolderNameExample().isBlank()
                ? "未分类档案"
                : task.getFolderNameExample();
        String reason = analyzeResult == null
                ? "规则命名：参考任务命名示例和原始文件名生成。"
                : "规则命名：参考任务命名示例、原始文件名和本地文档分析结果生成。分析依据：" + analyzeResult.reason();
        String summary = analyzeResult == null || analyzeResult.summary() == null || analyzeResult.summary().isBlank()
                ? "由原始文件 " + file.getOriginalName() + " 生成的工作区档案。"
                : analyzeResult.summary();
        return new AiNamingResult(suggestedName, folderName, summary, reason);
    }

    private String suggestName(ArchiveTask task, UploadedFile file, DocumentAnalyzeResult analyzeResult, Integer sequenceNo) {
        NamingConvention sourceConvention = parseNumberNameConvention(stripExt(file.getOriginalName()));
        if (sourceConvention != null) {
            return safeName(sourceConvention.fullName());
        }

        String detectedName = analyzeResult == null ? null : analyzeResult.detectedPersonName();
        String sourceName = file.getUploadType() == UploadType.ZIP ? null : parseChineseName(stripExt(file.getOriginalName()));
        String personName = firstNonBlank(detectedName, sourceName, fallbackPersonName(file));
        String studentNo = detectStudentNo(analyzeResult);
        String placeholderName = applyPlaceholderTemplate(stripExt(task.getFileNameExample()), personName, studentNo);
        if (placeholderName != null) {
            return safeName(placeholderName);
        }
        StudentNoConvention studentNoConvention = parseStudentNoConvention(stripExt(task.getFileNameExample()));
        if (studentNoConvention != null) {
            return safeName(studentNoConvention.prefix()
                    + firstNonBlank(studentNo, studentNoConvention.studentNo())
                    + studentNoConvention.separator()
                    + personName);
        }
        NamingConvention exampleConvention = parseNumberNameConvention(stripExt(task.getFileNameExample()));
        if (exampleConvention != null) {
            return safeName(exampleConvention.prefix() + formatSequence(sequenceNo, exampleConvention.numberWidth()) + personName);
        }

        String example = firstNonBlank(stripExt(task.getFileNameExample()), task.getFolderNameExample());
        if (example != null) {
            return safeName(example + "-" + personName);
        }
        return safeName(LocalDate.now().getYear() + "-档案-" + personName);
    }

    private NamingConvention parseNumberNameConvention(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        Matcher matcher = NUMBER_NAME_TAIL.matcher(filename.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new NamingConvention(matcher.group(1), matcher.group(2).length(), matcher.group(0));
    }

    private StudentNoConvention parseStudentNoConvention(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        Matcher matcher = STUDENT_NO_NAME_TAIL.matcher(filename.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new StudentNoConvention(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    private String applyPlaceholderTemplate(String example, String personName, String studentNo) {
        if (example == null || example.isBlank()) {
            return null;
        }
        String template = example.trim();
        boolean hasStudentNoPlaceholder = template.contains("学号");
        boolean hasPersonNamePlaceholder = template.contains("姓名");
        if (!hasStudentNoPlaceholder && !hasPersonNamePlaceholder) {
            return null;
        }
        if (hasStudentNoPlaceholder) {
            template = template.replace("学号", firstNonBlank(studentNo, "待识别学号"));
        }
        if (hasPersonNamePlaceholder) {
            template = template.replace("姓名", personName);
        }
        return template;
    }

    private String detectStudentNo(DocumentAnalyzeResult analyzeResult) {
        if (analyzeResult == null || analyzeResult.extractedText() == null || analyzeResult.extractedText().isBlank()) {
            return null;
        }
        Matcher matcher = STUDENT_NO.matcher(analyzeResult.extractedText().replaceAll("\\s+", ""));
        return matcher.find() ? matcher.group(1) : null;
    }

    private String parseChineseName(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        Matcher matcher = CHINESE_NAME_TAIL.matcher(filename.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String fallbackPersonName(UploadedFile file) {
        if (file.getUploadType() == UploadType.ZIP) {
            return "待识别姓名";
        }
        return stripExt(file.getOriginalName());
    }

    private String formatSequence(Integer sequenceNo, int width) {
        int safeSequence = sequenceNo == null || sequenceNo < 1 ? 1 : sequenceNo;
        return width <= 1 ? String.valueOf(safeSequence) : String.format("%0" + width + "d", safeSequence);
    }

    private String stripExt(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String safeName(String name) {
        String safe = name == null || name.isBlank() ? "档案" : name;
        return safe.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private record NamingConvention(String prefix, int numberWidth, String fullName) {
    }

    private record StudentNoConvention(String prefix, String studentNo, String separator) {
    }
}

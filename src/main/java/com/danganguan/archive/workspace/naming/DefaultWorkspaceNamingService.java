package com.danganguan.archive.workspace.naming;

import com.danganguan.archive.ai.dto.AiNamingResult;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.task.entity.ArchiveTask;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DefaultWorkspaceNamingService {
    private static final Pattern NUMBER_NAME_TAIL = Pattern.compile("^(.*?)(\\d+)([\\u4e00-\\u9fa5]{2,4})$");
    private static final Pattern CHINESE_NAME_TAIL = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})$");

    public AiNamingResult name(ArchiveTask task, UploadedFile file, int sequenceNo) {
        String sourceName = stripExt(file.getOriginalName());
        String nameToken = firstNonBlank(parseChineseName(sourceName), sourceName, "未命名档案");
        NamingConvention convention = parseNumberNameConvention(stripExt(task.getFileNameExample()));
        String suggestedName = convention == null
                ? defaultName(sourceName, sequenceNo)
                : convention.prefix() + formatSequence(sequenceNo, convention.numberWidth()) + nameToken;
        String folderName = firstNonBlank(task.getFolderNameExample(), "未分类档案");
        String summary = "未开启 AI 命名，按上传文件名和序号生成默认工作区档案。";
        String reason = "未开启 AI 命名干预，跳过 OCR/视觉分析和 AI 标签。";
        return new AiNamingResult(safeName(suggestedName), folderName, summary, reason);
    }

    private String defaultName(String sourceName, int sequenceNo) {
        String safeSourceName = firstNonBlank(sourceName, "未命名档案");
        return safeSourceName + "-" + formatSequence(sequenceNo, 1);
    }

    private NamingConvention parseNumberNameConvention(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        Matcher matcher = NUMBER_NAME_TAIL.matcher(filename.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new NamingConvention(matcher.group(1), matcher.group(2).length());
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

    private record NamingConvention(String prefix, int numberWidth) {
    }
}

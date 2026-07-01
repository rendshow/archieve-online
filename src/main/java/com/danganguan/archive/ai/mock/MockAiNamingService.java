package com.danganguan.archive.ai.mock;

import com.danganguan.archive.ai.dto.AiNamingRequest;
import com.danganguan.archive.ai.dto.AiNamingResult;
import com.danganguan.archive.ai.service.AiNamingService;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.task.entity.ArchiveTask;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class MockAiNamingService implements AiNamingService {

    @Override
    public AiNamingResult name(AiNamingRequest request) {
        ArchiveTask task = request.task();
        UploadedFile file = request.file();
        String baseName = stripExt(file.getOriginalName()).replaceAll("[\\\\/:*?\"<>|]", "_");
        String example = firstNonBlank(task.getFileNameExample(), task.getFolderNameExample());
        String prefix = example == null ? "档案" : example;
        String suggestedName = LocalDate.now().getYear() + "-" + prefix + "-" + baseName;
        String folderName = task.getFolderNameExample() == null || task.getFolderNameExample().isBlank()
                ? "未分类档案"
                : task.getFolderNameExample();
        String reason = "MVP mock：参考任务命名示例和原始文件名生成，后续替换为 OCR/视觉模型。";
        String summary = "由原始文件 " + file.getOriginalName() + " 生成的工作区档案。";
        return new AiNamingResult(suggestedName, folderName, summary, reason);
    }

    private String stripExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}

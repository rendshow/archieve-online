package com.danganguan.archive.ai.mock;

import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.danganguan.archive.ai.dto.AiNamingRequest;
import com.danganguan.archive.ai.dto.AiNamingResult;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.task.entity.ArchiveTask;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MockAiNamingServiceTest {
    private final MockAiNamingService namingService = new MockAiNamingService();

    @Test
    void shouldFollowNumberNameExampleWhenPersonNameDetected() {
        ArchiveTask task = taskWithExample("N2007-JX12•11•21-1韩学敏.pdf");
        UploadedFile file = uploadedFile("scan.pdf");
        DocumentAnalyzeResult analyzeResult = analyzeResult("尚宇");

        AiNamingResult result = namingService.name(new AiNamingRequest(task, file, analyzeResult, 14));

        assertEquals("N2007-JX12•11•21-14尚宇", result.suggestedName());
    }

    @Test
    void shouldPreserveOriginalNameWhenItAlreadyMatchesConvention() {
        ArchiveTask task = taskWithExample("N2007-JX12•11•21-1韩学敏.pdf");
        UploadedFile file = uploadedFile("N2007-JX12•11•21-14尚宇.pdf");
        DocumentAnalyzeResult analyzeResult = analyzeResult(null);

        AiNamingResult result = namingService.name(new AiNamingRequest(task, file, analyzeResult, 1));

        assertEquals("N2007-JX12•11•21-14尚宇", result.suggestedName());
    }

    private ArchiveTask taskWithExample(String example) {
        ArchiveTask task = new ArchiveTask();
        task.setFileNameExample(example);
        task.setFolderNameExample("N2007-JX12•11•21");
        return task;
    }

    private UploadedFile uploadedFile(String originalName) {
        UploadedFile file = new UploadedFile();
        file.setOriginalName(originalName);
        return file;
    }

    private DocumentAnalyzeResult analyzeResult(String personName) {
        return new DocumentAnalyzeResult(
                "姓名：" + (personName == null ? "" : personName),
                "测试摘要",
                personName,
                List.of("测试"),
                BigDecimal.ONE,
                "测试"
        );
    }
}

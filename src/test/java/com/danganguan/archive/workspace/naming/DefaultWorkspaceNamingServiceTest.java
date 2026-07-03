package com.danganguan.archive.workspace.naming;

import com.danganguan.archive.ai.dto.AiNamingResult;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.task.entity.ArchiveTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultWorkspaceNamingServiceTest {
    private final DefaultWorkspaceNamingService namingService = new DefaultWorkspaceNamingService();

    @Test
    void shouldFollowExamplePrefixAndSequenceWithoutAi() {
        ArchiveTask task = task("N2007-JX12•11•21-1韩学敏.pdf");
        UploadedFile file = file("尚宇.zip");

        AiNamingResult result = namingService.name(task, file, 14);

        assertEquals("N2007-JX12•11•21-14尚宇", result.suggestedName());
        assertTrue(result.reason().contains("跳过 OCR"));
    }

    @Test
    void shouldUseSourceNameWhenNoExampleExists() {
        ArchiveTask task = task(null);
        UploadedFile file = file("第一批材料.zip");

        AiNamingResult result = namingService.name(task, file, 2);

        assertEquals("第一批材料-2", result.suggestedName());
    }

    private ArchiveTask task(String fileNameExample) {
        ArchiveTask task = new ArchiveTask();
        task.setFileNameExample(fileNameExample);
        task.setFolderNameExample("N2007-JX12•11•21");
        return task;
    }

    private UploadedFile file(String originalName) {
        UploadedFile file = new UploadedFile();
        file.setOriginalName(originalName);
        return file;
    }
}

package com.danganguan.archive.ai.dto;

import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.task.entity.ArchiveTask;

public record AiNamingRequest(ArchiveTask task, UploadedFile file) {
}

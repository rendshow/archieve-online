package com.danganguan.archive.document.process;

import com.danganguan.archive.task.entity.ArchiveTask;

import java.nio.file.Path;

public interface ImageEnhanceService {
    Path enhance(ArchiveTask task, Path imagePath);
}

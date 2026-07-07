package com.danganguan.archive.task.processing;

import java.util.List;

public record TaskProcessMessage(Long taskId, List<Long> fileIds) {
    public TaskProcessMessage(Long taskId) {
        this(taskId, List.of());
    }

    public List<Long> fileIds() {
        return fileIds == null ? List.of() : fileIds;
    }
}

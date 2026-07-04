package com.danganguan.archive.document.process.impl;

import com.danganguan.archive.document.process.ImageEnhanceService;
import com.danganguan.archive.task.entity.ArchiveTask;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
@ConditionalOnProperty(prefix = "archive.image-enhance", name = "provider", havingValue = "noop", matchIfMissing = true)
public class NoopImageEnhanceServiceImpl implements ImageEnhanceService {
    @Override
    public Path enhance(ArchiveTask task, Path imagePath) {
        return imagePath;
    }
}

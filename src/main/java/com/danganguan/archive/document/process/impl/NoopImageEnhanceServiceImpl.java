package com.danganguan.archive.document.process.impl;

import com.danganguan.archive.document.process.ImageEnhanceService;
import com.danganguan.archive.task.entity.ArchiveTask;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class NoopImageEnhanceServiceImpl implements ImageEnhanceService {
    @Override
    public Path enhance(ArchiveTask task, Path imagePath) {
        return imagePath;
    }
}

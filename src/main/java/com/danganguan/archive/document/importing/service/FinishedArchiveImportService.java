package com.danganguan.archive.document.importing.service;

import com.danganguan.archive.document.importing.dto.FinishedArchiveImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FinishedArchiveImportService {
    FinishedArchiveImportResult importFinishedArchives(Long hallId, List<MultipartFile> files);
}

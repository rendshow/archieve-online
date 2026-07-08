package com.danganguan.archive.document.importing.service;

import com.danganguan.archive.document.importing.dto.FinishedArchiveImportResult;
import com.danganguan.archive.document.importing.dto.FinishedArchiveChunkUploadResult;
import com.danganguan.archive.document.importing.dto.FinishedArchiveChunkedCompleteRequest;
import com.danganguan.archive.document.importing.entity.FinishedArchiveImportJob;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FinishedArchiveImportService {
    FinishedArchiveImportResult importFinishedArchives(Long hallId, List<MultipartFile> files);

    FinishedArchiveImportJob createImportJob(Long hallId, List<MultipartFile> files);

    FinishedArchiveImportJob createChunkedImportJob(Long hallId);

    FinishedArchiveChunkUploadResult uploadChunk(Long jobId, Integer fileIndex, Integer chunkIndex,
                                                 Integer totalChunks, MultipartFile chunk);

    FinishedArchiveImportJob completeChunkedImportJob(Long jobId, FinishedArchiveChunkedCompleteRequest request);

    FinishedArchiveImportJob getImportJob(Long jobId);

    void processJob(Long jobId);
}

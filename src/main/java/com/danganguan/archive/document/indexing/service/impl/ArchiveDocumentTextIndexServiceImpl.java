package com.danganguan.archive.document.indexing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeRequest;
import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.danganguan.archive.ai.analysis.service.DocumentAnalyzeService;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.indexing.dto.ArchiveDocumentTextIndexResult;
import com.danganguan.archive.document.indexing.service.ArchiveDocumentTextIndexService;
import com.danganguan.archive.document.process.ProcessedFileResult;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArchiveDocumentTextIndexServiceImpl implements ArchiveDocumentTextIndexService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 200;

    private final ArchiveDocumentService archiveDocumentService;
    private final DocumentAnalyzeService documentAnalyzeService;

    @Override
    public ArchiveDocument indexOne(Long documentId) {
        ArchiveDocument document = archiveDocumentService.getById(documentId);
        if (document == null) {
            throw new BizException("正式档案不存在");
        }
        DocumentAnalyzeResult result = analyze(document);
        document.setOcrText(blankToNull(result.extractedText()));
        document.setAiSummary(firstNonBlank(result.summary(), document.getAiSummary()));
        document.setUpdatedAt(LocalDateTime.now());
        archiveDocumentService.updateById(document);
        return document;
    }

    @Override
    public ArchiveDocumentTextIndexResult indexMissing(Long hallId, int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        LambdaQueryWrapper<ArchiveDocument> wrapper = new LambdaQueryWrapper<ArchiveDocument>()
                .eq(ArchiveDocument::getStatus, ArchiveDocumentStatus.ACTIVE)
                .eq(hallId != null, ArchiveDocument::getHallId, hallId)
                .and(inner -> inner
                        .isNull(ArchiveDocument::getOcrText)
                        .or()
                        .eq(ArchiveDocument::getOcrText, ""))
                .orderByAsc(ArchiveDocument::getArchivedAt)
                .last("LIMIT " + safeLimit);
        List<ArchiveDocument> documents = archiveDocumentService.list(wrapper);
        int indexed = 0;
        int failed = 0;
        for (ArchiveDocument document : documents) {
            try {
                indexOne(document.getId());
                indexed++;
            } catch (RuntimeException ex) {
                failed++;
            }
        }
        return new ArchiveDocumentTextIndexResult(documents.size(), indexed, 0, failed);
    }

    private DocumentAnalyzeResult analyze(ArchiveDocument document) {
        ProcessedFileResult processedFile = new ProcessedFileResult(
                document.getStoragePath(),
                document.getFileFormat(),
                document.getPageCount(),
                ""
        );
        return documentAnalyzeService.analyze(new DocumentAnalyzeRequest(null, List.of(), processedFile));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}

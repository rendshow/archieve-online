package com.danganguan.archive.document.indexing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danganguan.archive.common.config.ArchiveStorageProperties;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.enums.ArchiveDocumentStatus;
import com.danganguan.archive.document.fact.service.ArchiveDocumentFactExtractionService;
import com.danganguan.archive.document.indexing.ArchiveTextIndexMessage;
import com.danganguan.archive.document.indexing.dto.CreateArchiveTextIndexJobRequest;
import com.danganguan.archive.document.indexing.dto.ArchiveDocumentTextIndexResult;
import com.danganguan.archive.document.indexing.entity.ArchiveTextIndexJob;
import com.danganguan.archive.document.indexing.enums.ArchiveTextIndexJobStatus;
import com.danganguan.archive.document.indexing.enums.ArchiveTextIndexMode;
import com.danganguan.archive.document.indexing.mapper.ArchiveTextIndexJobMapper;
import com.danganguan.archive.document.indexing.service.ArchiveDocumentTextIndexService;
import com.danganguan.archive.document.indexing.service.ArchiveDocumentIndexStateService;
import com.danganguan.archive.document.indexing.enums.ArchiveDocumentIndexStatus;
import com.danganguan.archive.document.page.dto.ArchiveDocumentPageIndexResult;
import com.danganguan.archive.document.page.service.ArchiveDocumentPageIndexService;
import com.danganguan.archive.document.service.ArchiveDocumentService;
import com.danganguan.archive.event.ArchiveRealtimeEvent;
import com.danganguan.archive.event.ArchiveRealtimeEventPublisher;
import com.danganguan.archive.search.service.ArchivePageSearchService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArchiveDocumentTextIndexServiceImpl
        extends ServiceImpl<ArchiveTextIndexJobMapper, ArchiveTextIndexJob>
        implements ArchiveDocumentTextIndexService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 200;
    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final int MAX_BATCH_SIZE = 50;

    private final ArchiveDocumentService archiveDocumentService;
    private final ArchiveDocumentPageIndexService archiveDocumentPageIndexService;
    private final ArchiveDocumentFactExtractionService archiveDocumentFactExtractionService;
    private final ArchiveStorageProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final ArchiveRealtimeEventPublisher eventPublisher;
    private final ArchivePageSearchService archivePageSearchService;
    private final ArchiveDocumentIndexStateService archiveDocumentIndexStateService;
    private final ObjectMapper objectMapper;

    @Override
    public ArchiveDocument indexOne(Long documentId) {
        ArchiveDocument document = archiveDocumentService.getById(documentId);
        if (document == null) {
            throw new BizException("正式档案不存在");
        }
        try {
            archiveDocumentIndexStateService.mark(documentId, ArchiveDocumentIndexStatus.OCR_RUNNING, null);
            ArchiveDocumentPageIndexResult result = archiveDocumentPageIndexService.rebuild(document);
            archiveDocumentIndexStateService.mark(documentId, ArchiveDocumentIndexStatus.EXTRACTING, null);
            archiveDocumentFactExtractionService.rebuild(document);
            document.setOcrText(blankToNull(result.mergedText()));
            document.setPageCount(result.pageCount());
            document.setAiSummary(firstNonBlank(document.getAiSummary(), "已完成页级 OCR 索引。"));
            document.setUpdatedAt(LocalDateTime.now());
            archiveDocumentService.updateById(document);
            archiveDocumentIndexStateService.mark(documentId, ArchiveDocumentIndexStatus.SEARCH_SYNCING, null);
            archivePageSearchService.syncDocument(document);
            archiveDocumentIndexStateService.mark(documentId, ArchiveDocumentIndexStatus.READY, null);
        } catch (RuntimeException ex) {
            archiveDocumentIndexStateService.mark(documentId, ArchiveDocumentIndexStatus.FAILED, ex.getMessage());
            throw ex;
        }
        eventPublisher.publish(ArchiveRealtimeEvent.archiveDocumentIndexed(
                document.getHallId(),
                document.getId(),
                "正式档案文本索引已更新"
        ));
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

    @Override
    public ArchiveTextIndexJob createJob(CreateArchiveTextIndexJobRequest request) {
        int batchSize = normalizeBatchSize(request == null ? null : request.batchSize());
        Long hallId = request == null ? null : request.hallId();
        ArchiveTextIndexMode mode = request == null || request.mode() == null ? ArchiveTextIndexMode.MISSING : request.mode();
        List<Long> documentIds = normalizeDocumentIds(request == null ? null : request.documentIds());
        int total = countCandidates(hallId, mode, documentIds);
        LocalDateTime now = LocalDateTime.now();
        ArchiveTextIndexJob job = new ArchiveTextIndexJob();
        job.setHallId(hallId);
        job.setMode(mode);
        job.setDocumentIdsJson(writeDocumentIds(documentIds));
        job.setStatus(ArchiveTextIndexJobStatus.PENDING);
        job.setBatchSize(batchSize);
        job.setTotalCount(total);
        job.setProcessedCount(0);
        job.setSuccessCount(0);
        job.setSkippedCount(0);
        job.setFailedCount(0);
        job.setErrorMessage(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        save(job);
        publish(job, "索引任务已创建（" + mode + "），共 " + total + " 份待处理");
        if (total == 0) {
            complete(job, "没有缺失 OCR 的正式档案");
            return job;
        }
        submit(job.getId());
        return job;
    }

    @Override
    public ArchiveTextIndexJob getJob(Long jobId) {
        ArchiveTextIndexJob job = getById(jobId);
        if (job == null) {
            throw new BizException("文本索引任务不存在");
        }
        return job;
    }

    @Override
    public void processJobBatch(Long jobId) {
        ArchiveTextIndexJob job = getJob(jobId);
        try {
            processJobBatchInternal(job);
        } catch (RuntimeException ex) {
            fail(job, ex.getMessage());
            throw ex;
        }
    }

    private void processJobBatchInternal(ArchiveTextIndexJob job) {
        if (job.getStatus() == ArchiveTextIndexJobStatus.COMPLETED
                || job.getStatus() == ArchiveTextIndexJobStatus.FAILED) {
            return;
        }
        if (job.getStatus() == ArchiveTextIndexJobStatus.PENDING) {
            job.setStatus(ArchiveTextIndexJobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now());
        }
        List<ArchiveDocument> documents = loadCandidates(job, value(job.getBatchSize(), DEFAULT_BATCH_SIZE));
        if (documents.isEmpty()) {
            complete(job, "文本索引任务完成");
            return;
        }
        int success = 0;
        int failed = 0;
        List<Long> indexedDocumentIds = new ArrayList<>();
        for (ArchiveDocument document : documents) {
            try {
                ArchiveTextIndexMode mode = job.getMode() == null ? ArchiveTextIndexMode.MISSING : job.getMode();
                if (mode == ArchiveTextIndexMode.SEARCH_ONLY) {
                    if (!hasOcrText(document)) {
                        throw new BizException("档案尚无页级 OCR，不能仅同步搜索索引");
                    }
                    archiveDocumentIndexStateService.mark(document.getId(), ArchiveDocumentIndexStatus.SEARCH_SYNCING, null);
                    archivePageSearchService.syncDocument(document);
                    archiveDocumentIndexStateService.mark(document.getId(), ArchiveDocumentIndexStatus.READY, null);
                } else {
                    indexOne(document.getId());
                }
                indexedDocumentIds.add(document.getId());
                success++;
            } catch (RuntimeException ex) {
                failed++;
                job.setErrorMessage(limit(ex.getMessage(), 1000));
            }
        }
        int processed = documents.size();
        job.setProcessedCount(value(job.getProcessedCount(), 0) + processed);
        job.setSuccessCount(value(job.getSuccessCount(), 0) + success);
        job.setFailedCount(value(job.getFailedCount(), 0) + failed);
        job.setSkippedCount(value(job.getSkippedCount(), 0) + Math.max(0, processed - success - failed));
        job.setUpdatedAt(LocalDateTime.now());
        updateById(job);
        publish(job, "文本索引进度：" + value(job.getProcessedCount(), 0) + "/" + value(job.getTotalCount(), 0));
        if (!indexedDocumentIds.isEmpty()) {
            eventPublisher.publish(ArchiveRealtimeEvent.agentKnowledgeChanged(
                    job.getHallId(),
                    indexedDocumentIds,
                    "Agent 可检索正文已更新"
            ));
        }

        if (value(job.getProcessedCount(), 0) >= value(job.getTotalCount(), 0)) {
            complete(job, failed > 0 ? "文本索引任务完成，部分档案处理失败" : "文本索引任务完成");
            return;
        }
        if (job.getMode() == ArchiveTextIndexMode.MISSING && countCandidates(job.getHallId(), ArchiveTextIndexMode.MISSING, documentIds(job)) <= 0) {
            complete(job, "文本索引任务完成");
            return;
        }
        submit(job.getId());
    }

    private int countCandidates(Long hallId, ArchiveTextIndexMode mode, List<Long> documentIds) {
        return Math.toIntExact(archiveDocumentService.count(candidateWrapper(hallId, mode, documentIds)));
    }

    private List<ArchiveDocument> loadCandidates(ArchiveTextIndexJob job, int limit) {
        ArchiveTextIndexMode mode = job.getMode() == null ? ArchiveTextIndexMode.MISSING : job.getMode();
        LambdaQueryWrapper<ArchiveDocument> wrapper = candidateWrapper(job.getHallId(), mode, documentIds(job))
                .orderByAsc(ArchiveDocument::getArchivedAt);
        if (mode == ArchiveTextIndexMode.MISSING) {
            return archiveDocumentService.list(wrapper.last("LIMIT " + limit));
        }
        return archiveDocumentService.list(wrapper.last("LIMIT " + limit + " OFFSET " + value(job.getProcessedCount(), 0)));
    }

    private LambdaQueryWrapper<ArchiveDocument> candidateWrapper(Long hallId, ArchiveTextIndexMode mode, List<Long> documentIds) {
        LambdaQueryWrapper<ArchiveDocument> wrapper = new LambdaQueryWrapper<ArchiveDocument>()
                .eq(ArchiveDocument::getStatus, ArchiveDocumentStatus.ACTIVE)
                .eq(hallId != null, ArchiveDocument::getHallId, hallId);
        if (!documentIds.isEmpty()) {
            wrapper.in(ArchiveDocument::getId, documentIds);
        }
        if (mode == ArchiveTextIndexMode.MISSING) {
            wrapper.and(inner -> inner.isNull(ArchiveDocument::getOcrText).or().eq(ArchiveDocument::getOcrText, ""));
        }
        return wrapper;
    }

    private void submit(Long jobId) {
        if ("rabbitmq".equalsIgnoreCase(properties.getProcessing().getMode())) {
            ArchiveStorageProperties.Rabbitmq rabbitmq = properties.getProcessing().getRabbitmq();
            rabbitTemplate.convertAndSend(
                    rabbitmq.getExchange(),
                    rabbitmq.getTextIndexRoutingKey(),
                    new ArchiveTextIndexMessage(jobId)
            );
            return;
        }
        processJobBatch(jobId);
    }

    private void complete(ArchiveTextIndexJob job, String message) {
        job.setStatus(ArchiveTextIndexJobStatus.COMPLETED);
        job.setFinishedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        updateById(job);
        publish(job, message);
    }

    private void fail(ArchiveTextIndexJob job, String message) {
        job.setStatus(ArchiveTextIndexJobStatus.FAILED);
        job.setErrorMessage(limit(message, 1000));
        job.setFinishedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        updateById(job);
        publish(job, "文本索引任务失败：" + limit(message, 200));
    }

    private void publish(ArchiveTextIndexJob job, String message) {
        eventPublisher.publish(ArchiveRealtimeEvent.textIndexJobChanged(
                job.getId(),
                job.getHallId(),
                job.getStatus() == null ? null : job.getStatus().name(),
                message,
                job.getTotalCount(),
                job.getProcessedCount(),
                job.getSuccessCount(),
                job.getSkippedCount(),
                job.getFailedCount()
        ));
    }

    private int normalizeBatchSize(Integer batchSize) {
        if (batchSize == null || batchSize < 1) {
            return DEFAULT_BATCH_SIZE;
        }
        return Math.min(batchSize, MAX_BATCH_SIZE);
    }

    private boolean hasOcrText(ArchiveDocument document) {
        return document.getOcrText() != null && !document.getOcrText().isBlank();
    }

    private List<Long> normalizeDocumentIds(List<Long> documentIds) {
        if (documentIds == null) {
            return List.of();
        }
        return documentIds.stream().filter(java.util.Objects::nonNull).distinct().limit(100).toList();
    }

    private String writeDocumentIds(List<Long> documentIds) {
        try {
            return objectMapper.writeValueAsString(documentIds);
        } catch (Exception ex) {
            throw new BizException("保存索引任务文档范围失败");
        }
    }

    private List<Long> documentIds(ArchiveTextIndexJob job) {
        if (job.getDocumentIdsJson() == null || job.getDocumentIdsJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(job.getDocumentIdsJson(), new TypeReference<>() { });
        } catch (Exception ex) {
            throw new BizException("读取索引任务文档范围失败");
        }
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
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

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

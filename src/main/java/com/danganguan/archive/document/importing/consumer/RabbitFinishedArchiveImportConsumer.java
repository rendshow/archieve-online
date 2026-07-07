package com.danganguan.archive.document.importing.consumer;

import com.danganguan.archive.document.importing.FinishedArchiveImportMessage;
import com.danganguan.archive.document.importing.service.FinishedArchiveImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "archive.processing", name = "mode", havingValue = "rabbitmq")
public class RabbitFinishedArchiveImportConsumer {
    private final FinishedArchiveImportService finishedArchiveImportService;

    @RabbitListener(queues = "${archive.processing.rabbitmq.import-queue}")
    public void importFinishedArchives(FinishedArchiveImportMessage message) {
        try {
            finishedArchiveImportService.processJob(message.jobId());
        } catch (Exception ex) {
            log.error("异步导入成品档案失败，jobId={}", message.jobId(), ex);
        }
    }
}

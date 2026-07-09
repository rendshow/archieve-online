package com.danganguan.archive.document.indexing.consumer;

import com.danganguan.archive.document.indexing.ArchiveTextIndexMessage;
import com.danganguan.archive.document.indexing.service.ArchiveDocumentTextIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "archive.processing", name = "mode", havingValue = "rabbitmq")
public class RabbitArchiveTextIndexConsumer {
    private final ArchiveDocumentTextIndexService archiveDocumentTextIndexService;

    @RabbitListener(queues = "${archive.processing.rabbitmq.text-index-queue}")
    public void indexArchiveText(ArchiveTextIndexMessage message) {
        try {
            archiveDocumentTextIndexService.processJobBatch(message.jobId());
        } catch (Exception ex) {
            log.error("异步文本索引失败，jobId={}", message.jobId(), ex);
        }
    }
}

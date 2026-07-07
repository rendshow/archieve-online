package com.danganguan.archive.task.processing.config;

import com.danganguan.archive.common.config.ArchiveStorageProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "archive.processing", name = "mode", havingValue = "rabbitmq")
public class RabbitTaskProcessingConfig {

    @Bean
    public Queue archiveTaskQueue(ArchiveStorageProperties properties) {
        return new Queue(properties.getProcessing().getRabbitmq().getQueue(), true);
    }

    @Bean
    public Queue archiveFinishedImportQueue(ArchiveStorageProperties properties) {
        return new Queue(properties.getProcessing().getRabbitmq().getImportQueue(), true);
    }

    @Bean
    public DirectExchange archiveTaskExchange(ArchiveStorageProperties properties) {
        return new DirectExchange(properties.getProcessing().getRabbitmq().getExchange(), true, false);
    }

    @Bean
    public Binding archiveTaskBinding(Queue archiveTaskQueue, DirectExchange archiveTaskExchange,
                                      ArchiveStorageProperties properties) {
        return BindingBuilder.bind(archiveTaskQueue)
                .to(archiveTaskExchange)
                .with(properties.getProcessing().getRabbitmq().getRoutingKey());
    }

    @Bean
    public Binding archiveFinishedImportBinding(Queue archiveFinishedImportQueue, DirectExchange archiveTaskExchange,
                                                ArchiveStorageProperties properties) {
        return BindingBuilder.bind(archiveFinishedImportQueue)
                .to(archiveTaskExchange)
                .with(properties.getProcessing().getRabbitmq().getImportRoutingKey());
    }

    @Bean
    public MessageConverter archiveRabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

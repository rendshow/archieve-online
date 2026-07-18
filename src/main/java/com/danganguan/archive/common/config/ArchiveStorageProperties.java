package com.danganguan.archive.common.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive")
@Getter
@Setter
public class ArchiveStorageProperties {
    private String storageRoot = "storage";
    private Storage storage = new Storage();
    private Processing processing = new Processing();




    @Getter
    @Setter
    public static class Storage {
        private String provider = "local";
        private Minio minio = new Minio();


    }

    @Getter
    @Setter
    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "danganguan-online";
        private boolean createBucket = true;





        public boolean isCreateBucket() {
            return createBucket;
        }

    }

    @Getter
    @Setter
    public static class Processing {
        private String mode = "sync";
        private Rabbitmq rabbitmq = new Rabbitmq();


    }

    @Getter
    @Setter
    public static class Rabbitmq {
        private String exchange = "archive.processing.exchange";
        private String queue = "archive.processing.task.queue";
        private String routingKey = "archive.processing.task";
        private String importQueue = "archive.import.finished.queue";
        private String importRoutingKey = "archive.import.finished";
        private String textIndexQueue = "archive.text.index.queue";
        private String textIndexRoutingKey = "archive.text.index";







    }
}

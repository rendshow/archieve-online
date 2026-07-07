package com.danganguan.archive.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive")
public class ArchiveStorageProperties {
    private String storageRoot = "storage";
    private Storage storage = new Storage();
    private Processing processing = new Processing();

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Processing getProcessing() {
        return processing;
    }

    public void setProcessing(Processing processing) {
        this.processing = processing;
    }

    public static class Storage {
        private String provider = "local";
        private Minio minio = new Minio();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Minio getMinio() {
            return minio;
        }

        public void setMinio(Minio minio) {
            this.minio = minio;
        }
    }

    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "danganguan-online";
        private boolean createBucket = true;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public boolean isCreateBucket() {
            return createBucket;
        }

        public void setCreateBucket(boolean createBucket) {
            this.createBucket = createBucket;
        }
    }

    public static class Processing {
        private String mode = "sync";
        private Rabbitmq rabbitmq = new Rabbitmq();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Rabbitmq getRabbitmq() {
            return rabbitmq;
        }

        public void setRabbitmq(Rabbitmq rabbitmq) {
            this.rabbitmq = rabbitmq;
        }
    }

    public static class Rabbitmq {
        private String exchange = "archive.processing.exchange";
        private String queue = "archive.processing.task.queue";
        private String routingKey = "archive.processing.task";
        private String importQueue = "archive.import.finished.queue";
        private String importRoutingKey = "archive.import.finished";

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getQueue() {
            return queue;
        }

        public void setQueue(String queue) {
            this.queue = queue;
        }

        public String getRoutingKey() {
            return routingKey;
        }

        public void setRoutingKey(String routingKey) {
            this.routingKey = routingKey;
        }

        public String getImportQueue() {
            return importQueue;
        }

        public void setImportQueue(String importQueue) {
            this.importQueue = importQueue;
        }

        public String getImportRoutingKey() {
            return importRoutingKey;
        }

        public void setImportRoutingKey(String importRoutingKey) {
            this.importRoutingKey = importRoutingKey;
        }
    }
}

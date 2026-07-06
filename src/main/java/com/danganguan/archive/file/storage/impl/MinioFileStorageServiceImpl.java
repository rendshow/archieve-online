package com.danganguan.archive.file.storage.impl;

import com.danganguan.archive.common.config.ArchiveStorageProperties;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.file.storage.FileStorageService;
import com.danganguan.archive.file.storage.StoredFile;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "archive.storage", name = "provider", havingValue = "minio")
public class MinioFileStorageServiceImpl implements FileStorageService {
    private final ArchiveStorageProperties properties;

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        ArchiveStorageProperties.Minio config = properties.getStorage().getMinio();
        if (config.getAccessKey() == null || config.getAccessKey().isBlank()
                || config.getSecretKey() == null || config.getSecretKey().isBlank()) {
            throw new BizException("MinIO 未配置 access-key/secret-key");
        }
        minioClient = MinioClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build();
        ensureBucket(config);
    }

    @Override
    public StoredFile saveRaw(Long taskId, MultipartFile file, String fileExt) {
        try {
            String objectKey = "raw/" + taskId + "/" + UUID.randomUUID() + (fileExt.isBlank() ? "" : "." + fileExt);
            Path cachePath = cachePath(objectKey);
            Files.createDirectories(cachePath.getParent());

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.getInputStream();
                 DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                Files.copy(digestInput, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
            upload(objectKey, cachePath);
            return new StoredFile(objectKey, HexFormat.of().formatHex(digest.digest()), Files.size(cachePath));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BizException("保存文件到 MinIO 失败：" + ex.getMessage());
        }
    }

    @Override
    public Path resolve(String relativePath) {
        String objectKey = normalizeObjectKey(relativePath);
        Path cachePath = cachePath(objectKey);
        if (Files.exists(cachePath)) {
            return cachePath;
        }
        try {
            Files.createDirectories(cachePath.getParent());
            try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build())) {
                Files.copy(input, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
            return cachePath;
        } catch (Exception ex) {
            throw new BizException("从 MinIO 读取文件失败：" + ex.getMessage());
        }
    }

    @Override
    public Path prepareWorkspaceFile(Long taskId, String filename) {
        try {
            Path target = cachePath("workspace/" + taskId + "/" + filename);
            Files.createDirectories(target.getParent());
            return target;
        } catch (IOException ex) {
            throw new BizException("创建 MinIO 本地缓存目录失败：" + ex.getMessage());
        }
    }

    @Override
    public String toRelativePath(Path path) {
        Path cacheRoot = cacheRoot();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(cacheRoot)) {
            throw new BizException("非法 MinIO 缓存文件路径");
        }
        String objectKey = cacheRoot.relativize(normalized).toString().replace('\\', '/');
        upload(objectKey, normalized);
        return objectKey;
    }

    private void ensureBucket(ArchiveStorageProperties.Minio config) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(config.getBucket()).build());
            if (!exists && config.isCreateBucket()) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(config.getBucket()).build());
            } else if (!exists) {
                throw new BizException("MinIO bucket 不存在：" + config.getBucket());
            }
        } catch (Exception ex) {
            throw new BizException("初始化 MinIO bucket 失败：" + ex.getMessage());
        }
    }

    private void upload(String objectKey, Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .stream(input, Files.size(path), -1)
                    .build());
        } catch (Exception ex) {
            throw new BizException("上传文件到 MinIO 失败：" + ex.getMessage());
        }
    }

    private Path cachePath(String objectKey) {
        Path root = cacheRoot();
        Path path = root.resolve(normalizeObjectKey(objectKey)).normalize();
        if (!path.startsWith(root)) {
            throw new BizException("非法 MinIO 对象路径");
        }
        return path;
    }

    private Path cacheRoot() {
        return Path.of(properties.getStorageRoot()).toAbsolutePath().normalize().resolve("minio-cache").normalize();
    }

    private String normalizeObjectKey(String objectKey) {
        String normalized = objectKey == null ? "" : objectKey.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.contains("..")) {
            throw new BizException("非法 MinIO 对象路径");
        }
        return normalized;
    }

    private String bucket() {
        return properties.getStorage().getMinio().getBucket();
    }
}

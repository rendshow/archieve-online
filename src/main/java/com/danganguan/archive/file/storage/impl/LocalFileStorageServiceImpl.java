package com.danganguan.archive.file.storage.impl;

import com.danganguan.archive.common.config.ArchiveStorageProperties;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.file.storage.FileStorageService;
import com.danganguan.archive.file.storage.StoredFile;
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
@ConditionalOnProperty(prefix = "archive.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageServiceImpl implements FileStorageService {
    private final ArchiveStorageProperties properties;

    @Override
    public StoredFile saveRaw(Long taskId, MultipartFile file, String fileExt) {
        try {
            Path root = storageRoot();
            Path dir = root.resolve("raw").resolve(String.valueOf(taskId)).normalize();
            Files.createDirectories(dir);

            String storedName = UUID.randomUUID() + (fileExt.isBlank() ? "" : "." + fileExt);
            Path target = dir.resolve(storedName).normalize();
            if (!target.startsWith(root)) {
                throw new BizException("非法文件路径");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.getInputStream();
                 DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                Files.copy(digestInput, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String sha256 = HexFormat.of().formatHex(digest.digest());
            return new StoredFile(toRelativePath(target), sha256, Files.size(target));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BizException("保存文件失败：" + ex.getMessage());
        }
    }

    @Override
    public StoredFile saveArchive(String objectKey, InputStream input) {
        try {
            Path root = storageRoot();
            Path target = root.resolve(normalizeRelativePath(objectKey)).normalize();
            if (!target.startsWith(root)) {
                throw new BizException("非法文件路径");
            }
            Files.createDirectories(target.getParent());

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                Files.copy(digestInput, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return new StoredFile(toRelativePath(target), HexFormat.of().formatHex(digest.digest()), Files.size(target));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BizException("保存正式档案文件失败：" + ex.getMessage());
        }
    }

    @Override
    public Path resolve(String relativePath) {
        Path root = storageRoot();
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new BizException("非法文件路径");
        }
        return path;
    }

    @Override
    public Path prepareWorkspaceFile(Long taskId, String filename) {
        try {
            Path root = storageRoot();
            Path dir = root.resolve("workspace").resolve(String.valueOf(taskId)).normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(root)) {
                throw new BizException("非法文件路径");
            }
            return target;
        } catch (IOException ex) {
            throw new BizException("创建工作区目录失败：" + ex.getMessage());
        }
    }

    @Override
    public String toRelativePath(Path path) {
        Path root = storageRoot();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new BizException("非法文件路径");
        }
        return root.relativize(normalized).toString().replace('\\', '/');
    }

    private Path storageRoot() {
        return Path.of(properties.getStorageRoot()).toAbsolutePath().normalize();
    }

    private String normalizeRelativePath(String relativePath) {
        String normalized = relativePath == null ? "" : relativePath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.contains("..")) {
            throw new BizException("非法文件路径");
        }
        return normalized;
    }
}

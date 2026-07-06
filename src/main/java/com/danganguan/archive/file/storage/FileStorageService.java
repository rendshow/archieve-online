package com.danganguan.archive.file.storage;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {
    StoredFile saveRaw(Long taskId, MultipartFile file, String fileExt);

    Path resolve(String relativePath);

    Path prepareWorkspaceFile(Long taskId, String filename);

    String toRelativePath(Path path);
}

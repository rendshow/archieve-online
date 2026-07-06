package com.danganguan.archive.file.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Path;

public interface FileStorageService {
    StoredFile saveRaw(Long taskId, MultipartFile file, String fileExt);

    StoredFile saveArchive(String objectKey, InputStream input);

    Path resolve(String relativePath);

    Path prepareWorkspaceFile(Long taskId, String filename);

    String toRelativePath(Path path);
}

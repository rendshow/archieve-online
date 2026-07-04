package com.danganguan.archive.ai.analysis.service.impl;

import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeRequest;
import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.danganguan.archive.ai.ocr.service.impl.NoopOcrServiceImpl;
import com.danganguan.archive.document.process.ProcessedFileResult;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadType;
import com.danganguan.archive.file.storage.FileStorageService;
import com.danganguan.archive.file.storage.StoredFile;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocalDocumentAnalyzeServiceImplTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldAnalyzeGbkZipWithoutMalformedInput() throws IOException {
        Path zipPath = tempDir.resolve("gbk.zip");
        try (ZipOutputStream zipOutput = new ZipOutputStream(Files.newOutputStream(zipPath), Charset.forName("GBK"))) {
            zipOutput.putNextEntry(new ZipEntry("中文图片.png"));
            zipOutput.write(new byte[]{1, 2, 3});
            zipOutput.closeEntry();
        }

        UploadedFile file = new UploadedFile();
        file.setOriginalName("测试压缩包.zip");
        file.setStoragePath("gbk.zip");
        file.setUploadType(UploadType.ZIP);

        LocalDocumentAnalyzeServiceImpl service = new LocalDocumentAnalyzeServiceImpl(
                new TestFileStorageService(tempDir),
                new NoopOcrServiceImpl()
        );

        DocumentAnalyzeResult result = service.analyze(new DocumentAnalyzeRequest(
                new ArchiveTask(),
                List.of(file),
                new ProcessedFileResult("output.pdf", OutputFormat.PDF, 1, "")
        ));

        assertNotNull(result);
    }

    @Test
    void shouldDetectPersonNameFromRawOcrVariants() throws Exception {
        LocalDocumentAnalyzeServiceImpl service = new LocalDocumentAnalyzeServiceImpl(
                new TestFileStorageService(tempDir),
                new NoopOcrServiceImpl()
        );
        Method detectPersonName = LocalDocumentAnalyzeServiceImpl.class.getDeclaredMethod("detectPersonName", String.class);
        detectPersonName.setAccessible(true);

        assertEquals("彭国斌", detectPersonName.invoke(service, "姓名彭国斌性期男出生年月"));
        assertEquals("李华", detectPersonName.invoke(service, "姓名 李华 工作单位 中国医科大学"));
        assertEquals("王海义", detectPersonName.invoke(service, "94052006-1王海义jpg 中国人民解放军农牧大学"));
    }


    private record TestFileStorageService(Path root) implements FileStorageService {
        @Override
        public StoredFile saveRaw(Long taskId, MultipartFile file, String fileExt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path resolve(String relativePath) {
            return root.resolve(relativePath).normalize();
        }

        @Override
        public Path prepareWorkspaceFile(Long taskId, String filename) {
            return root.resolve(filename).normalize();
        }

        @Override
        public String toRelativePath(Path path) {
            return root.relativize(path).toString();
        }
    }
}

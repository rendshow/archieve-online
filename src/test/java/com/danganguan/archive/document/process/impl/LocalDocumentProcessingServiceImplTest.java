package com.danganguan.archive.document.process.impl;

import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.process.ProcessedFileResult;
import com.danganguan.archive.document.process.boundary.BoundaryGroup;
import com.danganguan.archive.document.process.boundary.BoundaryImage;
import com.danganguan.archive.document.process.boundary.PersonBoundaryDetectionService;
import com.danganguan.archive.file.entity.UploadedFile;
import com.danganguan.archive.file.enums.UploadType;
import com.danganguan.archive.file.storage.FileStorageService;
import com.danganguan.archive.file.storage.StoredFile;
import com.danganguan.archive.task.entity.ArchiveTask;
import com.danganguan.archive.task.enums.OutputFormat;
import com.danganguan.archive.task.enums.PersonSplitStrategy;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDocumentProcessingServiceImplTest {
    @TempDir
    Path tempDir;

    @Test
    void pngOutputWithSinglePersonCopiesEveryImageFromZip() throws IOException {
        Path zipPath = tempDir.resolve("images.zip");
        writeImageZip(zipPath, "1.png", "2.png", "3.png");
        LocalDocumentProcessingServiceImpl service = service();

        List<ProcessedFileResult> results = service.process(task(OutputFormat.PNG, PersonSplitStrategy.SINGLE_PERSON, null), zipFile());

        assertEquals(3, results.size());
        for (ProcessedFileResult result : results) {
            assertEquals(OutputFormat.PNG, result.outputFormat());
            assertEquals(1, result.pageCount());
            assertTrue(Files.exists(tempDir.resolve(result.storagePath())));
        }
    }

    @Test
    void pdfOutputWithFixedElementsSplitsByExactCount() throws IOException {
        Path zipPath = tempDir.resolve("images.zip");
        writeImageZip(zipPath, "1.png", "2.png", "3.png", "4.png");
        LocalDocumentProcessingServiceImpl service = service();

        List<ProcessedFileResult> results = service.process(task(OutputFormat.PDF, PersonSplitStrategy.FIXED_ELEMENTS_PER_PERSON, 2), zipFile());

        assertEquals(2, results.size());
        for (ProcessedFileResult result : results) {
            assertEquals(OutputFormat.PDF, result.outputFormat());
            assertEquals(2, result.pageCount());
            try (PDDocument document = PDDocument.load(tempDir.resolve(result.storagePath()).toFile())) {
                assertEquals(2, document.getNumberOfPages());
            }
        }
    }

    @Test
    void fixedElementsRequiresImageCountToBeMultipleOfN() throws IOException {
        Path zipPath = tempDir.resolve("images.zip");
        writeImageZip(zipPath, "1.png", "2.png", "3.png");
        LocalDocumentProcessingServiceImpl service = service();

        BizException exception = assertThrows(BizException.class,
                () -> service.process(task(OutputFormat.PDF, PersonSplitStrategy.FIXED_ELEMENTS_PER_PERSON, 2), zipFile()));

        assertTrue(exception.getMessage().contains("必须是 n 的倍数"));
    }

    @Test
    void pdfOutputWithAiBoundaryUsesDetectedGroups() throws IOException {
        Path zipPath = tempDir.resolve("images.zip");
        writeImageZip(zipPath, "1.png", "2.png", "3.png", "4.png", "5.png");
        PersonBoundaryDetectionService boundaryService = images -> List.of(
                new BoundaryGroup(List.of(0, 1), "first"),
                new BoundaryGroup(List.of(2, 3), "second"),
                new BoundaryGroup(List.of(4), "third")
        );
        LocalDocumentProcessingServiceImpl service = service(boundaryService);

        List<ProcessedFileResult> results = service.process(task(OutputFormat.PDF, PersonSplitStrategy.AI_PERSON_BOUNDARY, null), zipFile());

        assertEquals(3, results.size());
        assertEquals(2, results.get(0).pageCount());
        assertEquals(2, results.get(1).pageCount());
        assertEquals(1, results.get(2).pageCount());
        for (ProcessedFileResult result : results) {
            try (PDDocument document = PDDocument.load(tempDir.resolve(result.storagePath()).toFile())) {
                assertEquals(result.pageCount(), document.getNumberOfPages());
            }
        }
    }

    private LocalDocumentProcessingServiceImpl service() {
        return service(images -> List.of(new BoundaryGroup(images.stream().map(BoundaryImage::index).toList(), "single")));
    }

    private LocalDocumentProcessingServiceImpl service(PersonBoundaryDetectionService boundaryService) {
        return new LocalDocumentProcessingServiceImpl(new TestFileStorageService(tempDir), new NoopImageEnhanceServiceImpl(), boundaryService);
    }

    private ArchiveTask task(OutputFormat outputFormat, PersonSplitStrategy strategy, Integer fixedElementsPerPerson) {
        ArchiveTask task = new ArchiveTask();
        task.setId(1L);
        task.setOutputFormat(outputFormat);
        task.setPersonSplitStrategy(strategy);
        task.setFixedElementsPerPerson(fixedElementsPerPerson);
        task.setEnableScanEnhance(false);
        return task;
    }

    private UploadedFile zipFile() {
        UploadedFile file = new UploadedFile();
        file.setOriginalName("images.zip");
        file.setStoragePath("images.zip");
        file.setUploadType(UploadType.ZIP);
        return file;
    }

    private void writeImageZip(Path zipPath, String... names) throws IOException {
        try (ZipOutputStream zipOutput = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (String name : names) {
                zipOutput.putNextEntry(new ZipEntry(name));
                zipOutput.write(pngBytes());
                zipOutput.closeEntry();
            }
        }
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.BLACK.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private record TestFileStorageService(Path root) implements FileStorageService {
        @Override
        public StoredFile saveRaw(Long taskId, MultipartFile file, String fileExt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredFile saveArchive(String objectKey, InputStream input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path resolve(String relativePath) {
            return root.resolve(relativePath).normalize();
        }

        @Override
        public Path prepareWorkspaceFile(Long taskId, String filename) {
            try {
                Path dir = root.resolve("workspace").resolve(String.valueOf(taskId)).normalize();
                Files.createDirectories(dir);
                return dir.resolve(filename).normalize();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public String toRelativePath(Path path) {
            return root.relativize(path).toString();
        }
    }
}

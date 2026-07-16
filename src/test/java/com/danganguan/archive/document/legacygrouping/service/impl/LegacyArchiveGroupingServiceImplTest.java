package com.danganguan.archive.document.legacygrouping.service.impl;

import com.danganguan.archive.common.config.LegacyArchiveGroupingProperties;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.legacygrouping.dto.LegacyArchiveGroupingPreview;
import com.danganguan.archive.document.legacygrouping.dto.LegacyArchiveGroupingPreviewRequest;
import com.danganguan.archive.document.legacygrouping.enums.LegacyArchiveGroupType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyArchiveGroupingServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void groupsPdfImagesAndCatalogWithinOneFolder() throws IOException {
        Path folder = Files.createDirectories(tempDir.resolve("西区/99级/97-100"));
        touch(folder, "99010199-1万泰金.JPG");
        touch(folder, "99010199-2万泰金.JPG");
        touch(folder, "99010200-1刘强.JPG");
        touch(folder, "99010200-2刘强.JPG");
        touch(folder, "名单1.JPG");
        touch(folder, "名单2.JPG");
        touch(folder, "N2006-JX12•13•21-1徐春雨.pdf");
        touch(folder, "说明.txt");

        LegacyArchiveGroupingServiceImpl service = service();
        LegacyArchiveGroupingPreview preview = service.preview(new LegacyArchiveGroupingPreviewRequest("西区/99级/97-100"));

        assertEquals(7, preview.supportedFileCount());
        assertEquals(1, preview.unsupportedFileCount());
        assertEquals(3, preview.personRecordCount());
        assertEquals(1, preview.catalogGroupCount());
        assertEquals(0, preview.unknownFileCount());
        assertEquals(2, preview.groups().stream()
                .filter(group -> group.groupType() == LegacyArchiveGroupType.PERSON_RECORD && group.files().size() == 2)
                .count());
        assertEquals(2, preview.groups().stream()
                .filter(group -> group.groupType() == LegacyArchiveGroupType.FOLDER_CATALOG)
                .findFirst().orElseThrow().files().size());
    }

    @Test
    void keepsSingleImageAsPersonRecordAndFlagsUnnamedFolderSupportFile() throws IOException {
        Path folder = Files.createDirectories(tempDir.resolve("南湖/测试"));
        touch(folder, "81040124-张三.JPG");
        touch(folder, "封面.JPG");

        LegacyArchiveGroupingPreview preview = service().preview(new LegacyArchiveGroupingPreviewRequest("南湖/测试"));

        assertEquals(1, preview.personRecordCount());
        assertEquals(1, preview.unknownFileCount());
        assertEquals(1, preview.reviewRequiredCount());
        assertEquals("张三", preview.groups().stream()
                .filter(group -> group.groupType() == LegacyArchiveGroupType.PERSON_RECORD)
                .findFirst().orElseThrow().personNameCandidate());
    }

    @Test
    void rejectsPathOutsideConfiguredRoot() {
        assertThrows(BizException.class, () -> service().preview(new LegacyArchiveGroupingPreviewRequest("../outside")));
    }

    private LegacyArchiveGroupingServiceImpl service() {
        LegacyArchiveGroupingProperties properties = new LegacyArchiveGroupingProperties();
        properties.setRoot(tempDir.toString());
        return new LegacyArchiveGroupingServiceImpl(properties);
    }

    private void touch(Path folder, String name) throws IOException {
        Files.writeString(folder.resolve(name), "test");
    }
}

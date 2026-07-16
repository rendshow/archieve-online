package com.danganguan.archive.document.logicalgroup.rule;

import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.logicalgroup.enums.ArchiveLogicalGroupType;
import com.danganguan.archive.task.enums.OutputFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ArchiveLogicalGroupRuleEngineTest {

    @Test
    void groupsConsecutiveImagesAndKeepsPdfAndCatalogSeparate() {
        List<ArchiveLogicalGroupCandidate> groups = ArchiveLogicalGroupRuleEngine.build(List.of(
                document(1L, "99010199-2万泰金", OutputFormat.JPG),
                document(2L, "99010199-1万泰金", OutputFormat.JPG),
                document(3L, "N2006-JX12•13•21-1徐春雨", OutputFormat.PDF),
                document(4L, "名单1", OutputFormat.JPG),
                document(5L, "名单2", OutputFormat.JPG)
        ));

        assertEquals(3, groups.size());
        ArchiveLogicalGroupCandidate personImages = groups.stream()
                .filter(group -> "万泰金".equals(group.personName()))
                .findFirst().orElseThrow();
        assertEquals(2, personImages.documents().size());
        assertEquals("99010199-1万泰金", personImages.documents().getFirst().getTitle());
        assertFalse(personImages.requiresReview());
        assertEquals("N2006-JX12•13•21-1徐春雨", groups.stream()
                .filter(group -> group.documents().getFirst().getFileFormat() == OutputFormat.PDF)
                .findFirst().orElseThrow().title());
        assertEquals(2, groups.stream().filter(group -> group.groupType() == ArchiveLogicalGroupType.FOLDER_CATALOG)
                .findFirst().orElseThrow().documents().size());
    }

    @Test
    void flagsUnrecognizedSingleImageForReview() {
        ArchiveLogicalGroupCandidate group = ArchiveLogicalGroupRuleEngine.build(List.of(
                document(10L, "封面", OutputFormat.JPG)
        )).getFirst();

        assertEquals(ArchiveLogicalGroupType.UNKNOWN_FOLDER_FILE, group.groupType());
        assertEquals(true, group.requiresReview());
    }

    private ArchiveDocument document(Long id, String title, OutputFormat format) {
        ArchiveDocument document = new ArchiveDocument();
        document.setId(id);
        document.setTitle(title);
        document.setFileFormat(format);
        return document;
    }
}

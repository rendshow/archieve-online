package com.danganguan.archive.document.process.boundary;

import com.danganguan.archive.ai.ocr.dto.OcrResult;
import com.danganguan.archive.ai.ocr.service.OcrService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleBasedOcrPersonBoundaryDetectionServiceTest {

    @Test
    void detectsNewGroupWhenGraduateFormStartPageAppearsAgain() {
        Map<String, String> textByName = Map.of(
                "1.jpg", "中国人民解放军军需大学 研究生毕业鉴定表 姓名 李华 工作单位 学位级别 所学专业 个人鉴定",
                "2.jpg", "学员队鉴定意见 指导教师鉴定意见 科室鉴定意见 学校审核意见",
                "3.jpg", "中国人民解放军军需大学 届研究生毕业鉴定表 姓名 周绪斌 工作单位 学位级别 所学专业 个人鉴定",
                "4.jpg", "学位评定委员会决议 学位证书编号 填发日期"
        );
        RuleBasedOcrPersonBoundaryDetectionService service = new RuleBasedOcrPersonBoundaryDetectionService(ocr(textByName));

        List<BoundaryGroup> groups = service.detect(List.of(
                image(0, "1.jpg"),
                image(1, "2.jpg"),
                image(2, "3.jpg"),
                image(3, "4.jpg")
        ));

        assertEquals(2, groups.size());
        assertEquals(List.of(0, 1), groups.get(0).imageIndexes());
        assertEquals(List.of(2, 3), groups.get(1).imageIndexes());
    }

    @Test
    void doesNotSplitOnCommitteeResolutionPageWithNameOnly() {
        Map<String, String> textByName = Map.of(
                "1.jpg", "研究生毕业鉴定表 姓名 李华 工作单位 学位级别 所学专业 个人鉴定",
                "2.jpg", "研究生姓名 李华 所学专业 预防兽医学 学位评定委员会决议"
        );
        RuleBasedOcrPersonBoundaryDetectionService service = new RuleBasedOcrPersonBoundaryDetectionService(ocr(textByName));

        List<BoundaryGroup> groups = service.detect(List.of(image(0, "1.jpg"), image(1, "2.jpg")));

        assertEquals(1, groups.size());
        assertEquals(List.of(0, 1), groups.get(0).imageIndexes());
    }

    private BoundaryImage image(int index, String name) {
        return new BoundaryImage(index, index + 1, name, Path.of(name));
    }

    private OcrService ocr(Map<String, String> textByName) {
        return imagePath -> new OcrResult(textByName.getOrDefault(imagePath.getFileName().toString(), ""),
                BigDecimal.ONE, "fake", "ok");
    }
}

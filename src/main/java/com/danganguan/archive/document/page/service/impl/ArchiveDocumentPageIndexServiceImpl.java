package com.danganguan.archive.document.page.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danganguan.archive.ai.ocr.dto.OcrResult;
import com.danganguan.archive.ai.ocr.service.OcrService;
import com.danganguan.archive.common.exception.BizException;
import com.danganguan.archive.document.entity.ArchiveDocument;
import com.danganguan.archive.document.page.dto.ArchiveDocumentPageIndexResult;
import com.danganguan.archive.document.page.entity.ArchiveDocumentPage;
import com.danganguan.archive.document.page.mapper.ArchiveDocumentPageMapper;
import com.danganguan.archive.document.page.service.ArchiveDocumentPageIndexService;
import com.danganguan.archive.file.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArchiveDocumentPageIndexServiceImpl implements ArchiveDocumentPageIndexService {
    private static final int PDF_RENDER_DPI = 200;

    private final ArchiveDocumentPageMapper pageMapper;
    private final FileStorageService fileStorageService;
    private final OcrService ocrService;

    @Override
    @Transactional
    public ArchiveDocumentPageIndexResult rebuild(ArchiveDocument document) {
        if (document == null || document.getId() == null) {
            throw new BizException("正式档案不存在");
        }
        pageMapper.delete(new LambdaQueryWrapper<ArchiveDocumentPage>()
                .eq(ArchiveDocumentPage::getArchiveDocumentId, document.getId()));

        Path source = fileStorageService.resolve(document.getStoragePath());
        List<IndexedPage> pages = "pdf".equalsIgnoreCase(extension(source))
                ? indexPdf(source)
                : List.of(indexImage(source));
        LocalDateTime now = LocalDateTime.now();
        for (IndexedPage page : pages) {
            ArchiveDocumentPage entity = new ArchiveDocumentPage();
            entity.setArchiveDocumentId(document.getId());
            entity.setPageNo(page.pageNo());
            entity.setOcrText(blankToNull(page.text()));
            entity.setOcrConfidence(page.confidence());
            entity.setOcrEngine(page.engine());
            entity.setOcrReason(limit(page.reason(), 1000));
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            pageMapper.insert(entity);
        }
        String mergedText = pages.stream()
                .map(page -> "[第" + page.pageNo() + "页]\n" + page.text())
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        return new ArchiveDocumentPageIndexResult(pages.size(), mergedText);
    }

    private List<IndexedPage> indexPdf(Path pdfPath) {
        try (PDDocument pdf = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            List<IndexedPage> pages = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < pdf.getNumberOfPages(); pageIndex++) {
                String nativeText = extractPdfPageText(pdf, pageIndex + 1);
                if (!nativeText.isBlank()) {
                    pages.add(new IndexedPage(pageIndex + 1, nativeText, BigDecimal.ONE, "pdf-text", "PDF 原生文本提取"));
                    continue;
                }
                pages.add(ocrRenderedPdfPage(renderer, pageIndex));
            }
            return pages;
        } catch (IOException ex) {
            throw new BizException("读取 PDF 页面失败：" + ex.getMessage());
        }
    }

    private IndexedPage ocrRenderedPdfPage(PDFRenderer renderer, int pageIndex) throws IOException {
        Path imagePath = Files.createTempFile("archive-page-", ".png");
        try {
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, PDF_RENDER_DPI, ImageType.RGB);
            ImageIO.write(image, "png", imagePath.toFile());
            OcrResult result = ocrService.recognize(imagePath);
            return new IndexedPage(pageIndex + 1, result.text(), result.confidence(), result.engine(), result.reason());
        } finally {
            Files.deleteIfExists(imagePath);
        }
    }

    private IndexedPage indexImage(Path imagePath) {
        OcrResult result = ocrService.recognize(imagePath);
        return new IndexedPage(1, result.text(), result.confidence(), result.engine(), result.reason());
    }

    private String extractPdfPageText(PDDocument pdf, int pageNo) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageNo);
        stripper.setEndPage(pageNo);
        String text = stripper.getText(pdf);
        return text == null ? "" : text.trim();
    }

    private String extension(Path path) {
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record IndexedPage(int pageNo, String text, BigDecimal confidence, String engine, String reason) {
    }
}

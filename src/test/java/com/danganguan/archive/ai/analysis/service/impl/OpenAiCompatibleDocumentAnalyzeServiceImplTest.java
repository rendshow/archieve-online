package com.danganguan.archive.ai.analysis.service.impl;

import com.danganguan.archive.ai.analysis.dto.DocumentAnalyzeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenAiCompatibleDocumentAnalyzeServiceImplTest {
    @Test
    void shouldFallbackWhenModelReturnsEmptyContent() throws Exception {
        OpenAiCompatibleDocumentAnalyzeServiceImpl service = new OpenAiCompatibleDocumentAnalyzeServiceImpl(
                null,
                null,
                null,
                new ObjectMapper(),
                null
        );
        Method parseResult = OpenAiCompatibleDocumentAnalyzeServiceImpl.class
                .getDeclaredMethod("parseResult", String.class, String.class);
        parseResult.setAccessible(true);

        DocumentAnalyzeResult result = (DocumentAnalyzeResult) parseResult.invoke(service, "", "姓名：尚宇\n硕士申请材料");

        assertNotNull(result);
        assertEquals("尚宇", result.detectedPersonName());
        assertEquals("姓名：尚宇\n硕士申请材料", result.extractedText());
    }
}

package com.danganguan.archive.agent.v2.service;

import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.agent.v2.config.AgentV2Properties;
import com.danganguan.archive.agent.v2.dto.AgentTaskSpec;
import com.danganguan.archive.agent.v2.dto.AgentToolExecutionResult;
import com.danganguan.archive.agent.v2.enums.AgentEvidenceRequirement;
import com.danganguan.archive.agent.v2.enums.AgentTaskIntent;
import com.danganguan.archive.document.fact.dto.ArchiveFactEvidence;
import com.danganguan.archive.document.fact.enums.ArchiveFactType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentV2AnswerComposerTest {
    @Test
    void shouldAcceptLlmAnswerThatKeepsEvidenceValue() {
        AgentV2AnswerLlmService llm = mock(AgentV2AnswerLlmService.class);
        when(llm.enhance(any(), any(), any(), any(), any(), any())).thenReturn("韩雪的自然辩证法成绩为 86 分。证据见第 5 页。");

        var result = new AgentV2AnswerComposer(enabledProperties(), llm).compose("韩雪的自然辩证法成绩是多少？", result());

        assertThat(result.source()).isEqualTo("LLM");
        assertThat(result.answer()).contains("86");
    }

    @Test
    void shouldRejectLlmAnswerThatChangesEvidenceValue() {
        AgentV2AnswerLlmService llm = mock(AgentV2AnswerLlmService.class);
        when(llm.enhance(any(), any(), any(), any(), any(), any())).thenReturn("韩雪的自然辩证法成绩为 99 分。");

        var result = new AgentV2AnswerComposer(enabledProperties(), llm).compose("韩雪的自然辩证法成绩是多少？", result());

        assertThat(result.source()).isEqualTo("RULE_GUARDED");
        assertThat(result.answer()).contains("86");
    }

    private AgentV2Properties enabledProperties() {
        AgentV2Properties properties = new AgentV2Properties();
        properties.setLlmEnabled(true);
        return properties;
    }

    private AgentToolExecutionResult result() {
        AgentResolvedScope scope = new AgentResolvedScope(AgentScopeType.DOCUMENT, 1L, "测试", 1L, null, "TEST", "测试");
        AgentTaskSpec task = new AgentTaskSpec(AgentTaskIntent.ANSWER_FROM_DOCUMENTS, "DOCUMENT_EVIDENCE_QUERY", scope,
                List.of("课程成绩"), AgentEvidenceRequirement.PAGE_EVIDENCE_REQUIRED, true, "测试", null);
        ArchiveFactEvidence evidence = new ArchiveFactEvidence(1L, "测试档案", "测试", 5,
                ArchiveFactType.COURSE_GRADE, "自然辩证法", "86", "86", new BigDecimal("0.75"), "自然辩证法 86");
        return new AgentToolExecutionResult(1L, "request-1", task, "COMPLETED", "RULE", "韩雪的自然辩证法成绩为 86（第 5 页）。",
                List.of(), List.of(evidence), List.of());
    }
}

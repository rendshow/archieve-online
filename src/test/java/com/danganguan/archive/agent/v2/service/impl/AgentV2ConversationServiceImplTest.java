package com.danganguan.archive.agent.v2.service.impl;

import com.danganguan.archive.agent.dto.AgentClientContext;
import com.danganguan.archive.agent.dto.AgentDocumentReference;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.entity.AgentMessage;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.agent.mapper.AgentMessageMapper;
import com.danganguan.archive.agent.mapper.AgentSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentV2ConversationServiceImplTest {
    private final AgentMessageMapper messageMapper = mock(AgentMessageMapper.class);
    private final AgentV2ConversationServiceImpl service = new AgentV2ConversationServiceImpl(
            mock(AgentSessionMapper.class), messageMapper, new ObjectMapper());

    @Test
    void shouldResolveSinglePreviousDocumentWithinCurrentFolder() throws Exception {
        when(messageMapper.selectList(any())).thenReturn(List.of(previousMessage()));

        var resolved = service.resolveReferencedDocument(99L, "分析这份档案记录了哪些信息",
                new AgentClientContext("ARCHIVE_FOLDER", 1L, "西区/博士", null, null, null, null),
                scope("西区/博士"));

        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().documentId()).isEqualTo(12L);
    }

    @Test
    void shouldNotResolvePreviousDocumentOutsideCurrentFolder() throws Exception {
        when(messageMapper.selectList(any())).thenReturn(List.of(previousMessage()));

        var resolved = service.resolveReferencedDocument(99L, "分析这份档案记录了哪些信息",
                new AgentClientContext("ARCHIVE_FOLDER", 1L, "西区/硕士", null, null, null, null),
                scope("西区/硕士"));

        assertThat(resolved).isEmpty();
    }

    private AgentMessage previousMessage() throws Exception {
        AgentMessage previous = new AgentMessage();
        previous.setResolvedScopeJson(new ObjectMapper().writeValueAsString(new Snapshot(
                scope("西区/博士"), List.of(new AgentDocumentReference(12L, 1L, "韩雪", "西区/博士/2006", "PDF"))
        )));
        return previous;
    }

    private AgentResolvedScope scope(String folderPath) {
        return new AgentResolvedScope(AgentScopeType.FOLDER, 1L, folderPath, null, null, "TEST", "");
    }

    private record Snapshot(AgentResolvedScope scope, List<AgentDocumentReference> documents) {
    }
}

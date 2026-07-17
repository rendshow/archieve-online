package com.danganguan.archive.agent.v2.benchmark;

import com.danganguan.archive.agent.context.AgentContextResolver;
import com.danganguan.archive.agent.dto.AgentClientContext;
import com.danganguan.archive.agent.v2.enums.AgentEvidenceRequirement;
import com.danganguan.archive.agent.v2.enums.AgentTaskIntent;
import com.danganguan.archive.agent.v2.service.impl.RuleBasedAgentTaskPlanner;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentV2TaskSpecBenchmarkTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleBasedAgentTaskPlanner planner = new RuleBasedAgentTaskPlanner(new AgentContextResolver());

    @Test
    void shouldMeetCurrentTaskSpecSmokeBaseline() throws Exception {
        List<BenchmarkCase> cases = loadCases();
        List<String> failures = cases.stream()
                .filter(item -> !matches(item))
                .map(BenchmarkCase::id)
                .toList();

        assertThat(cases).isNotEmpty();
        assertThat(failures).as("TaskSpec smoke benchmark failures").isEmpty();
    }

    private boolean matches(BenchmarkCase item) {
        var task = planner.plan(item.message(), item.context());
        return task.intent() == AgentTaskIntent.valueOf(item.intent())
                && task.toolName().equals(item.toolName())
                && task.evidenceRequirement() == AgentEvidenceRequirement.valueOf(item.evidenceRequirement());
    }

    private List<BenchmarkCase> loadCases() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/agent-v2/benchmark/task-spec-smoke.json")) {
            assertThat(stream).as("task-spec benchmark resource").isNotNull();
            return objectMapper.readValue(stream, new TypeReference<>() { });
        }
    }

    private record BenchmarkCase(String id, String message, AgentClientContext context, String intent,
                                 String toolName, String evidenceRequirement) {
    }
}

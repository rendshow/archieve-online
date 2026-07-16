package com.danganguan.archive.agent.v2.service.impl;

import com.danganguan.archive.agent.context.AgentContextResolver;
import com.danganguan.archive.agent.dto.AgentClientContext;
import com.danganguan.archive.agent.v2.enums.AgentEvidenceRequirement;
import com.danganguan.archive.agent.v2.enums.AgentTaskIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedAgentTaskPlannerTest {
    private final RuleBasedAgentTaskPlanner planner = new RuleBasedAgentTaskPlanner(new AgentContextResolver());

    @Test
    void shouldRequirePageEvidenceForCourseQuestion() {
        var task = planner.plan("李二牛的高等数学成绩是多少？", folderContext());

        assertThat(task.intent()).isEqualTo(AgentTaskIntent.ANSWER_FROM_DOCUMENTS);
        assertThat(task.toolName()).isEqualTo("DOCUMENT_EVIDENCE_QUERY");
        assertThat(task.evidenceRequirement()).isEqualTo(AgentEvidenceRequirement.PAGE_EVIDENCE_REQUIRED);
        assertThat(task.requestedFields()).contains("课程成绩");
    }

    @Test
    void shouldRouteDuplicateCheckToGovernance() {
        var task = planner.plan("检查这批学籍材料的姓名、学号和文件名是否一致", folderContext());

        assertThat(task.intent()).isEqualTo(AgentTaskIntent.AUDIT_ARCHIVE);
        assertThat(task.toolName()).isEqualTo("GOVERNANCE_INSPECT");
    }

    @Test
    void shouldRejectExpandingFolderScope() {
        var task = planner.plan("查询全校所有档案", folderContext());

        assertThat(task.intent()).isEqualTo(AgentTaskIntent.OUT_OF_SCOPE);
        assertThat(task.clarification()).contains("本页面范围");
    }

    private AgentClientContext folderContext() {
        return new AgentClientContext("ARCHIVE_FOLDER", 1L, "西区/测试", null, null, null, null);
    }
}

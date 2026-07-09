package com.danganguan.archive.agent.intent;

import com.danganguan.archive.agent.enums.AgentIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleBasedIntentDecisionServiceTest {
    private final RuleBasedIntentDecisionService service = new RuleBasedIntentDecisionService();

    @Test
    void shouldFindArchiveByPersonAndMaterial() {
        AgentIntentDecision decision = service.decide("帮我找一下包英夫的成绩单", null);

        assertEquals(AgentIntent.SEARCH_ARCHIVE, decision.intent());
        assertEquals(AgentIntentSubType.FIND_BY_PERSON, decision.subType());
        assertEquals("包英夫", decision.slots().personName());
        assertEquals("成绩单", decision.slots().materialType());
        assertEquals(AgentEvidencePolicy.TITLE_METADATA_FIRST, decision.evidencePolicy());
    }

    @Test
    void shouldAnswerSpecificArchiveInformation() {
        AgentIntentDecision decision = service.decide("2014-JX14•21•481-32包英夫这个文档都记录了哪些信息？", null);

        assertEquals(AgentIntent.DISCUSS_ARCHIVE_INFO, decision.intent());
        assertEquals(AgentIntentSubType.ANSWER_DOCUMENT_INFO, decision.subType());
        assertEquals("2014-JX14•21•481-32", decision.slots().archiveNo());
        assertEquals(AgentEvidencePolicy.CONTENT_REQUIRED, decision.evidencePolicy());
    }

    @Test
    void shouldRequireContentForTeacherSurnameQuestion() {
        AgentIntentDecision decision = service.decide("有哪些档案的学生导师姓欧阳？", null);

        assertEquals(AgentIntent.DISCUSS_ARCHIVE_INFO, decision.intent());
        assertEquals(AgentIntentSubType.ANSWER_RELATION_FIELD, decision.subType());
        assertEquals("欧阳", decision.slots().teacherSurname());
        assertEquals(AgentEvidencePolicy.CONTENT_REQUIRED, decision.evidencePolicy());
    }

    @Test
    void shouldRecognizeYearDistribution() {
        AgentIntentDecision decision = service.decide("当前文件夹有哪几年的档案", null);

        assertEquals(AgentIntent.YEAR_DISTRIBUTION, decision.intent());
        assertEquals(AgentIntentSubType.STAT_YEAR_DISTRIBUTION, decision.subType());
        assertEquals(AgentEvidencePolicy.METADATA_ONLY, decision.evidencePolicy());
    }

    @Test
    void shouldRecognizeCapabilityHelp() {
        AgentIntentDecision decision = service.decide("你能干嘛", null);

        assertEquals(AgentIntent.CAPABILITY_HELP, decision.intent());
        assertEquals(AgentIntentSubType.HELP, decision.subType());
    }
}

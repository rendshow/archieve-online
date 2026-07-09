package com.danganguan.archive.agent.intent;

import com.danganguan.archive.agent.enums.AgentIntent;

import java.util.List;

public record AgentIntentDecision(
        AgentIntent intent,
        AgentIntentSubType subType,
        AgentConfidence confidence,
        AgentScopePolicy scopePolicy,
        AgentEvidencePolicy evidencePolicy,
        AgentQuerySlots slots,
        List<AgentIntentCandidate> candidates,
        String reason,
        String clarificationQuestion
) {
    public static AgentIntentDecision of(AgentIntent intent,
                                         AgentIntentSubType subType,
                                         AgentConfidence confidence,
                                         AgentScopePolicy scopePolicy,
                                         AgentEvidencePolicy evidencePolicy,
                                         AgentQuerySlots slots,
                                         List<AgentIntentCandidate> candidates,
                                         String reason) {
        return new AgentIntentDecision(intent, subType, confidence, scopePolicy, evidencePolicy,
                slots, candidates == null ? List.of() : List.copyOf(candidates), reason, null);
    }
}

package com.danganguan.archive.agent.intent;

import com.danganguan.archive.agent.enums.AgentIntent;

public record AgentIntentCandidate(
        AgentIntent intent,
        AgentIntentSubType subType,
        int score,
        String reason
) {
}

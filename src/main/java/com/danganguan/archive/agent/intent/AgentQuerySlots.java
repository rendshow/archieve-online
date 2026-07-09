package com.danganguan.archive.agent.intent;

import java.util.List;

public record AgentQuerySlots(
        String personName,
        String archiveNo,
        String materialType,
        String year,
        String teacherSurname,
        List<String> keywords
) {
    public static AgentQuerySlots empty() {
        return new AgentQuerySlots(null, null, null, null, null, List.of());
    }
}

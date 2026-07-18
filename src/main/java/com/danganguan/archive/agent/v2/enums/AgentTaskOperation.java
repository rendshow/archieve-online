package com.danganguan.archive.agent.v2.enums;

/** Atomic read-only operations declared by an Agent V2 task plan. */
public enum AgentTaskOperation {
    LOCATE_DOCUMENTS,
    SEARCH_PAGE_TEXT,
    READ_PAGE_FACTS,
    AGGREGATE_SCOPE,
    INSPECT_GOVERNANCE,
    COMPOSE_EVIDENCED_ANSWER,
    REQUEST_CLARIFICATION
}

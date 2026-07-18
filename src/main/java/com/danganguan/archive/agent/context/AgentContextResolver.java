package com.danganguan.archive.agent.context;

import com.danganguan.archive.agent.dto.AgentClientContext;
import com.danganguan.archive.agent.dto.AgentResolvedScope;
import com.danganguan.archive.agent.enums.AgentScopeType;
import com.danganguan.archive.agent.v2.enums.AgentTaskIntent;
import org.springframework.stereotype.Component;

@Component
public class AgentContextResolver {

    public AgentResolvedScope resolve(AgentTaskIntent intent, AgentClientContext context) {
        AgentClientContext safeContext = context == null
                ? new AgentClientContext(null, null, null, null, null, null, null)
                : context;
        if (safeContext.documentId() != null) {
            return new AgentResolvedScope(AgentScopeType.DOCUMENT, safeContext.hallId(), safeContext.folderPath(),
                    safeContext.documentId(), safeContext.taskId(), "CLIENT_CONTEXT", "当前页面限定在单个档案");
        }
        if (safeContext.taskId() != null) {
            return new AgentResolvedScope(AgentScopeType.TASK, safeContext.hallId(), safeContext.folderPath(),
                    null, safeContext.taskId(), "CLIENT_CONTEXT", "当前页面限定在处理任务");
        }
        if (safeContext.folderPath() != null && !safeContext.folderPath().isBlank()) {
            return new AgentResolvedScope(AgentScopeType.FOLDER, safeContext.hallId(), normalizeFolderPath(safeContext.folderPath()),
                    null, null, "CLIENT_CONTEXT", "当前页面限定在文件夹及其子目录");
        }
        if (intent == AgentTaskIntent.SUMMARIZE_SCOPE
                || intent == AgentTaskIntent.AUDIT_ARCHIVE) {
            return new AgentResolvedScope(AgentScopeType.GLOBAL, safeContext.hallId(), null, null, null,
                    "CLIENT_CONTEXT", "当前页面没有文件夹范围，汇总和核验需要用户进入具体范围");
        }
        return new AgentResolvedScope(AgentScopeType.GLOBAL, safeContext.hallId(), null, null, null,
                "CLIENT_CONTEXT", "当前页面允许全局查档");
    }

    private String normalizeFolderPath(String folderPath) {
        String normalized = folderPath.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

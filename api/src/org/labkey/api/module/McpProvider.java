package org.labkey.api.module;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public interface McpProvider
{
    default List<ToolCallback> getMcpTools()
    {
        return List.of();
    }

    default List<McpServerFeatures.SyncPromptSpecification> getMcpPrompts()
    {
        return List.of();
    }

    default List<McpServerFeatures.SyncResourceSpecification> getMcpResources()
    {
        return List.of();
    }
}

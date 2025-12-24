package org.labkey.api.module;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public interface McpProvider
{
    List<ToolCallback> getMcpTools();

    List<McpServerFeatures.SyncPromptSpecification> getMcpPrompts();

    List<McpServerFeatures.SyncResourceSpecification> getMcpResources();
}

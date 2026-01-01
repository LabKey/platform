package org.labkey.api.mcp;


import io.modelcontextprotocol.server.McpServerFeatures;
import jakarta.servlet.http.HttpSession;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.McpProvider;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.function.Supplier;


public interface McpService
{
    static McpService get()
    {
        return ServiceRegistry.get().getService(McpService.class);
    }

    static void setInstance(McpService service)
    {
        ServiceRegistry.get().registerService(McpService.class, service);
    }

    default void register(McpProvider mcp)
    {
        registerTools(mcp.getMcpTools());
        registerPrompts(mcp.getMcpPrompts());
        registerResources(mcp.getMcpResources());
    }

    void registerTools(@NotNull List<ToolCallback> tools);

    void registerPrompts(@NotNull List<McpServerFeatures.SyncPromptSpecification> prompts);

    void registerResources(@NotNull List<McpServerFeatures.SyncResourceSpecification> resources);

    @NotNull List<ToolCallback> listTools();

    @NotNull List<McpServerFeatures.SyncPromptSpecification> listPrompts();

    @NotNull List<McpServerFeatures.SyncResourceSpecification> listResources();

    ChatClient getChat(HttpSession session, String agentName, Supplier<String> systemPromptSupplier);

    record MessageResponse(String markdown, HtmlString html) {}

    MessageResponse sendMessage(ChatClient chat, String message);
    /* </PROTOTYPE> */
}

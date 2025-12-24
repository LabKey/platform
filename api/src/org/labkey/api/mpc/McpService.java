package org.labkey.api.mpc;


import com.google.genai.Chat;
import io.modelcontextprotocol.server.McpServerFeatures;
import jakarta.servlet.http.HttpSession;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.McpProvider;
import org.labkey.api.services.ServiceRegistry;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;


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


    /* <PROTOTYPE> */
    // This probably belongs in its own LLM Service, but it's here for prototyping at the moment
    // This is hard-coded to use Gemini (switch to using Spring-AI wrapper or maybe LangChain4j?)
    // For now there is no more than one chat session per session!  The caller must keep track of prompts sent.

    Chat getChat(HttpSession session);
    String sendMessage(Chat chat, String message);
    /* </PROTOTYPE> */
}

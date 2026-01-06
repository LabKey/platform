package org.labkey.api.mcp;


import io.modelcontextprotocol.server.McpServerFeatures;
import jakarta.servlet.http.HttpSession;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.labkey.api.module.McpProvider;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.function.Supplier;


public interface McpService extends ToolCallbackProvider
{
    static McpService get()
    {
        return ServiceRegistry.get().getService(McpService.class);
    }

    static void setInstance(McpService service)
    {
        ServiceRegistry.get().registerService(McpService.class, service);
    }

    boolean isReady();

    default void register(McpProvider mcp)
    {
        registerTools(mcp.getMcpTools());
        registerPrompts(mcp.getMcpPrompts());
        registerResources(mcp.getMcpResources());
    }

    void registerTools(@NotNull List<ToolCallback> tools);

    void registerPrompts(@NotNull List<McpServerFeatures.SyncPromptSpecification> prompts);

    void registerResources(@NotNull List<McpServerFeatures.SyncResourceSpecification> resources);

    @Override
    ToolCallback @NonNull [] getToolCallbacks();

    ChatClient getChat(HttpSession session, String agentName, Supplier<String> systemPromptSupplier);

    record MessageResponse(String contentType, String text, HtmlString html) {}

    /** get consolidated response (good for many text oriented agents/use-cases) */
    MessageResponse sendMessage(ChatClient chat, String message);

    /** get individual response parts, useful for agents that generate SQL or programmatic responses */
    default List<MessageResponse> sendMessageEx(ChatClient chat, String message)
    {
        return List.of(sendMessage(chat, message));
    }

    /**
     * return an in-memory Vector store for prototyping RAG features
     * CONSIDER: Is it possible to implement VectorStoreRetriever wrapper for SearchService???
     */
    VectorStore getVectorStore();
}

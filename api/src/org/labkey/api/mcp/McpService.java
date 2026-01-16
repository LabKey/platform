package org.labkey.api.mcp;


import io.modelcontextprotocol.server.McpServerFeatures;
import jakarta.servlet.http.HttpSession;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.labkey.api.module.McpProvider;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;
import org.springaicommunity.mcp.provider.resource.SyncMcpResourceProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;


public interface McpService extends ToolCallbackProvider
{
    // marker interface for classes that we will "ingest" using Spring annotations
    interface McpImpl {};

    static @NotNull McpService get()
    {
        return ServiceRegistry.get().getService(McpService.class);
    }

    static void setInstance(McpService service)
    {
        ServiceRegistry.get().registerService(McpService.class, service);
    }

    boolean isReady();


    default void register(McpImpl obj)
    {
        ToolCallback[] tools = ToolCallbacks.from(obj);
        if (null != tools && tools.length > 0)
            registerTools(Arrays.asList(tools));

        List<McpServerFeatures.SyncResourceSpecification> ret;
        var resources = new SyncMcpResourceProvider(List.of(obj)).getResourceSpecifications();
        if (null != resources && !resources.isEmpty())
            registerResources(resources);
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

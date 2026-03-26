package org.labkey.api.mcp;


import io.modelcontextprotocol.server.McpServerFeatures;
import jakarta.servlet.http.HttpSession;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;
import org.labkey.api.writer.ContainerUser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.provider.resource.SyncMcpResourceProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * This service lets you expose functionality over the MCP protocol (only simple http for now).  This allows
 * external chat sessions to pull information from LabKey Server.  These methods are also made available
 * to chat session hosted by LabKey (see AbstractAgentAction).
 * <p></p>
 * These calls are not security checked.  Any tools registered here must check user permissions.  Maybe that
 * will come as we get further along.  Note that the LLM may make callbacks concerning containers other than the
 * current container.  This is an area for investigation.
 */
public interface McpService extends ToolCallbackProvider
{
    // marker interface for classes that we will "ingest" using Spring annotations
    interface McpImpl
    {
        default ContainerUser getContext(ToolContext toolContext)
        {
            User user = (User)toolContext.getContext().get("user");
            Container container = (Container)toolContext.getContext().get("container");
            if (container == null)
                throw new McpException("No container path is set. Ask the user which container/folder they want to use (you can call listContainers to show available options), then call setContainer before retrying.");
            return ContainerUser.create(container, user);
        }

        default User getUser(ToolContext toolContext)
        {
            return (User)toolContext.getContext().get("user");
        }

        // Every MCP resource should call this on every invocation
        default void incrementResourceRequestCount(String resource)
        {
            get().incrementResourceRequestCount(resource);
        }
    }

    static @NotNull McpService get()
    {
        McpService svc = ServiceRegistry.get().getService(McpService.class);
        if (svc == null)
            svc = NoopMcpService.get();
        return svc;
    }

    static void setInstance(McpService service)
    {
        ServiceRegistry.get().registerService(McpService.class, service);
    }

    boolean isReady();

    default void register(McpImpl mcp)
    {
        ToolCallback[] tools = ToolCallbacks.from(mcp);
        if (tools.length > 0)
            registerTools(Arrays.asList(tools), mcp);

        var resources = new SyncMcpResourceProvider(List.of(mcp)).getResourceSpecifications();
        if (null != resources && !resources.isEmpty())
            registerResources(resources);
    }

    void registerTools(@NotNull List<ToolCallback> tools, McpImpl mcp);

    void registerPrompts(@NotNull List<McpServerFeatures.SyncPromptSpecification> prompts);

    void registerResources(@NotNull List<McpServerFeatures.SyncResourceSpecification> resources);

    @Override
    ToolCallback @NonNull [] getToolCallbacks();

    default ChatClient getChat(HttpSession session, String agentName, Supplier<String> systemPromptSupplier)
    {
        return getChat(session, agentName, systemPromptSupplier, true);
    }

    void saveSessionContainer(ToolContext context, Container container);

    void incrementResourceRequestCount(String resource);

    ChatClient getChat(HttpSession session, String agentName, Supplier<String> systemPromptSupplier, boolean createIfNotExists);

    void close(HttpSession session, ChatClient chat);

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

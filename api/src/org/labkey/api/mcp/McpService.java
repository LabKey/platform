package org.labkey.api.mcp;


import io.modelcontextprotocol.server.McpServerFeatures;
import jakarta.servlet.http.HttpSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.ContainerUser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.provider.resource.SyncMcpResourceProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        // These methods throw a NotFoundException listing all missing parameters. Apparently, even though parameters
        // are marked as required, the LLM may not send them or send them with a different name. Best to check them all.
        default void validateRequiredParameters(String k1, @Nullable Object v1)
        {
            validateRequiredParameters(new HashMap<>(){{put(k1, v1);}});
        }

        default void validateRequiredParameters(String k1, @Nullable Object v1, String k2, @Nullable Object v2)
        {
            validateRequiredParameters(new HashMap<>(){{put(k1, v1);put(k2, v2);}});
        }

        default void validateRequiredParameters(String k1, @Nullable Object v1, String k2, @Nullable Object v2, String k3, @Nullable Object v3)
        {
            validateRequiredParameters(new HashMap<>(){{put(k1, v1);put(k2, v2);put(k3, v3);}});
        }

        default void validateRequiredParameters(Map<String, Object> parameters)
        {
            List<String> missing = parameters.entrySet().stream()
                .filter(entry -> entry.getValue() == null || entry.getValue().equals(""))
                .map(Map.Entry::getKey)
                .toList();

            if (!missing.isEmpty())
            {
                if (missing.size() == 1)
                    throw new NotFoundException(missing.getFirst() + " parameter is required");
                else
                    throw new NotFoundException("The following parameters are required: " + StringUtilsLabKey.joinWithConjunction(missing, "and"));
            }
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

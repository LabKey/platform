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

///
/// ### MCP Development Guide
/// `McpService` lets you expose functionality over the MCP protocol (only simple http for now). This allows external
/// chat sessions to pull information from LabKey Server. Exposed functionality is also made available to chat sessions
/// hosted by LabKey (see `AbstractAgentAction``).
///
/// ### Adding a new MCP class
/// 1. Create a new class that implements `McpImpl` (see below) in the appropriate module
/// 2. Register that class in your module `init()` method: `McpService.get().register(new MyMcp())`
/// 3. Add tools and resources
///
/// ### Adding a new MCP tool
/// 1.  In your MCP class, create a new method that returns a String with the name you want to advertise
/// 2.  Annotate it with `@Tool` and provide a detailed description. This description is important since it instructs
///     the LLM client (and the user) in the use of your tool.
/// 3.  Annotate it with `@RequiredPermission(Class&lt;? extends Permission>)` or `@RequiredNoPermission`. **A
///     permission annotation is required, otherwise your tool will not be registered.**
/// 4.  Add `ToolContext` as the first parameter to the method
/// 5.  Add additional required or optional parameters to the method signature, as needed. Note that "required" is the
///     default. Again here, the parameter descriptions are very important. Provide examples.
/// 6.  Use the helper method `getContext(ToolContext)` to retrieve the current `Container` and `User`
/// 7.  Use the helper method `getUser(ToolContext)` in the rare cases where you need just a `User`
/// 8.  Perform additional permissions checking (beyond what the annotations offer), where appropriate
/// 9.  Filter all results to the current container, of course
/// 10. For any error conditions, throw exceptions with detailed information. These will get translated into appropriate
///     failure responses and the LLM client will attempt to correct the problem.
/// 11. For success cases, return a String with a message or JSON content, for example, `JSONObject.toString()`. Spring
///     has some limited ability to convert other objects into JSON strings, but we haven't experimented with that. See
///     `DefaultToolCallResultConverter` and the ability to provide a custom result converter via the `@Tool` annotation.
///
/// At registration time, the framework will:
/// - Ensure all tools are annotated for permissions
/// - Ensure there aren't multiple tools with the same name
///
/// On every tool request, before invoking any tool code, the framework will:
/// - Authenticate the user or provide a guest user
/// - Ensure a container has been set if the tool requires a container
/// - Verify that the user has whatever permissions are required based on the tool's annotation(s)
/// - Verify that every required parameter is non-null and every string parameter is non-blank
/// - Push the container and user into the ToolContext to give the tool access
/// - Increment a metrics counter for that tool
///
/// CoreMcp and QueryMcp have examples of tool declarations.
///
/// ### Adding a new MCP resource
/// 1. In your MCP class, create a new method that returns `ReadResourceResult` with an appropriate name
/// 2. Annotate it with `@McpResource` and provide a uri, mimeType, name, and description
/// 3. Call `incrementResourceRequestCount()` with a short but unique name to increment its metrics count
/// 4. Read the resource, construct a `ReadResourceResult`, and return it.
///
/// No permissions checking is performed on resources. All resources are public.
///
/// CoreMcp and QueryMcp have examples of resource declarations.
///
public interface McpService extends ToolCallbackProvider
{
    // Interface for MCP classes that we will "ingest" using Spring annotations. Provides a few helper methods.
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

/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.writer.ContainerUser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.provider.resource.SyncMcpResourceProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockHttpSession;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

///
/// ### MCP Development Guide
/// `McpService` lets you expose functionality over the MCP protocol (only simple http for now). This allows external
/// chat sessions to pull information from LabKey Server. Exposed functionality is also made available to chat sessions
/// hosted by LabKey (see `AbstractAgentAction`).
///
/// ### Adding a new MCP class
/// 1. Create a new class that implements `McpImpl` (see below) in the appropriate module
/// 2. Register that class in your module's `startup()` method: `McpService.get().register(new MyMcp())`
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
///     default. Again, here, the parameter descriptions are very important. Provide examples of parameter values.
/// 6.  Use the helper method `getContext(ToolContext)` to retrieve the current `Container` and `User`
/// 7.  Use the helper method `getUser(ToolContext)` in the rare cases where you need just a `User`
/// 8.  Perform additional permissions checking (beyond what the annotations offer), where appropriate
/// 9.  Filter all results to the current container, of course
/// 10. For any error conditions, throw exceptions with detailed information. These will get translated into appropriate
///     failure responses, and the LLM client will attempt to correct any problems (hopefully).
/// 11. For success cases, return a String with a message or JSON content, for example, `JSONObject.toString()`. Spring
///     has some limited ability to convert other objects into JSON strings, but we haven't experimented with that. See
///     `DefaultToolCallResultConverter` and the ability to provide a custom result converter via the `@Tool` annotation.
///
/// At registration time, the framework will:
/// - Ensure all tools are annotated for permissions
/// - Ensure there are not multiple tools with the same name
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
    String VECTOR_SCHEMA = "vector_indexes"; // for pgvector extension, see core-26.005-26.006.sql

    Logger LOG = LogHelper.getLogger(McpService.class, "MCP registration exceptions");
    String ENABLE_MCP_SERVER_FLAG = "enableMcpServer";
    String ENABLE_AI_FEATURES = "enableAIFeatures";

    // Interface for MCP classes that we will "ingest" using Spring annotations. Provides a few helper methods.
    interface McpImpl
    {
        default ContainerUser getContext(ToolContext toolContext)
        {
            User user = getUser(toolContext);
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

    default boolean isEnabled()
    {
        return OptionalFeatureService.get().isFeatureEnabled(ENABLE_MCP_SERVER_FLAG);
    }

    default boolean isAIFeaturesEnabled()
    {
        return OptionalFeatureService.get().isFeatureEnabled(ENABLE_AI_FEATURES);
    }

    boolean isReady();

    boolean isModelReady();

    // Convenience for in-product AI features: the AI feature flag is on, the service is ready, and the model is ready.
    default boolean isAIFeaturesReady()
    {
        return isAIFeaturesEnabled() && isReady() && isModelReady();
    }

    // Register MCPs in Module.startup()
    default void register(McpImpl mcp)
    {
        try
        {
            ToolCallback[] tools = ToolCallbacks.from(mcp);
            if (tools.length > 0)
                registerTools(Arrays.asList(tools), mcp);

            var resources = new SyncMcpResourceProvider(List.of(mcp)).getResourceSpecifications();
            if (null != resources && !resources.isEmpty())
                registerResources(resources);
        }
        catch (NoSuchMethodError t)
        {
            LOG.error("You likely need to do a clean build of API! Exception while registering an MCP implementation.", t);
        }
    }

    void registerTools(@NotNull List<ToolCallback> tools, McpImpl mcp);

    void registerPrompts(@NotNull List<McpServerFeatures.SyncPromptSpecification> prompts);

    void registerResources(@NotNull List<McpServerFeatures.SyncResourceSpecification> resources);

    @Override
    ToolCallback @NotNull [] getToolCallbacks();

    default ChatClient getChat(HttpSession session, String conversationName, Supplier<String> systemPromptSupplier)
    {
        return getChat(session, conversationName, systemPromptSupplier, true);
    }

    void saveSessionContainer(ToolContext context, Container container);

    void incrementResourceRequestCount(String resource);

    default ChatClient getChat(HttpSession session, String conversationName, Supplier<String> systemPromptSupplier, boolean createIfNotExists)
    {
        return getChat(session, conversationName, systemPromptSupplier, createIfNotExists, null);
    }

    /** @param toolNames the registered tools this chat may offer the model, or null for all of them */
    ChatClient getChat(HttpSession session, String conversationName, Supplier<String> systemPromptSupplier, boolean createIfNotExists, @Nullable Set<String> toolNames);

    void close(HttpSession session, ChatClient chat);

    int MAX_ISSUED_CONVERSATION_IDS = 16;

    /** The chat holding an agent conversation's model memory. */
    default ChatClient getConversationChat(HttpSession session, String agentName, GUID conversationId, Supplier<String> systemPromptSupplier, boolean createIfNotExists, @Nullable Set<String> toolNames)
    {
        return getChat(session, agentName + ":" + conversationId, systemPromptSupplier, createIfNotExists, toolNames);
    }

    /** Issues a new conversation id to this session for the named agent. */
    default @NotNull GUID issueConversationId(HttpSession session, String agentName)
    {
        GUID conversationId = new GUID();
        Map<GUID, String> issued = getIssuedConversationIds(session);
        synchronized (issued)
        {
            issued.put(conversationId, agentName);
            // Evict this agent's oldest ids so one agent can't push out another's
            long count = issued.values().stream().filter(agentName::equals).count();
            for (var it = issued.values().iterator(); count > MAX_ISSUED_CONVERSATION_IDS && it.hasNext(); )
            {
                if (agentName.equals(it.next()))
                {
                    it.remove();
                    count--;
                }
            }
        }
        return conversationId;
    }

    /** The agent that issued this conversation id to this session, or null if none did. */
    default @Nullable String getIssuingAgentName(HttpSession session, GUID conversationId)
    {
        Map<GUID, String> issued = getIssuedConversationIds(session);
        synchronized (issued)
        {
            return issued.get(conversationId);
        }
    }

    /** The conversation id if this session was issued it by the named agent; never issues one. */
    default @Nullable GUID resolveIssuedConversationId(HttpSession session, String agentName, @Nullable String supplied)
    {
        if (!GUID.isGUID(supplied))
            return null;

        // Only honor an id this agent previously issued this session. This prevents a same-session caller from
        // splicing into another conversation by guessing or replaying a GUID.
        GUID candidate = new GUID(supplied);
        return agentName.equals(getIssuingAgentName(session, candidate)) ? candidate : null;
    }

    /** True while the model still holds this conversation's memory. */
    default boolean isConversationOpen(HttpSession session, String agentName, GUID conversationId)
    {
        return null != getConversationChat(session, agentName, conversationId, () -> "", false, null);
    }

    /** Clears the model's memory, the issued id, and the display transcript. */
    default void closeConversation(HttpSession session, User user, String agentName, GUID conversationId)
    {
        ChatClient chat = getConversationChat(session, agentName, conversationId, () -> "", false, null);
        if (null != chat)
            close(session, chat);
        Map<GUID, String> issued = getIssuedConversationIds(session);
        synchronized (issued)
        {
            issued.remove(conversationId, agentName);
        }
        ChatHistoryService.get().deleteChatHistory(session, user, conversationId);
    }

    /** Conversation id to issuing agent name, in issue order; synchronize on it. */
    private static Map<GUID, String> getIssuedConversationIds(HttpSession session)
    {
        return SessionHelper.getAttribute(session, "issuedConversationIds", LinkedHashMap::new);
    }

    record MessageResponse(String contentType, String text, HtmlString html) {}

    record VectorDocument(String id, String text, Map<String, Object> metadata) {}

    /**
     * get a consolidated response (good for many text-oriented agents/use-cases)
     * @throws GuardrailException when a guardrail blocks the exchange, after dropping it so it can't block later turns
     */
    MessageResponse sendMessage(ChatClient chat, String message);

    /**
     * get individual response parts, useful for agents that generate SQL or programmatic responses
     * @throws GuardrailException when a guardrail blocks the exchange, after dropping it so it can't block later turns
     */
    default List<MessageResponse> sendMessageEx(ChatClient chat, String message)
    {
        return List.of(sendMessage(chat, message));
    }

    /**
     * Collapses the latest {@code count + 1} exchanges in the chat's memory into one: the first exchange's user
     * message followed by the latest reply. A last exchange that got no reply or a blank one is dropped.
     */
    void squashExchanges(ChatClient chat, int count);

    /**
     * return an in-memory Vector store for prototyping RAG features
     * CONSIDER: Is it possible to implement VectorStoreRetriever wrapper for SearchService???
     */
    VectorStore getVectorStore();

    /** Returns true if the vector store exists and contains at least one document. */
    boolean isVectorStorePopulated(@NotNull VectorStore vs);

    /**
     * Adds documents to the vector store, automatically splitting any document whose token
     * count exceeds the embedding model's input limit. Prefer this over
     * {@code getVectorStore().add(...)} for indexing — it prevents the
     * {@code IllegalArgumentException} that {@code TokenCountBatchingStrategy} throws on
     * oversized inputs.
     */
    void addDocuments(List<VectorDocument> documents);

    void saveVectorStore();

    /** Drop and recreate the vector store table. Use when the embedding model has changed and dimensions no longer match. */
    void resetVectorStore();

    default HttpView<Object> getAssistantStatusView()
    {
        return new HtmlView(HtmlString.of("LabKey Assistant features are not available."));
    }

    class TestCase extends Assert
    {
        private static final String AGENT = "TestAgent";

        @Test
        public void testResolveIssuedConversationId()
        {
            McpService svc = NoopMcpService.get();
            HttpSession session = new MockHttpSession();
            GUID issued = svc.issueConversationId(session, AGENT);
            GUID unknown = new GUID();

            assertEquals(issued, svc.resolveIssuedConversationId(session, AGENT, issued.toString()));
            assertNull("Another agent's id", svc.resolveIssuedConversationId(session, "OtherAgent", issued.toString()));
            assertNull("Unknown id", svc.resolveIssuedConversationId(session, AGENT, unknown.toString()));
            assertNull("Must never issue an id", svc.resolveIssuedConversationId(session, AGENT, unknown.toString()));
            assertNull("Invalid id", svc.resolveIssuedConversationId(session, AGENT, "not-a-guid"));
            assertNull("Missing id", svc.resolveIssuedConversationId(session, AGENT, null));

            assertEquals(AGENT, svc.getIssuingAgentName(session, issued));
            assertNull("Unknown id", svc.getIssuingAgentName(session, unknown));
        }

        @Test
        public void testIssuedConversationIdLimit()
        {
            McpService svc = NoopMcpService.get();
            HttpSession session = new MockHttpSession();
            GUID oldest = svc.issueConversationId(session, AGENT);
            GUID otherAgent = svc.issueConversationId(session, "OtherAgent");
            for (int i = 0; i < MAX_ISSUED_CONVERSATION_IDS - 1; i++)
                svc.issueConversationId(session, AGENT);
            GUID newest = svc.issueConversationId(session, AGENT);

            assertNull("Oldest id evicted", svc.resolveIssuedConversationId(session, AGENT, oldest.toString()));
            assertNotNull("Newer id kept", svc.resolveIssuedConversationId(session, AGENT, newest.toString()));
            assertEquals("Limit is per agent", otherAgent, svc.resolveIssuedConversationId(session, "OtherAgent", otherAgent.toString()));
        }
    }
}

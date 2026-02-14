package org.labkey.core.mpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.labkey.api.collections.CopyOnWriteHashMap;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.ai.chat.messages.MessageType.ASSISTANT;


public class McpServiceImpl implements McpService
{
    public static final String MESSAGE_ENDPOINT = "/_mcp/message";
    public static final String SSE_ENDPOINT = "/_mcp/sse";
    private static final Logger LOG = LogHelper.getLogger(McpServiceImpl.class, "MCP services");

    private final CopyOnWriteHashMap<String, ToolCallback> toolMap = new CopyOnWriteHashMap<>();
    private final CopyOnWriteHashMap<String, McpServerFeatures.SyncPromptSpecification> promptMap = new CopyOnWriteHashMap<>();
    private final CopyOnWriteHashMap<String, McpServerFeatures.SyncResourceSpecification> resourceMap = new CopyOnWriteHashMap<>();

    private final _McpServlet mcpServlet = new _McpServlet(JsonUtil.DEFAULT_MAPPER, MESSAGE_ENDPOINT, SSE_ENDPOINT);
    private final ChatMemoryRepository chatMemoryRepository = new InMemoryChatMemoryRepository();
    private VectorStore vectorStore = null;
    private boolean serverReady = false;

    private final _ModelProvider modelProvider;
    private final _ModelProvider embeddingProvider;


    public static McpServiceImpl get()
    {
        return (McpServiceImpl) McpService.get();
    }

    public McpServiceImpl()
    {
        _ModelProvider model = null;
        _ModelProvider embedding = null;
        if (isNotBlank(System.getenv("CLAUDE_API_KEY")))
        {
            model = new _ClaudeProvider();
        }
        if (isNotBlank(System.getenv("OPENAI_API_KEY")))
        {
            var openai = new _ChatGptProvider();
            if (null == embedding)
                embedding = openai;
            if (null == model)
                model = openai;
        }
        if (isNotBlank(System.getenv("GEMINI_API_KEY")))
        {
            var gemini = new _GeminiProvider();
            if (null == embedding)
                embedding = gemini;
            if (null == model)
                model = gemini;
        }
        modelProvider = model;
        embeddingProvider = embedding;
    }


    /**
     * Called by CoreModule.registerServlets()
     * The servlet will return SC_SERVICE_UNAVAILABLE until startMcpServer() is called
     */
    public void registerServlets(ServletContext servletCtx)
    {
        var mcpServletDynamic = servletCtx.addServlet("mcpServlet", mcpServlet);
        mcpServletDynamic.setAsyncSupported(true);
        mcpServletDynamic.addMapping(MESSAGE_ENDPOINT + "/*");
        mcpServletDynamic.addMapping(SSE_ENDPOINT + "/*");
    }


    public void startMpcServer()
    {
        if (null  == modelProvider)
        {
            return;
        }
        vectorStore = createVectorStore();
        mcpServlet.startMcpServer();
        serverReady = true;
    }


    @Override
    public boolean isReady()
    {
        return serverReady;
    }


    @Override
    public void registerTools(@NotNull List<ToolCallback> tools)
    {
        tools.forEach(tool -> toolMap.put(tool.getToolDefinition().name(), new _LoggingToolCallback(tool)));
    }

    @Override
    public void registerPrompts(@NotNull List<McpServerFeatures.SyncPromptSpecification> prompts)
    {
        prompts.forEach(prompt -> promptMap.put(prompt.prompt().name(), prompt));
    }

    @Override
    public void registerResources(@NotNull List<McpServerFeatures.SyncResourceSpecification> resources)
    {
        resources.forEach(resource -> resourceMap.put(resource.resource().name(), resource));
    }


    @Override
    public ToolCallback @NonNull [] getToolCallbacks()
    {
        return toolMap.values().toArray(new ToolCallback[0]);
    }


    public List<McpSchema.Tool> tools()
    {
        McpJsonMapper mapper = McpJsonMapper.getDefault();
        return toolMap.values().stream().map(ToolCallback::getToolDefinition).map(td ->
                McpSchema.Tool.builder()
                        .name(td.name())
                        .description(td.description())
                        .inputSchema(mapper, td.inputSchema())
                        .build()
        ).toList();
    }

    private class _McpServlet extends HttpServlet // wraps HttpServletSseServerTransportProvider
    {
        HttpServletStreamableServerTransportProvider transportProvider;
        McpSyncServer mcpServer = null;

        _McpServlet(ObjectMapper objectMapper, String messageEndpoint, String sseEndpoint)
        {
            transportProvider = HttpServletStreamableServerTransportProvider.builder()
                    .jsonMapper(McpJsonMapper.getDefault())
                    .mcpEndpoint(messageEndpoint)
                    .build();
        }

        void startMcpServer()
        {
            List<McpServerFeatures.SyncToolSpecification> tools = Arrays.stream(getToolCallbacks()).map(McpToolUtils::toSyncToolSpecification).toList();
            List<McpServerFeatures.SyncResourceSpecification> resources = new ArrayList<>(resourceMap.values());

            mcpServer = McpServer.sync(transportProvider)
                    .tools(tools)
                    .resources(resources)
//                    .capabilities(new McpSchema.ServerCapabilities())
                    .build();
            ContextListener.addShutdownListener(new _ShutdownListener());
        }

        @Override
        public void service(ServletRequest sreq, ServletResponse sres) throws ServletException, IOException
        {
            if (!(sreq instanceof HttpServletRequest req) || !(sres instanceof HttpServletResponse res))
            {
                // how to set error???
                throw new ServletException("non-HTTP request");
            }

            if (null == mcpServer)
            {
                res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }

            if ("POST".equals(req.getMethod()))
            {
                if (null == req.getParameter("sessionId") && null == req.getSession(true).getAttribute("McpServiceImpl#mcpSessionId"))
                {
                    // USE SSE endpoint to get a sessionId
                    MockHttpServletRequest mockRequest = new MockHttpServletRequest(req.getServletContext(), "GET", SSE_ENDPOINT);
                    mockRequest.setAsyncSupported(true);
                    MockHttpServletResponse mockResponse = new MockHttpServletResponse();
                    transportProvider.service(mockRequest, mockResponse);
                    String body = new String(mockResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
                    String mcpSessionId = StringUtils.substringBetween(body, "sessionId=", "\n");
                    req.getSession(true).setAttribute("McpServiceImpl#mcpSessionId", mcpSessionId);
                    mockRequest.close();
                    mockResponse.getOutputStream().close();
                }

                req = new HttpServletRequestWrapper(req)
                {
                    @Override
                    public String getParameter(String name)
                    {
                        var ret = super.getParameter(name);
                        if (null == ret && "sessionId".equals(name))
                            return String.valueOf(Objects.requireNonNull(((HttpServletRequest) getRequest()).getSession(true).getAttribute("McpServiceImpl#mcpSessionId")));
                        return ret;
                    }
                };
            }
            transportProvider.service(req, res);
        }

        public Mono<Void> closeGracefully()
        {
            if (null != transportProvider)
                return transportProvider.closeGracefully();
            return Mono.empty();
        }
    }


    // ShutdownListener

    class _ShutdownListener implements ShutdownListener
    {
        @Override
        public String getName()
        {
            return "MpcService";
        }


        Mono<Void> closing = null;

        @Override
        public void shutdownPre()
        {
            closing = mcpServlet.closeGracefully();
            saveVectorDatabase();
        }

        @Override
        public void shutdownStarted()
        {
            if (null == closing)
                closing = mcpServlet.closeGracefully();
            closing.block(Duration.ofSeconds(5));
        }
    }


    /** Delegating wrapper that logs vector store similarity searches */
    private static class _LoggingVectorStore implements VectorStore
    {
        private final VectorStore delegate;

        _LoggingVectorStore(VectorStore delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void add(List<Document> documents)
        {
            delegate.add(documents);
        }

        @Override
        public void delete(Filter.Expression filterExpression)
        {
            delegate.delete(filterExpression);
        }

        @Override
        public void delete(List<String> idList)
        {
            delegate.delete(idList);
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request)
        {
            LOG.info("Vector store search: query=\"{}\"", request.getQuery());
            List<Document> results = delegate.similaritySearch(request);
            if (results.isEmpty())
            {
                LOG.info("Vector store search returned no results");
            }
            else
            {
                LOG.info("Vector store search returned {} result(s):", results.size());
                for (Document doc : results)
                {
                    String content = doc.getText();
                    String snippet = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                    LOG.info("  - [{}] {}", doc.getMetadata(), snippet);
                }
            }
            return results;
        }

        @Override
        public String getName()
        {
            return delegate.getName();
        }
    }


    @Override
    public ChatClient getChat(HttpSession session, String agentName, Supplier<String> systemPromptSupplier, boolean createIfNotExists)
    {
        if (!serverReady)
            return null;

        String sessionKey = ChatClient.class.getName() + "#" + agentName;
        if (createIfNotExists)
        {
            return SessionHelper.getAttribute(session, sessionKey, () ->
                    {
                        var springClient = createSpringChat(session, agentName, systemPromptSupplier);
                        return new _ChatClient(springClient, sessionKey);
                    });
        }
        return SessionHelper.getAttribute(session, sessionKey, null);
    }

    private ChatClient createSpringChat(HttpSession session, String agentName, Supplier<String> systemPromptSupplier)
    {
        String systemPrompt = systemPromptSupplier.get();
        String conversationId = session.getId() + ":" + agentName;
        List<Advisor> advisors = new ArrayList<>();

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(100)
                .chatMemoryRepository(chatMemoryRepository)
                .build();

        MessageChatMemoryAdvisor chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(conversationId)
                .build();
        advisors.add(chatMemoryAdvisor);

        VectorStore vs = getVectorStore();
        if (null != vs)
            advisors.add(QuestionAnswerAdvisor.builder(new _LoggingVectorStore(vs)).build());

        return ChatClient.builder(modelProvider.getChatModel())
                .defaultOptions(modelProvider.getChatOptions())
                .defaultAdvisors(advisors)
                .defaultSystem(systemPrompt)
                .build();
    }

    private class _ChatClient implements ChatClient
    {
        final ChatClient springClient;
        final String key;
        _ChatClient(ChatClient client, String key)
        {
            this.springClient = client;
            this.key = key;
        }

        @Override
        public ChatClientRequestSpec prompt()
        {
            return springClient.prompt();
        }

        @Override
        public ChatClientRequestSpec prompt(String content)
        {
            return springClient.prompt(content);
        }

        @Override
        public ChatClientRequestSpec prompt(Prompt prompt)
        {
            return springClient.prompt(prompt);
        }

        @Override
        public Builder mutate()
        {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void close(HttpSession session, ChatClient chat)
    {
        if (null == chat)
            return;
        session.removeAttribute(((_ChatClient)chat).key);
    }

    @Override
    public MessageResponse sendMessage(ChatClient chatSession, String message)
    {
        try
        {
            var callResponse = chatSession
                    .prompt(message)
                    .toolContext(McpContext.get().getToolContext().getContext())
                    .call();
            StringBuilder sb = new StringBuilder();
            for (Generation result : callResponse.chatResponse().getResults())
            {
                var output = result.getOutput();
                if (ASSISTANT == output.getMessageType())
                {
                    sb.append(output.getText());
                    sb.append("\n\n");
                }
            }
            String md = sb.toString().strip();
            HtmlString html = HtmlString.unsafe(MarkdownService.get().toHtml(md));
            return new MessageResponse("text/markdown", md, html);
        }
        catch (java.util.NoSuchElementException x)
        {
            // Spring AI GoogleGenAiChatModel bug: empty candidates cause NoSuchElementException
            // https://github.com/spring-projects/spring-ai/issues/4556
            LOG.warn("Empty response from chat model (likely a filtered or empty candidate)", x);
            return new MessageResponse("text/plain", "The model returned an empty response. Please try resubmitting and, if the problem continues, rephrase your question/prompt.", HtmlString.of("The model returned an empty response. Please try rephrasing your question."));
        }
    }

    @Override
    public List<MessageResponse> sendMessageEx(ChatClient chatSession, String message)
    {
        if (isBlank(message))
            return List.of();
        try
        {
            var callResponse = chatSession
                    .prompt(message)
                    .toolContext(McpContext.get().getToolContext().getContext())
                    .call();
            List<MessageResponse> ret = new ArrayList<>();
            for (Generation result : callResponse.chatResponse().getResults())
            {
                var output = result.getOutput();
                if (ASSISTANT == output.getMessageType())
                {
                    String md = output.getText();
                    HtmlString html = HtmlString.unsafe(MarkdownService.get().toHtml(md));
                    ret.add(new MessageResponse("text/markdown", md, html));
                }
            }
            return ret;
        }
        catch (NoSuchElementException x)
        {
            // Spring AI GoogleGenAiChatModel bug: empty candidates cause NoSuchElementException
            // https://github.com/spring-projects/spring-ai/issues/4556
            LOG.warn("Empty response from chat model (likely a filtered or empty candidate)", x);
            return List.of(new MessageResponse("text/plain", "The model returned an empty response. Please try rephrasing your question.", HtmlString.of("The model returned an empty response. Please try rephrasing your question.")));
        }
        catch (ConcurrentModificationException x)
        {
            // This can happen when the vector store is still loading, typically a problem shortly after startup
            // Should do better synchronization or state checking
            LOG.warn("Vector store not ready", x);
            return List.of(new MessageResponse("text/plain", "Vector store likely not ready yet. Try again.", HtmlString.of("Vector store likely not ready yet. Try again.")));
        }
    }


    @Override
    public VectorStore getVectorStore()
    {
        return !serverReady ? null : vectorStore;
    }


    VectorStore createVectorStore()
    {
        SimpleVectorStore ret = null;

        try
        {
            EmbeddingModel embeddingModel = embeddingProvider.createEmbeddingModel();
            if (null == embeddingModel)
                return null;

            ret = SimpleVectorStore.builder(embeddingModel).build();

            var savedFile = FileUtil.getTempDirectoryFileLike().resolveChild("VectorStore.database");
            if (savedFile.exists())
            {
                try
                {
                    ret.load(savedFile.toNioPathForRead().toFile());
                }
                catch (Exception x)
                {
                    LogHelper.getLogger(McpServiceImpl.class,"mcp service")
                            .error("error restoring saved vectordb: " + savedFile.toNioPathForRead(), x);
                }
            }
        }
        catch (Exception x)
        {
            LOG.error("Can't create vector store", x);
        }

        return ret;
    }

    void saveVectorDatabase()
    {
        SimpleVectorStore vectorStore = (SimpleVectorStore)getVectorStore();
        if (null == vectorStore)
            return;

        var db = FileUtil.getTempDirectoryFileLike().resolveChild("VectorStore.database");
        try
        {
            vectorStore.save(db.toNioPathForRead().toFile());
        }
        catch (Exception x)
        {
            LOG.error("Can't save vector store", x);
        }
    }


    interface _ModelProvider
    {
        String getModel();

        String getEmbeddingModel();

        ChatOptions getChatOptions();

        ChatModel getChatModel();

        EmbeddingModel createEmbeddingModel();
    }


    class _GeminiProvider implements _ModelProvider
    {
        final Object _initClientLock = new Object();
        Client _genAiClient = null;

        @Override
        public String getModel()
        {
//            return "gemini-2.5-flash";
            // gemini-2.5-flash
            // gemini-2.5-pro
            // gemini-3-flash-preview
            return "gemini-3-pro-preview";
        }

        @Override
        public String getEmbeddingModel()
        {
            return "gemini-embedding-001";
        }

        Client getLlmClient()
        {
            synchronized (_initClientLock)
            {
                if (null == _genAiClient)
                {
                    ClientOptions clientOptions = ClientOptions.builder()
                            .build();
                    _genAiClient = Client.builder()
                            .clientOptions(clientOptions)
                            .build();
                }

                return _genAiClient;
            }
        }

        public GoogleGenAiChatOptions getChatOptions()
        {
            GoogleGenAiChatOptions chatOptions = GoogleGenAiChatOptions.builder()
                    .model(getModel())
                    .toolCallbacks(getToolCallbacks())
                    .build();
            return chatOptions;
        }

        public ChatModel getChatModel()
        {
            Client genAiClient = getLlmClient();
            GoogleGenAiChatOptions chatOptions = getChatOptions();

            ChatModel chatModel = GoogleGenAiChatModel.builder()
                    .genAiClient(genAiClient)
                    .defaultOptions(chatOptions)
                    .build();
            return chatModel;
        }

        @Override
        public EmbeddingModel createEmbeddingModel()
        {
            ClientOptions clientOptions = ClientOptions.builder()
                    .build();
            Client client = Client.builder() // not shared with getLlmClient() ??? maybe causing problems?
                    .clientOptions(clientOptions)
                    .build();
            GoogleGenAiEmbeddingConnectionDetails connectionDetails = GoogleGenAiEmbeddingConnectionDetails.builder()
                    .genAiClient(client)
                    .build();
            GoogleGenAiTextEmbeddingOptions embeddingOptions = GoogleGenAiTextEmbeddingOptions.builder()
                    .model(getEmbeddingModel())
                    .build();
            EmbeddingModel embeddingModel;
            embeddingModel = new GoogleGenAiTextEmbeddingModel(connectionDetails, embeddingOptions);
            return embeddingModel;
        }
    }


    private static class _LoggingToolCallback implements ToolCallback
    {
        private final ToolCallback delegate;

        _LoggingToolCallback(ToolCallback delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition()
        {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata()
        {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput)
        {
            LOG.info("MCP tool invoked: {}", delegate.getToolDefinition().name());
            return delegate.call(toolInput);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext)
        {
            LOG.info("MCP tool invoked: {}", delegate.getToolDefinition().name());
            return delegate.call(toolInput, toolContext);
        }
    }


    class _ClaudeProvider implements _ModelProvider
    {
        @Override
        public String getModel()
        {
            return "claude-sonnet-4-5-20250929";
        }

        @Override
        public String getEmbeddingModel()
        {
            // NYI in spring-ai -- need to use a different service (or Claude java library)
            // return "voyage-3.5-lite";
            return null;
        }

        public AnthropicChatOptions getChatOptions()
        {
            AnthropicChatOptions chatOptions = AnthropicChatOptions.builder()
                    .model(getModel())
                    .toolCallbacks(getToolCallbacks())
                    .build();
            return chatOptions;
        }

        public AnthropicChatModel getChatModel()
        {
            AnthropicChatOptions chatOptions = getChatOptions();
            AnthropicApi api = AnthropicApi.builder()
                    .apiKey(System.getenv("CLAUDE_API_KEY"))
                    .build();
            AnthropicChatModel chatModel = AnthropicChatModel.builder()
                    .anthropicApi(api)
                    .build();
            return chatModel;
        }

        @Override
        public EmbeddingModel createEmbeddingModel()
        {
            return null;
        }
    }

    class _ChatGptProvider implements _ModelProvider
    {
        @Override
        public String getModel()
        {
            return "gpt-4o";
        }

        @Override
        public String getEmbeddingModel()
        {
            return "text-embedding-3-small";
        }

        @Override
        public OpenAiChatOptions getChatOptions()
        {
            return OpenAiChatOptions.builder()
                    .model(getModel())
                    .toolCallbacks(getToolCallbacks())
                    .build();
        }

        @Override
        public OpenAiChatModel getChatModel()
        {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .build();

            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(getChatOptions())
                    .build();
        }

        @Override
        public EmbeddingModel createEmbeddingModel()
        {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .build();

            OpenAiEmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
                    .model(getEmbeddingModel())
                    .build();

            return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, embeddingOptions);
        }
    }
}

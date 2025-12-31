package org.labkey.core.mpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Chat;
import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
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
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.collections.CopyOnWriteHashMap;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.util.ShutdownListener;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.isBlank;


public class McpServiceImpl implements McpService
{
    public static final String MESSAGE_ENDPOINT = "/_mcp/message";
    public static final String SSE_ENDPOINT = "/_mcp/sse";

    private final CopyOnWriteHashMap<String,ToolCallback> toolMap = new CopyOnWriteHashMap<>();
    private final CopyOnWriteHashMap<String,McpServerFeatures.SyncPromptSpecification> promptMap = new CopyOnWriteHashMap<>();
    private final CopyOnWriteHashMap<String,McpServerFeatures.SyncResourceSpecification> resourceMap = new CopyOnWriteHashMap<>();

    private final ObjectMapper objectMapper = JsonUtil.DEFAULT_MAPPER;
    private final _McpServlet  mcpServlet = new _McpServlet(JsonUtil.DEFAULT_MAPPER, MESSAGE_ENDPOINT, SSE_ENDPOINT);
    private final ChatMemoryRepository chatMemoryRepository = new InMemoryChatMemoryRepository();


    public static McpServiceImpl get()
    {
        return (McpServiceImpl) McpService.get();
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


    public static class HelloWorld
    {
        @Tool(description = "Call this tool when starting a new conversation")
        String hello()
        {
            return "hello world!";
        }

        @Tool(description = "Call this tool when ending a conversation")
        String bye()
        {
            return "bye now";
        }
    }


    static McpServerFeatures.SyncToolSpecification syncTool(ToolCallback tool)
    {
        return McpToolUtils.toSyncToolSpecification(tool, null);
    }


    public void startMpcServer()
    {
        mcpServlet.startMcpServer();
    }


    @Override
    public void registerTools(@NotNull List<ToolCallback> tools)
    {
        tools.forEach(tool -> toolMap.put(tool.getToolDefinition().name(), tool));
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
    public @NotNull List<ToolCallback> listTools()
    {
        return new ArrayList<>(toolMap.values());
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

    @Override
    public @NotNull List<McpServerFeatures.SyncPromptSpecification> listPrompts()
    {
        return List.of();
    }


    @Override
    public @NotNull List<McpServerFeatures.SyncResourceSpecification> listResources()
    {
        return List.of();
    }


    private class _McpServlet extends HttpServlet // wraps HttpServletSseServerTransportProvider
    {
        HttpServletStreamableServerTransportProvider transportProvider = null;
        McpSyncServer mcpServer = null;

        _McpServlet(ObjectMapper objectMapper, String messageEndpoint, String sseEndpoint)
        {
//            transportProvider = HttpServletSseServerTransportProvider.builder()
//                    .jsonMapper(McpJsonMapper.getDefault())
//                    .messageEndpoint(messageEndpoint)
//                    .sseEndpoint(sseEndpoint)
//                    .build();

            transportProvider = HttpServletStreamableServerTransportProvider.builder()
                    .jsonMapper(McpJsonMapper.getDefault())
                    .mcpEndpoint(messageEndpoint)
                    .build();
        }

        void startMcpServer()
        {
            ToolCallback[] toolCallbacks = ToolCallbacks.from(new HelloWorld());
            var tools = Arrays.stream(toolCallbacks).map(McpToolUtils::toSyncToolSpecification).toList();

            mcpServer = McpServer.sync(transportProvider)
                    .tools(tools)
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
                            return String.valueOf(Objects.requireNonNull(((HttpServletRequest)getRequest()).getSession(true).getAttribute("McpServiceImpl#mcpSessionId")));
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

        /*
        @Override
        public Mono<Void> closeGracefully()
        {
            return super.closeGracefully();
        }

        @Override
        public void destroy()
        {
            super.destroy();
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            if (!initialized)
            {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }
            super.doGet(request, response);
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            if (!initialized)
            {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }

            // spring ai requires call to SSE first to get a sessionId????
            if (null == request.getParameter("sessionId"))
            {
                MockHttpServletRequest mockRequest = new MockHttpServletRequest(request.getServletContext(), "GET", request.getRequestURI());
                MockHttpServletResponse mockResponse = new MockHttpServletResponse();
                doGet(mockRequest, mockResponse);
                String body = new String(mockResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
                String sessionId = StringUtils.substringBetween(body, "sessionId\":\"", "\"");
                request.setAttribute("sessionId", sessionId);
                request = new HttpServletRequestWrapper(request)
                {
                    @Override
                    public String getParameter(String name)
                    {
                        if ("sessionId".equals(name))
                            return sessionId;
                        return super.getParameter(name);
                    }
                };
            }

            super.doPost(request, response);
        }

        @Override
        public Mono<Void> notifyClients(String method, Object params)
        {
            return super.notifyClients(method, params);
        }

        @Override
        public void setSessionFactory(McpServerSession.Factory sessionFactory)
        {
            super.setSessionFactory(sessionFactory);
            initialized = true;
        }

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
        {
            super.service(req, resp);
        }

        @Override
        public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException
        {
            super.service(req, res);
        }
 */
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
        }

        @Override
        public void shutdownStarted()
        {
            if (null == closing)
                closing = mcpServlet.closeGracefully();
            closing.block(Duration.ofSeconds(5));
        }
    }



    /* GEMINI CHAT SERVICE */


    String getModel()
    {
        return "gemini-2.5-flash";
//      gemini-2.5-flash-lite is cheaper but it seems to be much worse at SQL than gemini-2.5-flash
//        return "gemini-2.5-flash-lite";
    }


    @Override
    public Chat getChat(HttpSession session)
    {
        return SessionHelper.getAttribute(session, Chat.class.getName(), () -> {
            Client client = new Client();

            List<FunctionDeclaration> fns = new ArrayList<>();
            for (var tc : listTools())
            {
                var inputSchema = Schema.fromJson(tc.getToolDefinition().inputSchema());
                var fd = FunctionDeclaration.builder()
                        .name(tc.getToolDefinition().name())
                        .description(tc.getToolDefinition().description())
                        .parameters(inputSchema);
                fns.add(fd.build());
            }

             GenerateContentConfig config = GenerateContentConfig.builder()
                     .tools( com.google.genai.types.Tool.builder().functionDeclarations(fns))
                     .build();
              Chat chatSession = client.chats.create(getModel(), config);

            return chatSession;
        });
    }

    @Override
    public String sendMessage(Chat chatSession, String message)
    {
        // TODO tool context? org.labkey.api.mpc.McpContext.get();

        GenerateContentResponse response;
        List<FunctionCall> functionCalls;
        int sends = 0;

        response = chatSession.sendMessage(message);
        sends = sends + 1;
        functionCalls = response.functionCalls();

        while (sends < 3 && null != functionCalls && !functionCalls.isEmpty())
        {
            StringBuilder sb = new StringBuilder();
            for (var call : functionCalls)
            {
                if (call.name().isEmpty())
                    break;
                var tool = toolMap.get(call.name().get());
                if (null == tool)   // ERROR?
                    continue;
                var argsMap = call.args().isEmpty() ? Map.of() : call.args().get();
                var argsString = new JSONObject(argsMap);
                String result = tool.call(argsString.toString(), null);
                // TODO add context about call and parameters to response?
                sb.append(result).append("\n\n");
            }
            response = chatSession.sendMessage(sb.toString());
            functionCalls = response.functionCalls();
        }

        // if text is empty and sends > 1 retry the original prompt
        var ret = response.text();
        if (isBlank(ret) && sends > 1)
        {
            response = chatSession.sendMessage(message);
            ret = response.text();
            if (isBlank(ret))
                ret = "Too many tool calls. Try again.";
        }
        return ret;
    }


    // SPRING AI CHAT SERVICE
    @Override
    public ChatClient getChatSpringAi(HttpSession session, String agentName, Supplier<String> systemPromptSupplier)
    {
        return SessionHelper.getAttribute(session, ChatClient.class.getName() + "#" + agentName, () ->
        {
            String systemPrompt = systemPromptSupplier.get();
            String conversationId = session.getId() + ":" + agentName;

            ClientOptions clientOptions = ClientOptions.builder()
                    .build();

            Client genAiClient = Client.builder()
                    .clientOptions(clientOptions)
                    .build();

            GoogleGenAiChatOptions chatOptions = GoogleGenAiChatOptions.builder()
                    .model(getModel())
                    .toolCallbacks(listTools())
                    .build();
            ChatModel chatModel = GoogleGenAiChatModel.builder()
                    .genAiClient(genAiClient)
                    .defaultOptions(chatOptions)
                    .build();
            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .maxMessages(100)
                    .chatMemoryRepository(chatMemoryRepository)
                    .build();
            return ChatClient.builder(chatModel)
                    .defaultOptions(chatOptions)
                    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory)
                            .conversationId(conversationId)
                            .build())
                    .defaultSystem(systemPrompt)
                    .build();
        });
    }


    @Override
    public String sendMessage(ChatClient chatSession, String message)
    {
        String content;
        content = chatSession
                .prompt(message)
                .toolContext(McpContext.get().getToolContext().getContext())
                .call()
                .content();
        return content;
    }
}

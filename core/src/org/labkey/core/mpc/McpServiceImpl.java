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
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CopyOnWriteHashMap;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.HtmlString;
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
import org.springframework.ai.chat.model.Generation;
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
import java.util.Objects;
import java.util.function.Supplier;

import static org.springframework.ai.chat.messages.MessageType.ASSISTANT;


public class McpServiceImpl implements McpService
{
    public static final String MESSAGE_ENDPOINT = "/_mcp/message";
    public static final String SSE_ENDPOINT = "/_mcp/sse";

    private final CopyOnWriteHashMap<String, ToolCallback> toolMap = new CopyOnWriteHashMap<>();
    private final CopyOnWriteHashMap<String, McpServerFeatures.SyncPromptSpecification> promptMap = new CopyOnWriteHashMap<>();
    private final CopyOnWriteHashMap<String, McpServerFeatures.SyncResourceSpecification> resourceMap = new CopyOnWriteHashMap<>();

    private final ObjectMapper objectMapper = JsonUtil.DEFAULT_MAPPER;
    private final _McpServlet mcpServlet = new _McpServlet(JsonUtil.DEFAULT_MAPPER, MESSAGE_ENDPOINT, SSE_ENDPOINT);
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




    // SPRING AI CHAT SERVICE
    @Override
    public ChatClient getChat(HttpSession session, String agentName, Supplier<String> systemPromptSupplier)
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
    public MessageResponse sendMessage(ChatClient chatSession, String message)
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
        return new MessageResponse(md, html);
    }
}

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

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.ai.chat.model.ToolContext;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calls a tool on a remote MCP server. Each call opens its own session, so remote restarts and session expiry need no handling.
 */
public class McpToolProxy
{
    private static final Logger LOG = LogHelper.getLogger(McpToolProxy.class, "MCP tool forwarding");
    private static final McpSchema.Implementation CLIENT_INFO = McpSchema.Implementation.builder("labkey-server-forwarder", "1.0").build();

    // Marks a request as already forwarded so the receiving server never forwards it again
    public static final String FORWARDED_HEADER = "X-LABKEY-MCP-Forwarded";
    public static final String FORWARDED_CONTEXT_KEY = "mcpForwarded";

    private McpToolProxy()
    {
    }

    /**
     * Calls {@code remoteToolName} on the MCP server at {@code remoteBaseUrl} with {@code arguments} and returns its text content
     * (joined, if the tool returned more than one text content block). Throws if the remote server can't be
     * reached or the remote tool itself reports an error.
     */
    public static String forward(@NotNull String remoteBaseUrl, @NotNull String remoteToolName, @NotNull Map<String, Object> arguments)
    {
        var transport = HttpClientStreamableHttpTransport.builder(remoteBaseUrl)
                .connectTimeout(Duration.ofSeconds(10))
                .requestBuilder(HttpRequest.newBuilder().header(FORWARDED_HEADER, "true"))
                .build();

        McpSchema.CallToolResult result;
        try (McpSyncClient client = McpClient.sync(transport).clientInfo(CLIENT_INFO).build())
        {
            try
            {
                client.initialize();
            }
            catch (RuntimeException e)
            {
                throw unreachable(remoteBaseUrl, remoteToolName, e);
            }

            try
            {
                result = client.callTool(McpSchema.CallToolRequest.builder(remoteToolName).arguments(arguments).build());
            }
            catch (McpError e)
            {
                // The remote tool threw, so pass its error through as-is
                LOG.debug("Remote MCP tool '{}' at {} failed: {}", remoteToolName, remoteBaseUrl, e.getMessage());
                throw e;
            }
            catch (RuntimeException e)
            {
                throw unreachable(remoteBaseUrl, remoteToolName, e);
            }
        }

        String text = result.content().stream()
                .filter(content -> content instanceof McpSchema.TextContent)
                .map(content -> ((McpSchema.TextContent) content).text())
                .collect(Collectors.joining("\n"));

        if (Boolean.TRUE.equals(result.isError()))
            throw new McpException("Remote tool '" + remoteToolName + "' at " + remoteBaseUrl + " reported an error: " + text);

        return text;
    }

    public static boolean isForwarded(@NotNull ToolContext toolContext)
    {
        return Boolean.TRUE.equals(toolContext.getContext().get(FORWARDED_CONTEXT_KEY));
    }

    private static McpException unreachable(String remoteBaseUrl, String remoteToolName, RuntimeException e)
    {
        LOG.error("Failed to forward MCP tool call '{}' to {}", remoteToolName, remoteBaseUrl, e);
        return new McpException("Unable to reach " + remoteBaseUrl + " to forward '" + remoteToolName + "': " + e.getMessage());
    }
}
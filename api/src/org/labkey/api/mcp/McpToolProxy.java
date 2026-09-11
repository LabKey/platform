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
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.logging.log4j.Logger;
import org.labkey.api.util.logging.LogHelper;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Proxy for calling an MCP tool on a remote server.
 *
 * A single {@link McpSyncClient} is created lazily and reused for the lifetime of this instance; the MCP
 * initialize handshake only happens once, on the first forwarded call.
 */
public class McpToolProxy
{
    private static final Logger LOG = LogHelper.getLogger(McpToolProxy.class, "MCP tool forwarding");

    private final String remoteBaseUrl;
    private volatile McpSyncClient client;

    public McpToolProxy(String remoteBaseUrl)
    {
        this.remoteBaseUrl = remoteBaseUrl;
    }

    private McpSyncClient getClient()
    {
        McpSyncClient c = client;
        if (c == null)
        {
            synchronized (this)
            {
                c = client;
                if (c == null)
                {
                    var transport = HttpClientStreamableHttpTransport.builder(remoteBaseUrl).build();
                    c = McpClient.sync(transport)
                            .clientInfo(McpSchema.Implementation.builder("labkey-server-forwarder", "1.0").build())
                            .build();
                    c.initialize();
                    client = c;
                }
            }
        }
        return c;
    }

    // Drop the cached client after a failed call, so the next attempt reconnects instead of reusing a dead session
    private synchronized void resetClient()
    {
        if (client != null)
        {
            try
            {
                client.closeGracefully();
            }
            catch (RuntimeException ignore)
            {
                // already broken; nothing to do
            }
            client = null;
        }
    }

    /**
     * Calls {@code remoteToolName} on the remote MCP server with {@code arguments} and returns its text content
     * (joined, if the tool returned more than one text content block). Throws if the remote server can't be
     * reached or the remote tool itself reports an error.
     */
    public String forward(String remoteToolName, Map<String, Object> arguments)
    {
        McpSchema.CallToolResult result;
        try
        {
            result = getClient().callTool(McpSchema.CallToolRequest.builder(remoteToolName).arguments(arguments).build());
        }
        catch (RuntimeException e)
        {
            LOG.error("Failed to forward MCP tool call '{}' to {}", remoteToolName, remoteBaseUrl, e);
            resetClient();
            throw new McpException("Unable to reach " + remoteBaseUrl + " to forward '" + remoteToolName + "': " + e.getMessage());
        }

        String text = result.content().stream()
                .filter(content -> content instanceof McpSchema.TextContent)
                .map(content -> ((McpSchema.TextContent) content).text())
                .collect(Collectors.joining("\n"));

        if (Boolean.TRUE.equals(result.isError()))
            throw new McpException("Remote tool '" + remoteToolName + "' at " + remoteBaseUrl + " reported an error: " + text);

        return text;
    }
}
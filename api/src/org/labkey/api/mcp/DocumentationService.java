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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.URLHelper;
import org.springframework.ai.chat.model.ToolContext;

import java.net.URISyntaxException;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Documentation search/retrieval, backing the {@code searchDocumentation}/{@code retrieveDocument} MCP tools in
 * {@code CoreMcp}. The registered implementation does the work locally and exists only on servers that host the
 * LabKey documentation content and its vector store (currently www.labkey.org); elsewhere, {@link #get(ToolContext)}
 * returns an unregistered implementation that forwards each call to that server. See {@link #isEnabled()}.
 */
public interface DocumentationService
{
    /**
     * The registered local implementation, or null if no module provides one; it serves documentation only where
     * {@link #isEnabled()}. Practically speaking, the local implementation is provided by the Service Tools module,
     * which is deployed on labkey.org (and staging.labkey.org) only.
     **/
    static @Nullable DocumentationService get()
    {
        return ServiceRegistry.get().getService(DocumentationService.class);
    }

    /**
     * The implementation an MCP tool call should use: the local one if enabled, otherwise one that forwards to the
     * configured documentation server. Chosen per call since settings and the forwarded-request guard can change.
     * @throws McpException if neither is available
     */
    static @NotNull DocumentationService get(ToolContext context)
    {
        DocumentationService local = get();
        if (local != null && local.isEnabled())
            return local;

        String documentationServer = AppProps.getInstance().getDocumentationServer();
        // A forwarded call must never be forwarded again
        if (!McpToolProxy.isForwarded(context) && isRemote(documentationServer))
            return new ForwardingDocumentationService(documentationServer);

        throw new McpException("Documentation is not available on this server.");
    }

    private static boolean isRemote(String documentationServer)
    {
        if (isBlank(documentationServer))
            return false;

        try
        {
            // Disallow call back to self. If this looks like a self-reference (www.labkey.org trying to call www.labkey.org) return false.
            return !new URLHelper(documentationServer).getHost().equalsIgnoreCase(new URLHelper(AppProps.getInstance().getBaseServerUrl()).getHost());
        }
        catch (URISyntaxException x)
        {
            return false;
        }
    }

    static void setInstance(DocumentationService impl)
    {
        ServiceRegistry.get().registerService(DocumentationService.class, impl);
    }

    /**
     * True if this server is enabled as the documentation source. The backing optional feature flag is owned by
     * whichever module registers an implementation (currently serviceTools), not this interface, so the flag
     * only exists at all on servers that have that module installed.
     */
    boolean isEnabled();

    /** Returns a JSON string; see CoreMcp's searchDocumentation tool description for the response shape. */
    String searchDocumentation(@NotNull String query, @Nullable Integer topK);

    /** Returns a JSON string; see CoreMcp's retrieveDocument tool description for the response shape. */
    String retrieveDocument(@NotNull String id);
}
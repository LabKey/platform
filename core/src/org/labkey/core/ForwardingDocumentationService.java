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
package org.labkey.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.mcp.DocumentationService;
import org.labkey.api.mcp.McpToolProxy;

import java.util.HashMap;
import java.util.Map;

/** Forwards documentation tool calls to the documentation server's MCP endpoint, whose tools share CoreMcp's names and parameters */
class ForwardingDocumentationService implements DocumentationService
{
    private final String _documentationServer;

    ForwardingDocumentationService(@NotNull String documentationServer)
    {
        _documentationServer = documentationServer;
    }

    /** Created only when forwarding is possible; the feature flag belongs to the local implementation */
    @Override
    public boolean isEnabled()
    {
        return true;
    }

    @Override
    public String searchDocumentation(@NotNull String query, @Nullable Integer topK)
    {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("query", query);
        if (topK != null)
            arguments.put("topK", topK);
        return McpToolProxy.forward(_documentationServer, "searchDocumentation", arguments);
    }

    @Override
    public String retrieveDocument(@NotNull String id)
    {
        return McpToolProxy.forward(_documentationServer, "retrieveDocument", Map.of("id", id));
    }
}

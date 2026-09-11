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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;

/**
 * Local implementation of documentation search/retrieval, backing the {@code searchDocumentation}/
 * {@code retrieveDocument} MCP tools in {@code CoreMcp}. Only registered on servers that host the LabKey
 * documentation content and its vector store (currently www.labkey.org); every other server forwards those
 * tool calls to www.labkey.org instead of calling this service. See {@link #isEnabled()}.
 */
public interface DocumentationService
{
    static @Nullable DocumentationService get()
    {
        return ServiceRegistry.get().getService(DocumentationService.class);
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
    String searchDocumentation(String query, @Nullable Integer topK);

    /** Returns a JSON string; see CoreMcp's retrieveDocument tool description for the response shape. */
    String retrieveDocument(String id);
}
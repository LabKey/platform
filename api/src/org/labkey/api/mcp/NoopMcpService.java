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
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.function.Supplier;

class NoopMcpService implements McpService
{
    private static final McpService INSTANCE = new NoopMcpService();

    static McpService get()
    {
        return INSTANCE;
    }

    @Override
    public boolean isEnabled()
    {
        return false;
    }

    @Override
    public boolean isReady()
    {
        return false;
    }

    @Override
    public void registerTools(@NotNull List<ToolCallback> tools, McpImpl mcp)
    {
    }

    @Override
    public void registerPrompts(@NotNull List<McpServerFeatures.SyncPromptSpecification> prompts)
    {
    }

    @Override
    public void registerResources(@NotNull List<McpServerFeatures.SyncResourceSpecification> resources)
    {
    }

    @Override
    public ToolCallback @NotNull [] getToolCallbacks()
    {
        return new ToolCallback[0];
    }

    @Override
    public void saveSessionContainer(ToolContext context, Container container)
    {
    }

    @Override
    public void incrementResourceRequestCount(String resource)
    {
    }

    @Override
    public ChatClient getChat(HttpSession session, String conversationName, Supplier<String> systemPromptSupplier, boolean createIfNotExists)
    {
        return null;
    }

    @Override
    public void close(HttpSession session, ChatClient chat)
    {
    }

    @Override
    public MessageResponse sendMessage(ChatClient chat, String message)
    {
        return null;
    }

    @Override
    public VectorStore getVectorStore()
    {
        return null;
    }

    @Override
    public void addDocuments(List<VectorDocument> documents)
    {
    }

    @Override
    public void saveVectorStore()
    {
    }

    @Override
    public void resetVectorStore()
    {
    }
}

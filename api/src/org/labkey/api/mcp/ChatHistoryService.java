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

import jakarta.servlet.http.HttpSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.GUID;

import java.util.Date;
import java.util.List;

/**
 * Display transcript of agent conversations, so a client can replay a conversation after navigating. Separate from the
 * model's chat memory, which holds composed prompts and is rewritten by squashing and guardrails.
 */
public interface ChatHistoryService
{
    enum Role { user, assistant, error }

    /**
     * content is the raw prompt (user), the segments JSON array (assistant), or the error text (error). Hidden snippets
     * were added in a container the user can no longer read and have null content.
     */
    record ChatSnippet(int rowId, String agentName, Role role, @Nullable String content, Date created, boolean hidden) {}

    static ChatHistoryService get()
    {
        return ServiceRegistry.get().getService(ChatHistoryService.class);
    }

    static void setInstance(ChatHistoryService impl)
    {
        ServiceRegistry.get().registerService(ChatHistoryService.class, impl);
    }

    /** Rows are deleted when the session ends */
    void addChatSnippet(HttpSession session, User user, Container c, String agentName, GUID conversationId, Role role, String content);

    /**
     * The user's latest snippets, oldest first, with those from containers they can no longer read hidden. Guests share
     * a user id, so callers must also check the session was issued the conversation (McpService.getIssuingAgentName).
     */
    @NotNull List<ChatSnippet> getChatHistory(User user, GUID conversationId);

    /** The id of the container the user's latest prompt in the conversation was sent from; not permission checked */
    @Nullable String getLastPromptContainerId(User user, GUID conversationId);

    void deleteChatHistory(HttpSession session, User user, GUID conversationId);
}

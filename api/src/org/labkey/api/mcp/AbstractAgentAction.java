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
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.labkey.api.action.SpringActionController.ERROR_GENERIC;

/**
 * "Agent" it is too strong a word, but if you want to create a tools-specific chat endpoint, then start here.
 * First implement getServicePrompt() to tell your agent its mission. You can also listen in on the
 * conversation to help your user get the right results.
 */
public abstract class AbstractAgentAction<F extends PromptForm> extends MutatingApiAction<F>
{
    private static final int MAX_PROMPT_CHAR_LENGTH = 4_000;
    // Matches the client's fallback for a failure response without an error
    private static final String DEFAULT_ERROR_MESSAGE = "Request failed.";

    protected GUID conversationId;

    protected abstract String getAgentName();

    protected abstract String getServicePrompt();

    /** The registered tools this agent's chat may offer the model; null offers all of them. */
    protected @Nullable Set<String> getToolNames()
    {
        return null;
    }

    /** Agents with a UI that reloads conversations return true to keep a display transcript in ChatHistoryService. */
    protected boolean recordsHistory()
    {
        return false;
    }

    protected ChatClient getChat(boolean create)
    {
        HttpSession session = getViewContext().getSession();
        return McpService.get().getConversationChat(session, getAgentName(), getConversationId(), this::getServicePrompt, create, getToolNames());
    }

    @Override
    public void validateForm(F form, Errors errors)
    {
        if (!McpService.get().isAIFeaturesReady())
        {
            errors.reject(ERROR_GENERIC, "Agent actions are not available.");
            return;
        }

        String prompt = form.getPrompt();
        if (prompt != null && prompt.length() > MAX_PROMPT_CHAR_LENGTH)
        {
            errors.rejectValue("prompt", ERROR_GENERIC, "Prompt cannot exceed " + MAX_PROMPT_CHAR_LENGTH + " characters.");
            return;
        }

        String supplied = form.getConversationId();
        if (supplied != null && !GUID.isGUID(supplied))
        {
            errors.rejectValue("conversationId", ERROR_GENERIC, "Invalid conversationId");
            return;
        }

        HttpSession session = getViewContext().getSession();
        conversationId = McpService.get().resolveIssuedConversationId(session, getAgentName(), supplied);

        if (null == conversationId)
            conversationId = McpService.get().issueConversationId(session, getAgentName());
    }

    @Override
    public Object execute(PromptForm form, BindException errors) throws Exception
    {
        try (var _ = McpContext.withContext(getViewContext()))
        {
            McpContext.get().put(McpContext.AGENT_NAME_ATTR, getAgentName());
            String prompt = form.getPrompt();
            JSONObject escapeResponse = escapeResponse(prompt);

            if (null != escapeResponse)
                return escapeResponse;

            recordPrompt(prompt);

            // call getChat() after handleEscape()
            ChatClient chatSession = getChat(true);
            if (null == chatSession)
            {
                return recordResponse(new JSONObject(Map.of(
                        "contentType", "text/plain",
                        "response", "Service is not ready yet",
                        "error", "Service is not ready yet",
                        "conversationId", conversationId,
                        "success", Boolean.FALSE)));
            }

            McpService.MessageResponse response = McpService.get().sendMessage(chatSession, prompt);
            var ret = new JSONObject(Map.of("success", Boolean.TRUE, "conversationId", conversationId));
            if (!HtmlString.isBlank(response.html()))
            {
                ret.put("contentType", "text/html");
                ret.put("response", response.html());
            }
            else if (isNotBlank(response.text()))
            {
                ret.put("contentType", response.contentType());
                ret.put("response", response.text());
            }
            else
            {
                ret.put("contentType", "text/plain");
                ret.put("response", "I got nothing");
            }
            return recordResponse(ret);
        }
        catch (ChatException x)
        {
            String message = StringUtils.defaultIfBlank(x.getMessage(), DEFAULT_ERROR_MESSAGE);
            return recordResponse(new JSONObject(Map.of(
                    "error", message,
                    "text", "ERROR: " + message,
                    "conversationId", conversationId,
                    "success", Boolean.FALSE)));
        }
    }

    protected @NotNull JSONObject errorResponse(Exception ex)
    {
        String message = StringUtils.defaultIfBlank(ex.getMessage(), DEFAULT_ERROR_MESSAGE);
        return new JSONObject(Map.of(
                "text", message,
                "error", message,
                "user", getViewContext().getUser().getName(),
                "conversationId", conversationId,
                "success", Boolean.FALSE));
    }

    /** The response to an escape command such as {@code /clear}, or null when the prompt isn't one. */
    protected @Nullable JSONObject escapeResponse(String prompt)
    {
        if (!"/clear".equals(StringUtils.trimToEmpty(prompt)))
            return null;

        McpService.get().closeConversation(getViewContext().getSession(), getUser(), getAgentName(), conversationId);

        // No conversationId, since it was just closed; "reset" tells the client to clear its messages too
        return new JSONObject(Map.of(
                "contentType", "text/plain",
                "text", "OK, let's start over.",
                "reset", Boolean.TRUE,
                "success", Boolean.TRUE));
    }

    /** Call before sending the prompt to the model, so the prompt is recorded even if the turn fails. */
    protected void recordPrompt(String prompt)
    {
        if (recordsHistory())
            addChatSnippet(ChatHistoryService.Role.user, StringUtils.defaultString(prompt));
    }

    /** Call with every response returned after recordPrompt(); returns the response for chaining. */
    protected JSONObject recordResponse(JSONObject response)
    {
        if (recordsHistory())
        {
            Pair<ChatHistoryService.Role, String> snippet = toHistorySnippet(response);
            addChatSnippet(snippet.first, snippet.second);
        }
        return response;
    }

    private void addChatSnippet(ChatHistoryService.Role role, String content)
    {
        ChatHistoryService.get().addChatSnippet(getViewContext().getSession(), getUser(), getContainer(), getAgentName(), conversationId, role, content);
    }

    /** Maps a response to what the client displays: segments for an answer, or the error text for a failure. */
    static Pair<ChatHistoryService.Role, String> toHistorySnippet(JSONObject response)
    {
        if (!response.optBoolean("success"))
            return new Pair<>(ChatHistoryService.Role.error, StringUtils.defaultIfBlank(response.optString("error", null), DEFAULT_ERROR_MESSAGE));

        JSONArray segments = response.optJSONArray("segments");
        if (null == segments)
        {
            segments = new JSONArray();
            String body = StringUtils.defaultIfBlank(response.optString("response", null), response.optString("text", null));
            if (isNotBlank(body))
            {
                if ("text/html".equals(response.optString("contentType")))
                    segments.put(new JSONObject(Map.of("type", "html", "html", body)));
                else
                    segments.put(new JSONObject(Map.of("type", "text", "text", body)));
            }
        }
        return new Pair<>(ChatHistoryService.Role.assistant, segments.toString());
    }

    protected GUID getConversationId()
    {
        return conversationId;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testToHistorySnippet()
        {
            JSONArray segments = new JSONArray().put(new JSONObject(Map.of("type", "html", "html", "<p>Hi</p>")));
            assertSnippet(ChatHistoryService.Role.assistant, segments.toString(), Map.of("success", true, "segments", segments));

            assertSnippet(ChatHistoryService.Role.assistant, "[{\"html\":\"<p>Hi</p>\",\"type\":\"html\"}]",
                    Map.of("success", true, "contentType", "text/html", "response", "<p>Hi</p>"));
            assertSnippet(ChatHistoryService.Role.assistant, "[{\"text\":\"Hi\",\"type\":\"text\"}]",
                    Map.of("success", true, "contentType", "text/markdown", "response", "Hi"));
            assertSnippet(ChatHistoryService.Role.assistant, "[{\"text\":\"Hi\",\"type\":\"text\"}]",
                    Map.of("success", true, "contentType", "text/plain", "text", "Hi"));

            assertSnippet(ChatHistoryService.Role.error, "Boom", Map.of("success", false, "error", "Boom", "text", "ERROR: Boom"));
            assertSnippet(ChatHistoryService.Role.error, DEFAULT_ERROR_MESSAGE, Map.of("success", false, "text", "Boom"));
        }

        private static void assertSnippet(ChatHistoryService.Role role, String content, Map<String, Object> response)
        {
            Pair<ChatHistoryService.Role, String> snippet = toHistorySnippet(new JSONObject(response));
            assertEquals(role, snippet.first);
            if (role == ChatHistoryService.Role.assistant)
                assertEquals(new JSONArray(content).toList(), new JSONArray(snippet.second).toList());
            else
                assertEquals(content, snippet.second);
        }
    }

    protected static void flushHtmlSegment(JSONArray segments, StringBuilder buf, MarkdownService md)
    {
        if (buf.isEmpty())
            return;

        String raw = buf.toString().strip();
        buf.setLength(0);
        if (raw.isEmpty())
            return;

        String html;
        try
        {
            html = md != null ? md.toHtml(raw) : raw;
        }
        catch (Exception x)
        {
            html = raw;
        }

        var validateErrors = new ArrayList<String>();
        PageFlowUtil.validateHtml(html, validateErrors, validateErrors);
        if (!validateErrors.isEmpty())
            throw new RuntimeValidationException("Invalid HTML markup. " + String.join("\n", validateErrors));

        segments.put(new JSONObject(Map.of("type", "html", "html", html)));
    }
}

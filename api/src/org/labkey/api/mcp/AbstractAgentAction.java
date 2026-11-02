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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            conversationId = McpService.get().issueConversationId(session, getUser(), getAgentName());
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

    protected JSONObject failureResponse(String message)
    {
        return new JSONObject(Map.of(
                "error", message,
                "text", "ERROR: " + message,
                "conversationId", getConversationId(),
                "success", Boolean.FALSE));
    }

    protected record Fence(String tag, String body, int nextIndex, boolean terminated) {}

    /** Length of the leading backtick run if {@code trimmed} is long enough to delimit a fence, else 0. */
    protected static int backtickRun(String trimmed)
    {
        int n = 0;
        while (n < trimmed.length() && trimmed.charAt(n) == '`')
            n++;
        return n >= 3 ? n : 0;
    }

    /** Per CommonMark a closing fence is backticks only, and at least as long as the opener. */
    protected static boolean isFenceClose(String line, int openLength)
    {
        String trimmed = line.trim();
        int n = backtickRun(trimmed);
        return n == trimmed.length() && n >= openLength;
    }

    protected static Fence readFence(String[] lines, int i)
    {
        String trimmed = lines[i].trim();
        int openLength = backtickRun(trimmed);
        if (openLength == 0)
            return null;
        String rest = trimmed.substring(openLength).trim();
        if (rest.isEmpty())
            return null;
        // Only the first word of the info string is the tag; models pad it ("expression json").
        String tag = rest.split("\\s+", 2)[0].toLowerCase();

        int j = i + 1;
        StringBuilder body = new StringBuilder();
        while (j < lines.length && !isFenceClose(lines[j], openLength))
        {
            if (!body.isEmpty()) body.append("\n");
            body.append(lines[j]);
            j++;
        }
        boolean terminated = j < lines.length;
        return new Fence(tag, body.toString(), terminated ? j + 1 : lines.length, terminated);
    }

    /**
     * True when a validateCalculatedColumnExpression payload reached the user as text rather than being unpacked
     * into an `expression` segment. Prose is one surface; the others are an illustrative `sql` block and an
     * `expression` block whose body didn't yield an expression, which shows the raw JSON behind an Apply button.
     * A well-formed `expression` segment carries jdbcType as its own key, so only the displayed text is checked.
     */
    private static boolean hasLeakedPayload(JSONArray segments)
    {
        for (int i = 0; i < segments.length(); i++)
        {
            JSONObject segment = segments.getJSONObject(i);
            String displayed = "html".equals(segment.optString("type"))
                    ? segment.optString("html", "")
                    : segment.optString("sql", "");
            if (displayed.contains("jdbcType"))
                return true;
        }
        return false;
    }

    /**
     * Build a segment JSON object for an `sql` or `expression` fenced block. For `expression`
     * blocks the body is expected to be the JSON returned by validateCalculatedColumnExpression;
     * we pull "expression" into "sql" and pass through "jdbcType".
     */
    private static JSONObject buildSqlSegment(String tag, String body, Logger log)
    {
        Map<String, Object> segData = new LinkedHashMap<>();
        segData.put("type", tag);

        if ("expression".equals(tag))
        {
            String sql = null;
            String jdbcType = null;
            try
            {
                JSONObject payload = new JSONObject(body);
                if (payload.has("expression"))
                {
                    sql = payload.optString("expression", null);
                    if (payload.has("jdbcType"))
                        jdbcType = payload.optString("jdbcType", null);
                }
            }
            catch (org.json.JSONException x)
            {
                log.debug("Unable to parse JSON expression: " + body, x);
            }

            segData.put("sql", sql != null ? sql : body);
            if (sql != null && jdbcType != null)
                segData.put("jdbcType", jdbcType);
        }
        else
            segData.put("sql", body);

        return new JSONObject(segData);
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
        // Model output can contain markup we won't render; show it as plain text rather than failing the turn
        if (!validateErrors.isEmpty())
            segments.put(new JSONObject(Map.of("type", "text", "text", raw)));
        else
            segments.put(new JSONObject(Map.of("type", "html", "html", html)));
    }

    /**
     * Walks the markdown of each MessageResponse and produces an ordered list of segments.
     * Each segment is either a rendered-HTML span or a fenced SQL block. SQL blocks fenced as
     * `expression` are tagged "expression" (the model's assertion that this SQL has been validated
     * and is safe to apply); blocks fenced as `sql` are tagged "sql" (illustrative / unvalidated).
     * For `expression` blocks the body is expected to be the JSON returned by
     * validateCalculatedColumnExpression — at minimum {@code {"expression": "..."}}, optionally
     * with {@code "jdbcType"}.
     */
    protected static JSONArray buildSegments(List<McpService.MessageResponse> responses, Logger log)
    {
        JSONArray segments = new JSONArray();
        MarkdownService md = MarkdownService.get();
        StringBuilder htmlBuf = new StringBuilder();

        // Scan the turns as one document: tool calling can split a single assistant turn so that a
        // fence opens in one MessageResponse and closes in the next.
        String text = responses.stream()
                .map(McpService.MessageResponse::text)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));

        log.debug("Assistant raw response:\n{}", text);

        String[] lines = text.split("\n", -1);
        int i = 0;
        while (i < lines.length)
        {
            Fence f = readFence(lines, i);
            if (f != null && f.terminated() && ("sql".equals(f.tag()) || "expression".equals(f.tag())))
            {
                flushHtmlSegment(segments, htmlBuf, md);
                segments.put(buildSqlSegment(f.tag(), f.body(), log));
                i = f.nextIndex();
            }
            else if (f != null && !f.terminated())
            {
                // Unterminated fence — skip only the opening line, so the user doesn't see a stray
                // "```expression" rendered as a code marker, and keep scanning. The body lands in prose
                // line by line via the branch below, and a well-formed fence later in the turn still parses.
                i++;
            }
            else
            {
                // Either not a fence opener or an unknown tag (e.g., python). In the unknown-tag
                // case we leave the fence intact in prose so the Markdown renderer turns it into
                // a code block.
                if (!htmlBuf.isEmpty()) htmlBuf.append("\n");
                htmlBuf.append(lines[i]);
                i++;
            }
        }

        flushHtmlSegment(segments, htmlBuf, md);

        // A payload the user can read means the model broke the fence protocol, so it renders as raw JSON
        // instead of a working Apply Expression action.
        if (hasLeakedPayload(segments))
            log.warn("Assistant leaked a validator payload into its reply");

        return segments;
    }

    public static class TestCase extends Assert
    {
        private static final Logger LOG = LogHelper.getLogger(TestCase.class, "AbstractAgentAction Tests");

        private static McpService.MessageResponse markdownResponse(String md)
        {
            return new McpService.MessageResponse("text/markdown", md, HtmlString.of(md));
        }

        private static JSONObject segment(JSONArray segments, int i)
        {
            return segments.getJSONObject(i);
        }

        /** The JSON body the model is expected to echo verbatim from validateCalculatedColumnExpression. */
        private static String expressionPayload(String sql, String jdbcType)
        {
            JSONObject json = new JSONObject();
            json.put("jdbcType", jdbcType);
            json.put("expression", sql);
            return json.toString();
        }

        private static void assertExpressionSegment(JSONArray segments, int i, String sql, String jdbcType)
        {
            assertEquals("expression", segment(segments, i).getString("type"));
            assertEquals(sql, segment(segments, i).getString("sql"));
            assertEquals(jdbcType, segment(segments, i).getString("jdbcType"));
        }

        /** The validator payload is transport between the tool and this action; it must never reach the user as text. */
        private static void assertNoPayloadInProse(JSONArray segments)
        {
            for (int i = 0; i < segments.length(); i++)
            {
                JSONObject seg = segments.getJSONObject(i);
                if ("html".equals(seg.optString("type")))
                    assertFalse("validator payload leaked into prose: " + seg, seg.getString("html").contains("jdbcType"));
            }
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

        @Test
        public void emptyResponseList()
        {
            JSONArray segments = buildSegments(List.of(), LOG);
            assertEquals(0, segments.length());
        }

        @Test
        public void blankMessageProducesNoSegments()
        {
            JSONArray segments = buildSegments(List.of(markdownResponse(""), markdownResponse("   ")), LOG);
            assertEquals(0, segments.length());
        }

        @Test
        public void proseOnlyProducesSingleHtmlSegment()
        {
            JSONArray segments = buildSegments(List.of(markdownResponse("Hello, here is some advice.")), LOG);
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertTrue(segment(segments, 0).getString("html").contains("Hello, here is some advice."));
        }

        @Test
        public void expressionFenceProducesExpressionSegment()
        {
            String md = "```expression\n" + expressionPayload("SELECT 1", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(1, segments.length());
            assertEquals("expression", segment(segments, 0).getString("type"));
            assertEquals("SELECT 1", segment(segments, 0).getString("sql"));
            assertEquals("INTEGER", segment(segments, 0).getString("jdbcType"));
        }

        @Test
        public void expressionFenceWithRawSqlBodyFallsBackToSqlOnly()
        {
            // Legacy / model-misbehavior fallback: if the body isn't JSON, treat it as raw SQL and omit jdbcType.
            String md = "```expression\nSELECT 1\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(1, segments.length());
            assertEquals("expression", segment(segments, 0).getString("type"));
            assertEquals("SELECT 1", segment(segments, 0).getString("sql"));
            assertFalse("jdbcType should be absent when body isn't JSON",
                    segment(segments, 0).has("jdbcType"));
        }

        @Test
        public void sqlFenceProducesSqlSegment()
        {
            String md = "```sql\nSELECT 1\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(1, segments.length());
            assertEquals("sql", segment(segments, 0).getString("type"));
            assertEquals("SELECT 1", segment(segments, 0).getString("sql"));
        }

        @Test
        public void interleavedProseAndFencesProduceOrderedSegments()
        {
            String md = String.join("\n",
                "Here are two options.",
                "",
                "Option A (illustrative):",
                "```sql",
                "SELECT a FROM t",
                "```",
                "",
                "Option B (ready to apply):",
                "```expression",
                expressionPayload("SELECT b FROM t", "VARCHAR"),
                "```",
                "Pick whichever fits."
            );
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(5, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertEquals("sql", segment(segments, 1).getString("type"));
            assertEquals("SELECT a FROM t", segment(segments, 1).getString("sql"));
            assertEquals("html", segment(segments, 2).getString("type"));
            assertEquals("expression", segment(segments, 3).getString("type"));
            assertEquals("SELECT b FROM t", segment(segments, 3).getString("sql"));
            assertEquals("VARCHAR", segment(segments, 3).getString("jdbcType"));
            assertEquals("html", segment(segments, 4).getString("type"));
        }

        @Test
        public void multipleExpressionFencesEachBecomeOwnSegment()
        {
            String md = String.join("\n",
                "```expression",
                expressionPayload("SELECT 1", "INTEGER"),
                "```",
                "```expression",
                expressionPayload("SELECT 2", "BIGINT"),
                "```"
            );
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(2, segments.length());
            assertEquals("expression", segment(segments, 0).getString("type"));
            assertEquals("SELECT 1", segment(segments, 0).getString("sql"));
            assertEquals("INTEGER", segment(segments, 0).getString("jdbcType"));
            assertEquals("expression", segment(segments, 1).getString("type"));
            assertEquals("SELECT 2", segment(segments, 1).getString("sql"));
            assertEquals("BIGINT", segment(segments, 1).getString("jdbcType"));
        }

        @Test
        public void unterminatedFenceFallsBackToHtml()
        {
            String payload = expressionPayload("SELECT 1", "INTEGER");
            String md = "Here's an expression:\n```expression\n" + payload + "\n(no closing fence)";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            String html = segment(segments, 0).getString("html");
            assertTrue("body should survive: " + html, html.contains("SELECT 1"));
            assertTrue("leading prose should survive: " + html, html.contains("Here&#39;s an expression:") || html.contains("Here's an expression:"));
            assertFalse("opening fence must be stripped: " + html, html.contains("```"));
        }

        @Test
        public void unterminatedFenceDoesNotDiscardLaterResponses()
        {
            // An unterminated fence must cost only its own block — the four-backtick opener below is never
            // closed (its closer is shorter), and the well-formed block after it still has to parse.
            var r1 = markdownResponse("````expression\n" + expressionPayload("SELECT 1", "INTEGER") + "\n```");
            var r2 = markdownResponse("Corrected:\n```expression\n" + expressionPayload("SELECT 2", "BIGINT") + "\n```");
            JSONArray segments = buildSegments(List.of(r1, r2), LOG);
            assertEquals(2, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertExpressionSegment(segments, 1, "SELECT 2", "BIGINT");
        }

        @Test
        public void unknownFenceLanguageIsTreatedAsProse()
        {
            String md = "```python\nprint('hi')\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            // Only sql/expression are split out; other fenced blocks stay in the html segment.
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
        }

        @Test
        public void fenceTagIsCaseInsensitive()
        {
            String md = "```EXPRESSION\n" + expressionPayload("SELECT 1", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(1, segments.length());
            assertEquals("expression", segment(segments, 0).getString("type"));
            assertEquals("SELECT 1", segment(segments, 0).getString("sql"));
            assertEquals("INTEGER", segment(segments, 0).getString("jdbcType"));
        }

        @Test
        public void expressionFenceIsRecognizedAtAnyBacktickLength()
        {
            for (int n = 3; n <= 6; n++)
            {
                String delim = "`".repeat(n);
                String md = delim + "expression\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n" + delim;
                JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
                assertEquals("fence length " + n, 1, segments.length());
                assertExpressionSegment(segments, 0, "Int7 + Int6", "INTEGER");
                assertNoPayloadInProse(segments);
            }
        }

        @Test
        public void longerClosingFenceTerminatesShorterOpener()
        {
            String md = "```expression\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n````";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(1, segments.length());
            assertExpressionSegment(segments, 0, "Int7 + Int6", "INTEGER");
        }

        @Test
        public void shorterClosingFenceDoesNotTerminateLongerOpener()
        {
            // Per CommonMark the short line is fence content, not a closer, so the block never terminates.
            String md = "````expression\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
        }

        @Test
        public void leakedPayloadDetectedWhenFenceIsMissing()
        {
            // No opening fence, so the payload lands in prose and the user gets no Apply affordance.
            String md = "Adding Int7 and Int6.\n\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertTrue("prose payload should be reported as a leak", hasLeakedPayload(segments));
        }

        @Test
        public void leakedPayloadDetectedInSqlFence()
        {
            // The model reached for the illustrative `sql` tag, so the payload renders as a read-only code block.
            String md = "```sql\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(1, segments.length());
            assertEquals("sql", segment(segments, 0).getString("type"));
            assertTrue("payload in a sql block should be reported as a leak", hasLeakedPayload(segments));
        }

        @Test
        public void leakedPayloadDetectedWhenExpressionBodyIsNotJson()
        {
            // The model narrated inside the fence, so the body doesn't parse and Apply would write JSON to the field.
            String body = "Here is the payload: " + expressionPayload("Int7 + Int6", "INTEGER");
            JSONArray segments = buildSegments(List.of(markdownResponse("```expression\n" + body + "\n```")), LOG);
            assertEquals(1, segments.length());
            assertEquals("expression", segment(segments, 0).getString("type"));
            assertEquals(body, segment(segments, 0).getString("sql"));
            assertTrue("unparsable payload should be reported as a leak", hasLeakedPayload(segments));
        }

        @Test
        public void leakedPayloadDetectedWhenExpressionKeyIsMissing()
        {
            // Parses, but nothing to unpack, so buildSqlSegment falls back to the raw body.
            String body = "{\"jdbcType\":\"INTEGER\",\"sql\":\"Int7 + Int6\"}";
            JSONArray segments = buildSegments(List.of(markdownResponse("```expression\n" + body + "\n```")), LOG);
            assertEquals(body, segment(segments, 0).getString("sql"));
            assertTrue("payload without an expression key should be reported as a leak", hasLeakedPayload(segments));
        }

        @Test
        public void wellFormedExpressionSegmentIsNotALeak()
        {
            // "jdbcType" is a key on the expression segment itself, not part of the SQL the user sees.
            String md = "```expression\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertExpressionSegment(segments, 0, "Int7 + Int6", "INTEGER");
            assertFalse("a parsed expression segment is not a leak", hasLeakedPayload(segments));
        }

        @Test
        public void proseWithoutPayloadIsNotALeak()
        {
            JSONArray segments = buildSegments(List.of(markdownResponse("Which date field should be used?")), LOG);
            assertEquals("html", segment(segments, 0).getString("type"));
            assertFalse(hasLeakedPayload(segments));
        }

        @Test
        public void expressionFenceWithExtraInfoStringTokenIsRecognized()
        {
            // Only the info string's first word is the tag, so "expression json" still resolves to "expression".
            String md = "```expression json\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(1, segments.length());
            assertExpressionSegment(segments, 0, "Int7 + Int6", "INTEGER");
            assertNoPayloadInProse(segments);
        }

        @Test
        public void expressionFenceSplitAcrossResponsesIsRecognized()
        {
            // Tool calling can split one assistant turn mid-fence; the scan joins the turns so the block still closes.
            String payload = expressionPayload("Int7 + Int6", "INTEGER");
            var r1 = markdownResponse("Adding Int7 and Int6.\n\n```expression\n" + payload);
            var r2 = markdownResponse("```");
            JSONArray segments = buildSegments(List.of(r1, r2), LOG);
            assertEquals(2, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertExpressionSegment(segments, 1, "Int7 + Int6", "INTEGER");
            assertNoPayloadInProse(segments);
        }

        @Test
        public void preservesMultilineSqlBody()
        {
            String multiline = "SELECT a,\n       b\nFROM t";
            String md = "```expression\n" + expressionPayload(multiline, "VARCHAR") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)), LOG);
            assertEquals(1, segments.length());
            assertEquals(multiline, segment(segments, 0).getString("sql"));
            assertEquals("VARCHAR", segment(segments, 0).getString("jdbcType"));
        }

        @Test
        public void multipleMessageResponsesAreConcatenatedInOrder()
        {
            McpService.MessageResponse r1 = markdownResponse("First response prose.");
            McpService.MessageResponse r2 = markdownResponse("```expression\n" + expressionPayload("SELECT 1", "INTEGER") + "\n```");
            JSONArray segments = buildSegments(List.of(r1, r2), LOG);
            assertEquals(2, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertEquals("expression", segment(segments, 1).getString("type"));
            assertEquals("SELECT 1", segment(segments, 1).getString("sql"));
            assertEquals("INTEGER", segment(segments, 1).getString("jdbcType"));
        }
    }
}

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
package org.labkey.query.controllers;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.mcp.AbstractAgentAction;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.query.controllers.QueryController.ParseForm;
import org.labkey.query.controllers.QueryController.PromptResource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@RequiresPermission(ReadPermission.class)
@RequiresLogin
public class ExpressionAssistantAgentAction extends AbstractAgentAction<ParseForm>
{
    private static final Logger LOG = LogManager.getLogger(ExpressionAssistantAgentAction.class);

    @Override
    protected String getAgentName()
    {
        return ExpressionAssistantAgentAction.class.getName();
    }

    @Override
    protected String getServicePrompt()
    {
        return PromptResource.ExpressionAssistant.resource() +
            "\n\nBefore starting, load the LabKey SQL documentation using the \"readResource\" tool with the URI \"" + PromptResource.LabKeySql.uri() + "\"\n\n";
    }

    @Override
    public Object execute(ParseForm form, BindException errors) throws Exception
    {
        try (var _ = McpContext.withContext(getViewContext()))
        {
            boolean firstTurn = isBlank(form.getConversationId());
            String prompt = form.getPrompt();
            String composedPrompt = composePrompt(firstTurn, prompt, form.getField(), form.getDomainFields(), form.getFieldExpression(), form.getFieldError());

            if (isBlank(composedPrompt))
            {
                return new JSONObject(Map.of(
                        "contentType", "text/plain",
                        "text", "🤷",
                        "success", Boolean.TRUE));
            }

            ChatClient chatSession = getChat(true);
            List<McpService.MessageResponse> responses;

            try
            {
                // Context for the QueryMcp.validateCalculatedColumnExpression() tool
                McpContext.get()
                        .put("columnMap", form.getColumnMap())
                        .put("phiColumns", form.getPhiColumns());

                LOG.info("Expression assistant prompt: {}", prompt);
                responses = McpService.get().sendMessageEx(chatSession, composedPrompt);
            }
            catch (ServerException x)
            {
                return new JSONObject(Map.of(
                        "error", x.getMessage(),
                        "text", "ERROR: " + x.getMessage(),
                        "success", Boolean.FALSE));
            }

            JSONArray segments = buildSegments(responses);
            return new JSONObject(Map.of(
                    "success", Boolean.TRUE,
                    "conversationId", getConversationId(),
                    "segments", segments));
        }
        catch (ClientException x)
        {
            return errorResponse(x);
        }
    }

    /**
     * Combines the user's prompt with any first-turn context supplied by the client (the catalog
     * of available fields, the current expression, and an error message when auto-evaluating an
     * invalid expression). When {@code firstTurn} is false the context fields are ignored, and the
     * user's prompt is returned verbatim.
     */
    static String composePrompt(boolean firstTurn, String userPrompt, JSONObject field, JSONArray domainFields, String fieldExpression, String fieldError)
    {
        if (!firstTurn)
            return StringUtils.defaultString(userPrompt);

        boolean autoEvaluate = isBlank(userPrompt) && isNotBlank(fieldError);
        if (isBlank(userPrompt) && !autoEvaluate)
            return "";

        StringBuilder sb = new StringBuilder();
        if (domainFields != null && !domainFields.isEmpty())
        {
            sb.append("The following enumerates the available columns and their types:\n");
            sb.append(fence(domainFields.toString(), "json"));
        }

        if (field != null)
        {
            sb.append("The current column that is having its expression evaluated and the one you are assisting with is:\n");
            sb.append(fence(field.toString(), "json"));
        }

        if (autoEvaluate)
        {
            if (isNotBlank(fieldExpression))
            {
                sb.append("The user already has the following calculated column expression:\n");
                sb.append(fence(fieldExpression));
            }
            sb.append("This expression contains an error:\n");
            sb.append(fence(fieldError));
            sb.append("Evaluate this expression and see if you can determine how to fix this error. If you can, point them out and propose corrections.");
        }
        else if (sb.isEmpty())
        {
            sb.append(userPrompt);
        }
        else
        {
            sb.append("Generate a calculated column expression that matches the following description:\n");
            sb.append(userPrompt);
        }

        return sb.toString();
    }

    static String fence(String body)
    {
        return fence(body, "");
    }

    /** Wraps {@code body} in a markdown fenced code block, optionally tagged with a language. */
    static String fence(String body, String tag)
    {
        return "```" + tag + "\n" + body + "\n```\n";
    }

    /**
     * Walks the markdown of each MessageResponse and produces an ordered list of segments.
     * Each segment is either a rendered-HTML span or a fenced SQL block. SQL blocks fenced as
     * `expression` are tagged "expression" (the model's assertion that this SQL has been validated
     * and is safe to apply); blocks fenced as `sql` are tagged "sql" (illustrative / unvalidated).
     * For `expression` blocks the body is expected to be the JSON returned by
     * validateCalculatedColumnExpression — at minimum {@code {"expression": "..."}}, optionally
     * with {@code "jdbcType"}. If the body fails to parse as JSON we fall back to treating it as a
     * raw SQL string so the Apply affordance still works.
     */
    private static JSONArray buildSegments(List<McpService.MessageResponse> responses)
    {
        JSONArray segments = new JSONArray();
        MarkdownService md = MarkdownService.get();
        StringBuilder htmlBuf = new StringBuilder();

        for (var response : responses)
        {
            String text = response.text();
            if (isBlank(text))
                continue;

            String[] lines = text.split("\n", -1);
            int i = 0;
            while (i < lines.length)
            {
                Fence f = readFence(lines, i);
                if (f != null && f.terminated && ("sql".equals(f.tag) || "expression".equals(f.tag)))
                {
                    flushHtmlSegment(segments, htmlBuf, md);
                    segments.put(buildCodeSegment(f.tag, f.body));
                    i = f.nextIndex;
                }
                else if (f != null && !f.terminated)
                {
                    // Unterminated fence — fold the body back into prose so we don't drop content,
                    // but skip the opening fence line itself so the user doesn't see a stray
                    // "```expression" rendered as a code marker.
                    if (!htmlBuf.isEmpty()) htmlBuf.append("\n");
                    htmlBuf.append(f.body);
                    break;
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
        }
        flushHtmlSegment(segments, htmlBuf, md);
        return segments;
    }

    /**
     * Build a segment JSON object for an `sql` or `expression` fenced block. For `expression`
     * blocks the body is expected to be the JSON returned by validateCalculatedColumnExpression;
     * we pull "expression" into "sql" and pass through "jdbcType". If the body isn't JSON we treat
     * it as raw SQL text so the Apply affordance still works.
     */
    private static JSONObject buildCodeSegment(String tag, String body)
    {
        Map<String, Object> segData = new LinkedHashMap<>();
        segData.put("type", tag);

        if ("expression".equals(tag))
        {
            try
            {
                JSONObject payload = new JSONObject(body);
                segData.put("sql", payload.optString("expression", body));
                if (payload.has("jdbcType"))
                    segData.put("jdbcType", payload.getString("jdbcType"));
            }
            catch (org.json.JSONException x)
            {
                segData.put("sql", body);
            }
        }
        else
            segData.put("sql", body);

        return new JSONObject(segData);
    }

    private record Fence(String tag, String body, int nextIndex, boolean terminated) {}

    private static Fence readFence(String[] lines, int i)
    {
        String trimmed = lines[i].trim();
        if (!trimmed.startsWith("```"))
            return null;
        String rest = trimmed.substring(3).trim();
        if (rest.isEmpty())
            return null;
        String tag = rest.toLowerCase();

        int j = i + 1;
        StringBuilder body = new StringBuilder();
        while (j < lines.length && !"```".equals(lines[j].trim()))
        {
            if (!body.isEmpty()) body.append("\n");
            body.append(lines[j]);
            j++;
        }
        boolean terminated = j < lines.length;
        return new Fence(tag, body.toString(), terminated ? j + 1 : lines.length, terminated);
    }

    private static void flushHtmlSegment(JSONArray segments, StringBuilder buf, MarkdownService md)
    {
        if (buf.isEmpty()) return;
        String raw = buf.toString().strip();
        buf.setLength(0);
        if (raw.isEmpty()) return;
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

    public static class TestCase extends Assert
    {
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
            json.put("success", true);
            return json.toString();
        }

        @Test
        public void emptyResponseList()
        {
            JSONArray segments = buildSegments(List.of());
            assertEquals(0, segments.length());
        }

        @Test
        public void blankMessageProducesNoSegments()
        {
            JSONArray segments = buildSegments(List.of(markdownResponse(""), markdownResponse("   ")));
            assertEquals(0, segments.length());
        }

        @Test
        public void proseOnlyProducesSingleHtmlSegment()
        {
            JSONArray segments = buildSegments(List.of(markdownResponse("Hello, here is some advice.")));
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertTrue(segment(segments, 0).getString("html").contains("Hello, here is some advice."));
        }

        @Test
        public void expressionFenceProducesExpressionSegment()
        {
            String md = "```expression\n" + expressionPayload("SELECT 1", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
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
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
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
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
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
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
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
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
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
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            String html = segment(segments, 0).getString("html");
            assertTrue("body should survive: " + html, html.contains("SELECT 1"));
            assertTrue("leading prose should survive: " + html, html.contains("Here&#39;s an expression:") || html.contains("Here's an expression:"));
            assertFalse("opening fence must be stripped: " + html, html.contains("```"));
        }

        @Test
        public void unknownFenceLanguageIsTreatedAsProse()
        {
            String md = "```python\nprint('hi')\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            // Only sql/expression are split out; other fenced blocks stay in the html segment.
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
        }

        @Test
        public void fenceTagIsCaseInsensitive()
        {
            String md = "```EXPRESSION\n" + expressionPayload("SELECT 1", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertEquals("expression", segment(segments, 0).getString("type"));
            assertEquals("SELECT 1", segment(segments, 0).getString("sql"));
            assertEquals("INTEGER", segment(segments, 0).getString("jdbcType"));
        }

        @Test
        public void preservesMultilineSqlBody()
        {
            String multiline = "SELECT a,\n       b\nFROM t";
            String md = "```expression\n" + expressionPayload(multiline, "VARCHAR") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertEquals(multiline, segment(segments, 0).getString("sql"));
            assertEquals("VARCHAR", segment(segments, 0).getString("jdbcType"));
        }

        @Test
        public void multipleMessageResponsesAreConcatenatedInOrder()
        {
            McpService.MessageResponse r1 = markdownResponse("First response prose.");
            McpService.MessageResponse r2 = markdownResponse("```expression\n" + expressionPayload("SELECT 1", "INTEGER") + "\n```");
            JSONArray segments = buildSegments(List.of(r1, r2));
            assertEquals(2, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertEquals("expression", segment(segments, 1).getString("type"));
            assertEquals("SELECT 1", segment(segments, 1).getString("sql"));
            assertEquals("INTEGER", segment(segments, 1).getString("jdbcType"));
        }

        @Test
        public void flushHtmlSegmentRejectsScriptTag()
        {
            JSONArray segments = new JSONArray();
            StringBuilder buf = new StringBuilder("<div>Here is a greeting:</div><script>alert('xss')</script>");
            RuntimeValidationException ex = assertThrows(RuntimeValidationException.class,
                    () -> flushHtmlSegment(segments, buf, null));
            assertTrue("expected validation error message, got: " + ex.getMessage(),
                    ex.getMessage().contains("Illegal element <script>"));
            assertEquals("no segment should be emitted when validation fails", 0, segments.length());
        }

        @Test
        public void testFence()
        {
            assertEquals("```json\n{\"a\":1}\n```\n", fence("{\"a\":1}", "json"));
            assertEquals("```\nSELECT 1\n```\n", fence("SELECT 1", ""));
            assertEquals("```sql\nline1\nline2\n```\n", fence("line1\nline2", "sql"));
            assertEquals("```json\n\n```\n", fence("", "json"));
        }

        private static JSONArray fields(String json)
        {
            return new JSONArray(json);
        }

        @Test
        public void composePromptFollowUpTurnIgnoresContextAndReturnsUserPromptVerbatim()
        {
            // Once a conversation is underway, the catalog/expression/error are already in chat history.
            assertEquals("more please", composePrompt(false, "more please", null, fields("[{\"name\":\"A\"}]"), "SELECT 1", "boom"));
        }

        @Test
        public void composePromptFirstTurnWrapsWithFieldsCatalogAndInstruction()
        {
            JSONObject field = new JSONObject(Map.of("name", "MyCalc"));
            String composed = composePrompt(true, "sum A and B", field, fields("[{\"name\":\"A\"}]"), null, null);
            assertTrue(composed.contains("available columns"));
            assertTrue(composed.contains("```json\n[{\"name\":\"A\"}]\n```"));
            assertTrue("current-column preamble missing: " + composed,
                    composed.contains("current column that is having its expression evaluated"));
            assertTrue("current-column JSON missing: " + composed,
                    composed.contains("```json\n" + field + "\n```"));
            assertTrue(composed.contains("Generate a calculated column expression"));
            assertTrue(composed.endsWith("sum A and B"));
        }

        @Test
        public void composePromptAutoEvaluateIncludesExpressionAndError()
        {
            String composed = composePrompt(true, "", null, fields("[{\"name\":\"A\"}]"), "SELECT bad", "syntax error");
            assertTrue(composed.contains("available columns"));
            assertTrue(composed.contains("```\nSELECT bad\n```"));
            assertTrue(composed.contains("```\nsyntax error\n```"));
            assertTrue(composed.contains("Evaluate this expression"));
            assertFalse("auto-evaluate must not include the change/new instruction line",
                    composed.contains("Generate a calculated column expression"));
        }

        @Test
        public void composePromptAutoEvaluateWithoutExpressionStillIncludesError()
        {
            String composed = composePrompt(true, "", null, null, null, "boom");
            assertTrue(composed.contains("```\nboom\n```"));
            assertFalse(composed.contains("user already has the following"));
        }

        @Test
        public void composePromptEmptyWhenNothingToSay()
        {
            // First turn with no user prompt and no error context — caller renders the no-op shrug response.
            assertEquals("", composePrompt(true, "", null, null, null, null));
            assertEquals("", composePrompt(true, null, null, fields("[]"), "expr", null));
        }

        @Test
        public void composePromptFirstTurnWithoutDomainFieldsSkipsWrapping()
        {
            // No catalog to inject — don't bother prepending the "Generate a calculated column" preamble.
            assertEquals("just do it", composePrompt(true, "just do it", null, null, null, null));
        }
    }
}
